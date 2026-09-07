package app.vpncheck.core.check

import org.json.JSONObject

/** Parses https://api.2ip.io/ JSON. */
object TwoIpParser {
    fun parse(json: String): CheckOutcome? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val ip = o.optString("ip").ifBlank { return null }
        val asn = o.optJSONObject("asn")
        return CheckOutcome(
            ok = true,
            latencyMs = null,
            exitIp = ip,
            country = o.optString("country").ifBlank { null },
            countryCode = o.optString("code").ifBlank { null },
            city = o.optString("city").ifBlank { null },
            asnId = asn?.optString("id")?.toIntOrNull(),
            asnName = asn?.optString("name")?.ifBlank { null },
            httpStatus = 200,
            error = null,
        )
    }

    /** True when an HTML body plausibly came from 2ip.io (including its JS challenge page). */
    fun looksLike2ipPage(body: String): Boolean = body.contains("2ip", ignoreCase = true)
}
