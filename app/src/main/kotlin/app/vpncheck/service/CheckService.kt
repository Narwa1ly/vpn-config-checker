package app.vpncheck.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import app.vpncheck.App
import app.vpncheck.R
import app.vpncheck.core.network.NetworkType
import app.vpncheck.data.repo.RunState
import app.vpncheck.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Foreground service that keeps a check run alive while the app is in background. */
class CheckService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                App.container(this).runner.cancel()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val mode = intent.getStringExtra(EXTRA_MODE)?.let { runCatching { NetworkType.valueOf(it) }.getOrNull() }
                    ?: NetworkType.MOBILE
                val ids = intent.getStringArrayListExtra(EXTRA_IDS)
                startInForeground(buildNotification("Подготовка…", 0, 0, true))
                acquireWakeLock()
                val runner = App.container(this).runner
                if (runJob?.isActive == true) return START_NOT_STICKY
                runJob = scope.launch {
                    val progressJob = launch {
                        runner.state.onEach { s -> updateNotification(s) }.collect()
                    }
                    try {
                        runner.run(mode, ids)
                    } finally {
                        progressJob.cancel()
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(s: RunState) {
        val n = when (s) {
            is RunState.Fetching -> buildNotification("Загрузка подписок ${s.done}/${s.total}", s.done, s.total, s.total == 0)
            is RunState.Running -> buildNotification("${s.network.label}: ${s.done}/${s.total} · рабочих ${s.ok}", s.done, s.total, false)
            else -> return
        }
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    private fun buildNotification(text: String, progress: Int, max: Int, indeterminate: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CheckService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Проверка VPN-конфигов")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .setProgress(max, progress, indeterminate)
            .addAction(Notification.Action.Builder(null, "Остановить", stop).build())
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vpncheck:run").apply {
            setReferenceCounted(false)
            acquire(45 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.vpncheck.action.START"
        const val ACTION_STOP = "app.vpncheck.action.STOP"
        const val EXTRA_MODE = "mode"
        const val EXTRA_IDS = "ids"
        private const val NOTIF_ID = 1001

        fun start(context: Context, mode: NetworkType, ids: List<String>? = null) {
            val i = Intent(context, CheckService::class.java).setAction(ACTION_START).putExtra(EXTRA_MODE, mode.name)
            if (ids != null) i.putStringArrayListExtra(EXTRA_IDS, ArrayList(ids))
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CheckService::class.java).setAction(ACTION_STOP))
        }
    }
}
