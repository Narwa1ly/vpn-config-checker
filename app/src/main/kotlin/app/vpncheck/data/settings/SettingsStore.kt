package app.vpncheck.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.vpncheck.data.subscription.SubscriptionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val enabledSources: Set<String>,
    val concurrency: Int,
    val timeoutSec: Int,
    val showFailed: Boolean,
    /** Re-download subscriptions at the start of every full check run (off: check uses cached configs). */
    val refreshBeforeCheck: Boolean,
    val lastRunMobileAt: Long?,
    val lastRunWifiAt: Long?,
    val selectedTab: String,
) {
    companion object {
        val DEFAULT = AppSettings(
            enabledSources = SubscriptionSource.ALL.filter { it.enabledByDefault }.map { it.id }.toSet(),
            concurrency = 4,
            timeoutSec = 15,
            showFailed = true,
            refreshBeforeCheck = false,
            lastRunMobileAt = null,
            lastRunWifiAt = null,
            selectedTab = "MOBILE",
        )
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val SOURCES = stringSetPreferencesKey("enabled_sources")
        val SOURCES_SET = booleanPreferencesKey("enabled_sources_set")
        val CONCURRENCY = intPreferencesKey("concurrency")
        val TIMEOUT = intPreferencesKey("timeout_sec")
        val SHOW_FAILED = booleanPreferencesKey("show_failed")
        val REFRESH_BEFORE_CHECK = booleanPreferencesKey("refresh_before_check")
        val LAST_MOBILE = longPreferencesKey("last_run_mobile")
        val LAST_WIFI = longPreferencesKey("last_run_wifi")
        val TAB = stringPreferencesKey("selected_tab")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            enabledSources = if (p[Keys.SOURCES_SET] == true) p[Keys.SOURCES] ?: emptySet() else AppSettings.DEFAULT.enabledSources,
            concurrency = (p[Keys.CONCURRENCY] ?: AppSettings.DEFAULT.concurrency).coerceIn(1, 8),
            timeoutSec = (p[Keys.TIMEOUT] ?: AppSettings.DEFAULT.timeoutSec).coerceIn(5, 60),
            showFailed = p[Keys.SHOW_FAILED] ?: AppSettings.DEFAULT.showFailed,
            refreshBeforeCheck = p[Keys.REFRESH_BEFORE_CHECK] ?: AppSettings.DEFAULT.refreshBeforeCheck,
            lastRunMobileAt = p[Keys.LAST_MOBILE],
            lastRunWifiAt = p[Keys.LAST_WIFI],
            selectedTab = p[Keys.TAB] ?: AppSettings.DEFAULT.selectedTab,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { p ->
            val cur = if (p[Keys.SOURCES_SET] == true) p[Keys.SOURCES] ?: emptySet() else AppSettings.DEFAULT.enabledSources
            p[Keys.SOURCES] = if (enabled) cur + id else cur - id
            p[Keys.SOURCES_SET] = true
        }
    }

    suspend fun setConcurrency(v: Int) = context.dataStore.edit { it[Keys.CONCURRENCY] = v.coerceIn(1, 8) }
    suspend fun setTimeout(v: Int) = context.dataStore.edit { it[Keys.TIMEOUT] = v.coerceIn(5, 60) }
    suspend fun setShowFailed(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_FAILED] = v }
    suspend fun setRefreshBeforeCheck(v: Boolean) = context.dataStore.edit { it[Keys.REFRESH_BEFORE_CHECK] = v }
    suspend fun setSelectedTab(tab: String) = context.dataStore.edit { it[Keys.TAB] = tab }

    suspend fun markRun(networkType: String, at: Long) = context.dataStore.edit {
        if (networkType == "WIFI") it[Keys.LAST_WIFI] = at else it[Keys.LAST_MOBILE] = at
    }
}
