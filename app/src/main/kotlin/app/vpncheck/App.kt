package app.vpncheck

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.vpncheck.core.engine.CoreRunner
import app.vpncheck.core.network.NetworkTypeDetector
import app.vpncheck.core.provider.ProviderResolver
import app.vpncheck.data.db.AppDatabase
import app.vpncheck.data.repo.CheckRunner
import app.vpncheck.data.settings.SettingsStore
import app.vpncheck.data.subscription.SubscriptionFetcher

class AppContainer(context: Context) {
    val db: AppDatabase by lazy { AppDatabase.create(context) }
    val settings: SettingsStore by lazy { SettingsStore(context) }
    val detector: NetworkTypeDetector by lazy { NetworkTypeDetector(context) }
    val coreRunner: CoreRunner by lazy { CoreRunner(context) }
    val runner: CheckRunner by lazy {
        CheckRunner(db, settings, SubscriptionFetcher(), coreRunner, detector, ProviderResolver())
    }
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "check_progress"
        fun container(context: Context): AppContainer = (context.applicationContext as App).container
    }
}
