package app.vpncheck.core.provider

import org.json.JSONObject

/** Pure JSON -> [ProviderInfo] conversions for the lookup services; kept free of I/O for testing. */
object ProviderJsonParser {

    /** https://ipwho.is/{ip} */
    fun fromIpWhoIs(ip: String, json: String): ProviderInfo? {
        val o = JSONObject(json)
        if (!o.optBoolean("success", true)) return null
        val conn = o.optJSONObject("connection") ?: JSONObject()
        val asn = conn.optInt("asn", 0).takeIf { it > 0 }
        val domain = conn.optString("domain").ifBlank { null }
        return ProviderInfo(
            ip = ip,
            asn = asn,
            org = conn.optString("org").ifBlank { null },
            isp = conn.optString("isp").ifBlank { null },
            domain = domain,
            website = websiteFor(domain, asn),
            country = o.optString("country").ifBlank { null },
            countryCode = o.optString("country_code").ifBlank { null },
            city = o.optString("city").ifBlank { null },
            source = "ipwho.is",
        )
    }

    /** http://ip-api.com/json/{ip}?fields=status,country,countryCode,city,as,asname,org,isp,query */
    fun fromIpApi(ip: String, json: String): ProviderInfo? {
        val o = JSONObject(json)
        if (o.optString("status") != "success") return null
        val asField = o.optString("as") // "AS15169 Google LLC"
        val asn = Regex("^AS(\\d+)").find(asField)?.groupValues?.get(1)?.toIntOrNull()
        val asOrg = asField.substringAfter(' ', "").ifBlank { null }
        return ProviderInfo(
            ip = ip,
            asn = asn,
            org = o.optString("org").ifBlank { null } ?: asOrg,
            isp = o.optString("isp").ifBlank { null },
            domain = null,
            website = websiteFor(null, asn),
            country = o.optString("country").ifBlank { null },
            countryCode = o.optString("countryCode").ifBlank { null },
            city = o.optString("city").ifBlank { null },
            source = "ip-api.com",
        )
    }

    /** Built from what 2ip.io already told us when the lookup services are unreachable. */
    fun fromAsnOnly(ip: String, asn: Int?, asnName: String?, country: String?, countryCode: String?, city: String?): ProviderInfo {
        val entry = AsnWebsiteTable.lookup(asn)
        return ProviderInfo(
            ip = ip,
            asn = asn,
            org = entry?.name ?: asnName?.ifBlank { null },
            isp = null,
            domain = null,
            website = entry?.website,
            country = country,
            countryCode = countryCode,
            city = city,
            source = "2ip.io",
        )
    }

    fun websiteFor(domain: String?, asn: Int?): String? {
        val d = domain?.trim()?.lowercase()?.removePrefix("http://")?.removePrefix("https://")?.trimEnd('/')
        if (!d.isNullOrBlank() && d.contains('.') && !d.contains(' ')) return "https://$d"
        return AsnWebsiteTable.lookup(asn)?.website
    }
}
