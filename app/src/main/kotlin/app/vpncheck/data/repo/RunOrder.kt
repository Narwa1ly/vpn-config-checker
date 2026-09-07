package app.vpncheck.data.repo

import app.vpncheck.core.network.NetworkType

/**
 * Order in which configs are checked. On mobile networks white-list configs go first (they are the
 * ones that matter during white-list shutdowns); then previously working, then unchecked, then failed.
 */
object RunOrder {
    data class Info(val isWhite: Boolean, val previousOk: Boolean?)

    fun priority(mode: NetworkType, info: Info): Int {
        var p = 0
        if (!(mode == NetworkType.MOBILE && info.isWhite)) p += 10
        p += when (info.previousOk) {
            true -> 0
            null -> 1
            false -> 2
        }
        return p
    }

    fun <T> sort(items: List<T>, mode: NetworkType, info: (T) -> Info): List<T> =
        items.sortedBy { priority(mode, info(it)) }
}
