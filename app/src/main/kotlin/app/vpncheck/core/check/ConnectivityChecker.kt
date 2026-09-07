package app.vpncheck.core.check

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Verifies that 2ip.io is reachable through the local HTTP proxy exposed by a running core.
 * Success = api.2ip.io returned JSON with an IP, or 2ip.io returned an HTTP response whose body mentions 2ip.
 */
class ConnectivityChecker(
    private val connectTimeoutSec: Long = 10,
    private val readTimeoutSec: Long = 15,
) {
    suspend fun check(localPort: Int): CheckOutcome = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", localPort)))
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(connectTimeoutSec + readTimeoutSec, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(true)
            .build()
        try {
            val start = System.nanoTime()
            val (status, body) = get(client, API_URL)
            val latency = (System.nanoTime() - start) / 1_000_000
            if (status == 200) {
                TwoIpParser.parse(body)?.let { return@withContext it.copy(latencyMs = latency) }
            }
            // API unavailable but tunnel works? Fall back to the HTML page.
            val start2 = System.nanoTime()
            val (status2, body2) = get(client, PAGE_URL)
            val latency2 = (System.nanoTime() - start2) / 1_000_000
            if (TwoIpParser.looksLike2ipPage(body2)) {
                return@withContext CheckOutcome(
                    ok = true, latencyMs = latency2, exitIp = null, country = null, countryCode = null,
                    city = null, asnId = null, asnName = null, httpStatus = status2, error = null,
                )
            }
            CheckOutcome.failure("2ip.io ответил HTTP $status2 без ожидаемого содержимого", status2)
        } catch (e: IOException) {
            CheckOutcome.failure(describe(e))
        } catch (e: Exception) {
            CheckOutcome.failure(e.message ?: e.javaClass.simpleName)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun get(client: OkHttpClient, url: String): Pair<Int, String> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string() ?: "")
        }
    }

    private fun describe(e: IOException): String {
        val msg = e.message ?: ""
        return when {
            e is java.net.SocketTimeoutException || msg.contains("timeout", true) -> "таймаут"
            msg.contains("Failed to authenticate with proxy", true) -> "прокси отверг соединение"
            msg.contains("Unexpected response code for CONNECT", true) -> "туннель не установлен (${msg.substringAfterLast(':').trim()})"
            msg.contains("unexpected end of stream", true) -> "соединение оборвано"
            msg.contains("Connection reset", true) -> "соединение сброшено"
            else -> msg.ifBlank { e.javaClass.simpleName }
        }
    }

    companion object {
        const val API_URL = "https://api.2ip.io/"
        const val PAGE_URL = "https://2ip.io/"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
