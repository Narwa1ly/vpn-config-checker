package app.vpncheck.data.subscription

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FetchResult(
    val source: SubscriptionSource,
    val links: List<String>,
    /** Mirror that answered, null on failure. */
    val usedUrl: String?,
    /** Null on success. */
    val error: String?,
    /** Short per-mirror failure descriptions ("githack: таймаут"). */
    val mirrorErrors: List<String> = emptyList(),
) {
    val ok: Boolean get() = error == null
    val usedMirror: String? get() = usedUrl?.let { shortHost(it) }

    companion object {
        fun shortHost(url: String): String = url.removePrefix("https://").removePrefix("http://").substringBefore('/')
            .removePrefix("raw.").removeSuffix(".com").removeSuffix(".org")
    }
}

private typealias MirrorOutcome = Pair<String, Result<List<String>>>

/**
 * Downloads a subscription file. All mirrors are requested concurrently and the first one that
 * returns a valid subscription wins; the rest are cancelled. Worst case is bounded by one call timeout
 * instead of (mirrors x timeout), which matters when GitHub is blocked.
 */
class SubscriptionFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val urlsFor: (SubscriptionSource) -> List<String> = { it.urls() },
) {
    suspend fun fetch(source: SubscriptionSource): FetchResult = coroutineScope {
        val urls = urlsFor(source)
        if (urls.isEmpty()) return@coroutineScope FetchResult(source, emptyList(), null, "нет зеркал")
        val pending: MutableList<Deferred<MirrorOutcome>> = urls.map { url ->
            async(Dispatchers.IO) { url to runCatching { download(url) } }
        }.toMutableList()
        val errors = ArrayList<String>()
        var winner: Pair<String, List<String>>? = null
        while (pending.isNotEmpty() && winner == null) {
            val (finished, outcome) = select<Pair<Deferred<MirrorOutcome>, MirrorOutcome>> {
                pending.forEach { d -> d.onAwait { d to it } }
            }
            pending.remove(finished)
            val (url, result) = outcome
            result.fold(
                onSuccess = { winner = url to it },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errors += "${FetchResult.shortHost(url)}: ${describe(e)}"
                },
            )
        }
        pending.forEach { it.cancel() }
        val w = winner
        if (w != null) FetchResult(source, w.second, w.first, null, errors)
        else FetchResult(source, emptyList(), null, "все ${urls.size} зеркал недоступны", errors)
    }

    private suspend fun download(url: String): List<String> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "vpn-config-checker/1.0")
            .header("Cache-Control", "no-cache")
            .build()
        val resp = client.newCall(req).await()
        val body = withContext(Dispatchers.IO) {
            resp.use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                r.body?.string() ?: throw IOException("пустой ответ")
            }
        }
        val links = SubscriptionParser.extractLinks(body)
        if (links.isEmpty() && SubscriptionParser.profileTitle(body) == null) {
            throw IOException("в ответе нет конфигов")
        }
        return links
    }

    private fun describe(e: Throwable): String {
        val msg = e.message ?: ""
        return when {
            e is SocketTimeoutException || msg.contains("timeout", true) -> "таймаут"
            e is UnknownHostException -> "DNS не отвечает"
            msg.startsWith("Failed to connect") -> "нет соединения"
            msg.contains("Connection reset", true) -> "соединение сброшено"
            msg.contains("SSL", true) || msg.contains("TLS", true) || msg.contains("handshake", true) -> "TLS-ошибка"
            else -> msg.ifBlank { e.javaClass.simpleName }
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
