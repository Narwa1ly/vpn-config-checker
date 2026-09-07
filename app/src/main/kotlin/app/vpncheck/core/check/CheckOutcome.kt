package app.vpncheck.core.check

data class CheckOutcome(
    val ok: Boolean,
    val latencyMs: Long?,
    val exitIp: String?,
    val country: String?,
    val countryCode: String?,
    val city: String?,
    val asnId: Int?,
    val asnName: String?,
    val httpStatus: Int?,
    val error: String?,
) {
    companion object {
        fun failure(error: String, status: Int? = null) =
            CheckOutcome(false, null, null, null, null, null, null, null, status, error)
    }
}
