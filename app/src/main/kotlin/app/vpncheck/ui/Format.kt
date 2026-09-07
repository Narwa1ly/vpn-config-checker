package app.vpncheck.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun flagEmoji(countryCode: String?): String {
    val cc = countryCode?.trim()?.uppercase() ?: return ""
    if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return ""
    val first = Character.toChars(0x1F1E6 + (cc[0] - 'A'))
    val second = Character.toChars(0x1F1E6 + (cc[1] - 'A'))
    return String(first) + String(second)
}

fun formatDateTime(ms: Long?): String {
    if (ms == null) return "—"
    return SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(ms))
}

fun formatLatency(ms: Long?): String = when {
    ms == null -> ""
    ms < 1000 -> "$ms мс"
    else -> String.format(Locale.US, "%.1f с", ms / 1000.0)
}

/** Strips the "| [BL]" style suffixes and stray decorations from subscription names. */
fun prettyName(name: String): String {
    var s = name.trim()
    s = s.replace(Regex("\\s*\\|\\s*\\[[^]]*]\\s*$"), "")
    s = s.replace(Regex("\\s*\\|\\s*🌐\\s*$"), "")
    return s.ifBlank { name }
}

fun hostLabel(host: String, port: Int): String = if (host.contains(':')) "[$host]:$port" else "$host:$port"

/** How old the last successful subscription download is. */
enum class Freshness(val label: String) {
    NONE("не скачаны"),
    FRESH("актуальны"),
    AGING("стоит обновить"),
    STALE("устарели");

    companion object {
        private const val HOUR = 60 * 60 * 1000L

        fun of(lastSuccessAt: Long?, now: Long): Freshness = when {
            lastSuccessAt == null -> NONE
            now - lastSuccessAt < 12 * HOUR -> FRESH
            now - lastSuccessAt < 48 * HOUR -> AGING
            else -> STALE
        }
    }
}
