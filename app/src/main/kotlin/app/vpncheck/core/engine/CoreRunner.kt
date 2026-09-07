package app.vpncheck.core.engine

import android.content.Context
import android.util.Log
import app.vpncheck.core.parser.CoreType
import app.vpncheck.core.parser.ProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Runs Xray-core / sing-box as a subprocess with a generated config that exposes a local HTTP proxy.
 *
 * Binaries ship in jniLibs as libxray.so / libsingbox.so so that Android extracts them into
 * [android.content.pm.ApplicationInfo.nativeLibraryDir] with exec permission (works on Android 10+
 * where exec from app data dirs is forbidden).
 */
class CoreRunner(private val context: Context) {

    class CoreHandle internal constructor(
        val config: ProxyConfig,
        val localPort: Int,
        val configFile: File,
        val logFile: File,
        internal val process: Process,
        val resolvedAddress: String,
    ) {
        val isAlive: Boolean get() = process.isAlive

        fun stop() {
            try {
                process.destroy()
                if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            } catch (_: Exception) {
                process.destroyForcibly()
            }
        }

        fun logTail(maxChars: Int = 1500): String = try {
            val text = logFile.readText()
            if (text.length > maxChars) text.substring(text.length - maxChars) else text
        } catch (_: Exception) {
            ""
        }
    }

    class CoreStartException(message: String, val logTail: String) : Exception(message)

    private val workDir: File by lazy { File(context.cacheDir, "cores").apply { mkdirs() } }

    fun binaryFor(core: CoreType): File {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val name = when (core) {
            CoreType.XRAY -> "libxray.so"
            CoreType.SINGBOX -> "libsingbox.so"
        }
        return File(libDir, name)
    }

    fun binariesPresent(): Boolean = CoreType.values().all { binaryFor(it).exists() }

    /**
     * Starts the core for [config] and waits until the local proxy port accepts connections.
     * The server host is resolved via the Android system resolver first so the core does not
     * depend on Go's DNS on Android; SNI / Host headers keep the original host name.
     */
    suspend fun start(config: ProxyConfig, readyTimeoutMs: Long = 10_000): CoreHandle = withContext(Dispatchers.IO) {
        val port = freePort()
        val address = resolveAddress(config.host)
        val json = when (config.core) {
            CoreType.XRAY -> XrayConfigBuilder.build(config, port, address)
            CoreType.SINGBOX -> SingBoxConfigBuilder.build(config, port, address)
        }
        val shortId = config.id.substring(0, 12)
        val configFile = File(workDir, "$shortId.json").apply { writeText(json.toString()) }
        val logFile = File(workDir, "$shortId.log").apply { delete() }
        val bin = binaryFor(config.core)
        if (!bin.exists()) throw CoreStartException("бинарник ядра не найден: ${bin.name}", "")

        val cmd = when (config.core) {
            CoreType.XRAY -> listOf(bin.absolutePath, "run", "-c", configFile.absolutePath)
            CoreType.SINGBOX -> listOf(bin.absolutePath, "run", "-D", workDir.absolutePath, "-c", configFile.absolutePath)
        }
        val pb = ProcessBuilder(cmd)
            .directory(workDir)
            .redirectErrorStream(true)
            .redirectOutput(logFile)
        pb.environment()["XRAY_LOCATION_ASSET"] = workDir.absolutePath
        pb.environment()["HOME"] = workDir.absolutePath
        pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
        val process = try {
            pb.start()
        } catch (e: Exception) {
            throw CoreStartException("не удалось запустить ${bin.name}: ${e.message}", "")
        }
        val handle = CoreHandle(config, port, configFile, logFile, process, address)

        val deadline = System.currentTimeMillis() + readyTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) {
                val tail = handle.logTail()
                throw CoreStartException("ядро завершилось (код ${process.exitValue()})", tail)
            }
            if (portOpen(port)) return@withContext handle
            delay(100)
        }
        handle.stop()
        throw CoreStartException("ядро не открыло порт за ${readyTimeoutMs / 1000} с", handle.logTail())
    }

    private fun resolveAddress(host: String): String {
        if (host.matches(Regex("^[0-9.]+$")) || host.contains(':')) return host
        return try {
            val all = InetAddress.getAllByName(host)
            (all.firstOrNull { it is java.net.Inet4Address } ?: all.first()).hostAddress ?: host
        } catch (e: Exception) {
            Log.w(TAG, "DNS failed for $host: ${e.message}")
            host
        }
    }

    private fun freePort(): Int = ServerSocket().use { s ->
        s.reuseAddress = true
        s.bind(InetSocketAddress("127.0.0.1", 0))
        s.localPort
    }

    private fun portOpen(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200); true }
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val TAG = "CoreRunner"
    }
}
