package app.vpncheck.core.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Resolves the hosting provider of an exit IP. Tries direct requests first and falls back
 * to routing the lookup through the tunnel that is still open for the config under test.
 */
class ProviderResolver(
    private val directClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun resolve(ip: String, viaLocalProxyPort: Int? = null): ProviderInfo? = withContext(Dispatchers.IO) {
        val clients = buildList {
            add(directClient)
            if (viaLocalProxyPort != null) {
                add(
                    directClient.newBuilder()
                        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", viaLocalProxyPort)))
                        .build()
                )
            }
        }
        for (client in clients) {
            runCatching { get(client, "https://ipwho.is/$ip") }
                .getOrNull()?.let { body -> runCatching { ProviderJsonParser.fromIpWhoIs(ip, body) }.getOrNull() }
                ?.let { return@withContext it }
            runCatching { get(client, "http://ip-api.com/json/$ip?fields=status,country,countryCode,city,as,asname,org,isp,query") }
                .getOrNull()?.let { body -> runCatching { ProviderJsonParser.fromIpApi(ip, body) }.getOrNull() }
                ?.let { return@withContext it }
        }
        null
    }

    private fun get(client: OkHttpClient, url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "vpn-config-checker/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw IOException("empty")
        }
    }
}
