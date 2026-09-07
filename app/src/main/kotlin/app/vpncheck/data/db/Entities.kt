package app.vpncheck.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "configs")
data class ConfigEntity(
    @PrimaryKey val id: String,
    val rawLink: String,
    val protocol: String,
    val transport: String,
    val security: String,
    val host: String,
    val port: Int,
    val name: String,
    /** Comma-separated, sorted [app.vpncheck.data.subscription.SubscriptionSource] ids; see SourceMembership. */
    val sourceIds: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val unsupportedReason: String?,
)

@Entity(tableName = "check_results", primaryKeys = ["configId", "networkType"])
data class CheckResultEntity(
    val configId: String,
    /** [app.vpncheck.core.network.NetworkType] name: MOBILE or WIFI. */
    val networkType: String,
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
    val coreLog: String?,
    val checkedAt: Long,
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val ip: String,
    val asn: Int?,
    val org: String?,
    val isp: String?,
    val domain: String?,
    val website: String?,
    val country: String?,
    val countryCode: String?,
    val city: String?,
    val source: String,
    val resolvedAt: Long,
) {
    val displayName: String
        get() = org?.takeIf { it.isNotBlank() }
            ?: isp?.takeIf { it.isNotBlank() }
            ?: asn?.let { "AS$it" }
            ?: "неизвестный провайдер"
}

data class ConfigWithResults(
    @Embedded val config: ConfigEntity,
    @Relation(parentColumn = "id", entityColumn = "configId")
    val results: List<CheckResultEntity>,
)

/** Result of the last download attempt per subscription source. */
@Entity(tableName = "source_status")
data class SourceStatusEntity(
    @PrimaryKey val sourceId: String,
    val lastAttemptAt: Long,
    val lastSuccessAt: Long?,
    /** Number of links in the last successful download. */
    val lastCount: Int,
    /** Short host of the mirror that answered last time. */
    val lastMirror: String?,
    /** Null when the last attempt succeeded. */
    val lastError: String?,
) {
    val lastAttemptOk: Boolean get() = lastError == null && lastSuccessAt != null
}
