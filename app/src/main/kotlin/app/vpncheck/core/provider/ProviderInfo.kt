package app.vpncheck.core.provider

data class ProviderInfo(
    val ip: String,
    val asn: Int?,
    val org: String?,
    val isp: String?,
    val domain: String?,
    val website: String?,
    val country: String?,
    val countryCode: String?,
    val city: String?,
    val source: String,
) {
    /** Human-readable provider name. */
    val displayName: String
        get() = org?.takeIf { it.isNotBlank() }
            ?: isp?.takeIf { it.isNotBlank() }
            ?: AsnWebsiteTable.lookup(asn)?.name
            ?: asn?.let { "AS$it" }
            ?: "неизвестный провайдер"
}
