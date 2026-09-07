package app.vpncheck.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkType(val label: String) {
    MOBILE("Мобильный"),
    WIFI("Wi‑Fi"),
    OTHER("Другая сеть"),
    NONE("Нет сети");
}

data class NetworkState(
    val type: NetworkType,
    val vpnActive: Boolean,
) {
    /** Which result bucket a check on this network belongs to; null when checks make no sense. */
    val bucket: NetworkType? get() = when (type) {
        NetworkType.MOBILE, NetworkType.WIFI -> type
        else -> null
    }
}

class NetworkTypeDetector(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun current(): NetworkState {
        val network = cm.activeNetwork ?: return NetworkState(NetworkType.NONE, false)
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkState(NetworkType.NONE, false)
        return fromCaps(caps)
    }

    private fun fromCaps(caps: NetworkCapabilities): NetworkState {
        val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.OTHER
            vpn -> NetworkType.OTHER
            else -> NetworkType.OTHER
        }
        return NetworkState(type, vpn)
    }

    fun observe(): Flow<NetworkState> = callbackFlow {
        trySend(current())
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(current()) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(current()) }
        }
        val request = NetworkRequest.Builder().build()
        cm.registerNetworkCallback(request, cb)
        awaitClose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }.distinctUntilChanged()
}
