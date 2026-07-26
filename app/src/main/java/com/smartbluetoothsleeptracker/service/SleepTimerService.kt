package com.smartbluetoothsleeptracker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.smartbluetoothsleeptracker.SleepBTApp
import com.smartbluetoothsleeptracker.widget.WidgetUpdater
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

class SleepTimerService : Service() {

    private val app by lazy { application as SleepBTApp }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var timerObserverJob: Job? = null
    private var batteryCheckJob: Job? = null
    private var blockerJob: Job? = null     // periodic re-disconnect during block window
    private var twoMinWarningFired = false
    private var lastAudioActiveMs = System.currentTimeMillis()

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        // Android 14: startForeground MUST be called immediately in onCreate
        val notification = app.notifications.buildForegroundNotification(
            remainingMillis = app.timerManager.getRemainingMillis(),
            deviceName = app.btMonitor.firstDevice()?.name,
            isPaused = app.timerManager.state.value.isPaused,
            blockerActive = app.btDisconnector.isBlockerActive
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                com.smartbluetoothsleeptracker.core.notification.AppNotifications.ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(
                com.smartbluetoothsleeptracker.core.notification.AppNotifications.ID_FOREGROUND,
                notification
            )
        }
        startObserving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                val minutes = intent.getLongExtra(EXTRA_MINUTES, 0L)
                if (minutes > 0L && !app.timerManager.isRunning()) {
                    app.analyticsHelper.trackNotificationActionClicked("start_timer")
                    app.timerManager.startTimer(minutes)
                    twoMinWarningFired = false
                }
            }
            ACTION_CANCEL_TIMER -> {
                app.analyticsHelper.trackNotificationActionClicked("stop_timer")
                app.timerManager.clearTimer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_EXTEND_TIMER -> {
                app.analyticsHelper.trackNotificationActionClicked("extend_timer")
                val extra = intent.getLongExtra(EXTRA_MINUTES, 10L)
                app.timerManager.extendTimer(extra)
                twoMinWarningFired = false
            }
            ACTION_DISCONNECT_NOW -> {
                app.analyticsHelper.trackNotificationActionClicked("disconnect_now")
                val devices = app.btMonitor.devices.value
                app.btDisconnector.disconnectAll(devices)
                app.timerManager.clearTimer()
                startBlockerEnforcement()           // keep retrying
            }
            ACTION_DISMISS_WARNING -> {
                app.notifications.cancelWarning()
            }
        }
        refreshNotification()
        WidgetUpdater.update(applicationContext)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (app.timerManager.isRunning()) startIfAllowed(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Observers ──────────────────────────────────────────────────────────────

    private fun startObserving() {
        timerObserverJob = scope.launch {
            app.timerManager.state.collectLatest { state ->
                if (state.isRunning) {
                    val remaining = state.remainingMillis
                    if (remaining in 1L..120_000L && !twoMinWarningFired) {
                        twoMinWarningFired = true
                        app.notifications.showTwoMinuteWarning(app.btMonitor.firstDevice()?.name)
                    }
                    if (remaining == 0L) onTimerFinished()
                }
                refreshNotification()
                WidgetUpdater.update(applicationContext)
                stopSelfIfIdle()
            }
        }

        batteryCheckJob = scope.launch {
            while (true) {
                delay(120_000L)
                checkBatterySaver()
            }
        }
    }

    private fun onTimerFinished() {
        val devices = app.btMonitor.devices.value
        val totalMinutes = app.timerManager.state.value.totalWallClockMillis?.let {
            it / 60_000L
        } ?: 0L
        app.analyticsHelper.trackTimerCompleted(totalMinutes.coerceAtLeast(1L))
        app.btDisconnector.disconnectAll(devices, blockForMillis = 120_000L)
        app.timerManager.clearTimer()
        startBlockerEnforcement()   // retry every 3 s for up to 2 min
    }

    /**
     * Periodically sweeps all connected audio devices and disconnects them.
     * Runs every 3 seconds while the blocker is active.
     * This is the key fix: even if the first disconnect is rejected by the OS
     * (Android 12+) or the device reconnects, the next sweep will catch it.
     */
    private fun startBlockerEnforcement() {
        blockerJob?.cancel()
        blockerJob = scope.launch(Dispatchers.IO) {
            repeat(40) {            // 40 × 3 s = 120 s = 2 min max
                delay(3_000L)
                if (!app.btDisconnector.isBlockerActive) return@launch
                app.btDisconnector.disconnectAllConnectedNow()
            }
        }
    }

    private suspend fun checkBatterySaver() {
        val settings = app.prefs.settings.first()
        if (!settings.batterySaverEnabled) return
        if (!app.btMonitor.hasConnectedDevice()) return

        val am = getSystemService(AudioManager::class.java) ?: return
        if (am.isMusicActive) {
            lastAudioActiveMs = System.currentTimeMillis()
            return
        }
        val idleMs = settings.idleMinutes * 60_000L
        if (System.currentTimeMillis() - lastAudioActiveMs >= idleMs) {
            val devices = app.btMonitor.devices.value
            app.btDisconnector.disconnectAll(devices, blockForMillis = 30_000L)
            lastAudioActiveMs = System.currentTimeMillis()
        }
    }

    private fun refreshNotification() {
        app.notifications.updateForegroundNotification(
            remainingMillis = app.timerManager.getRemainingMillis(),
            deviceName = app.btMonitor.firstDevice()?.name,
            isPaused = app.timerManager.state.value.isPaused,
            blockerActive = app.btDisconnector.isBlockerActive
        )
    }

    private fun stopSelfIfIdle() {
        val state = app.timerManager.state.value
        if (!state.isActive && !app.btDisconnector.isBlockerActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        const val ACTION_START_TIMER    = "sleepbt.START_TIMER"
        const val ACTION_CANCEL_TIMER   = "sleepbt.CANCEL_TIMER"
        const val ACTION_EXTEND_TIMER   = "sleepbt.EXTEND_TIMER"
        const val ACTION_DISCONNECT_NOW = "sleepbt.DISCONNECT_NOW"
        const val ACTION_DISMISS_WARNING= "sleepbt.DISMISS_WARNING"
        const val EXTRA_MINUTES         = "extra_minutes"

        fun startIntent(ctx: Context)         = Intent(ctx, SleepTimerService::class.java)
        fun cancelIntent(ctx: Context)        = Intent(ctx, SleepTimerService::class.java).apply { action = ACTION_CANCEL_TIMER }
        fun disconnectNowIntent(ctx: Context) = Intent(ctx, SleepTimerService::class.java).apply { action = ACTION_DISCONNECT_NOW }
        fun dismissWarningIntent(ctx: Context)= Intent(ctx, SleepTimerService::class.java).apply { action = ACTION_DISMISS_WARNING }

        fun extendIntent(ctx: Context, minutes: Long = 10L) =
            Intent(ctx, SleepTimerService::class.java).apply {
                action = ACTION_EXTEND_TIMER
                putExtra(EXTRA_MINUTES, minutes)
            }

        fun startTimerIntent(ctx: Context, minutes: Long) =
            Intent(ctx, SleepTimerService::class.java).apply {
                action = ACTION_START_TIMER
                putExtra(EXTRA_MINUTES, minutes)
            }

        fun startIfAllowed(ctx: Context) = runCatching {
            ContextCompat.startForegroundService(ctx, startIntent(ctx))
        }.isSuccess

        fun startTimerIfAllowed(ctx: Context, minutes: Long) = runCatching {
            ContextCompat.startForegroundService(ctx, startTimerIntent(ctx, minutes))
        }.isSuccess
    }
}
