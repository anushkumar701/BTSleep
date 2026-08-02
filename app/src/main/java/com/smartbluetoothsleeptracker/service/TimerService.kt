package com.smartbluetoothsleeptracker.service

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.smartbluetoothsleeptracker.BTCurfewApp
import com.smartbluetoothsleeptracker.core.bluetooth.DisconnectResult
import com.smartbluetoothsleeptracker.core.notification.AppNotifications
import com.smartbluetoothsleeptracker.core.playback.FadeResult
import com.smartbluetoothsleeptracker.data.db.DailyUsageEntity
import com.smartbluetoothsleeptracker.data.db.SessionEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TimerService : Service() {
    companion object {
        private const val TAG = "TimerService"

        const val ACTION_START = "com.btcurfew.START"
        const val ACTION_CANCEL = "com.btcurfew.CANCEL"
        const val ACTION_EXTEND = "com.btcurfew.EXTEND"
        const val ACTION_END_NOW = "com.btcurfew.END_NOW"
        const val ACTION_ALLOW_RECONNECT = "com.btcurfew.ALLOW_RECONNECT"

        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_TARGETS = "targets" // comma-separated MAC addresses

        fun startIntent(ctx: Context, minutes: Int, targets: String): Intent =
            Intent(ctx, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MINUTES, minutes)
                putExtra(EXTRA_TARGETS, targets)
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickJob: Job? = null
    private var cooldownTickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Timer state
    private var endTimeMillis = 0L
    private var pausedRemaining: Long? = null
    private var plannedMinutes = 0
    private var extendedMinutes = 0
    private var targetAddresses = listOf<String>()
    private var sessionId = 0L
    private var warningFired = false

    private val app get() = application as BTCurfewApp

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 30)
                val targets = intent.getStringExtra(EXTRA_TARGETS) ?: ""
                startTimer(minutes, targets)
            }
            ACTION_CANCEL -> cancelTimer()
            ACTION_EXTEND -> extendTimer()
            ACTION_END_NOW -> endNow()
            ACTION_ALLOW_RECONNECT -> allowReconnect()
        }
        return START_STICKY
    }

    private fun startTimer(minutes: Int, targets: String) {
        plannedMinutes = minutes
        extendedMinutes = 0
        targetAddresses = targets.split(",").filter { it.isNotBlank() }
        endTimeMillis = System.currentTimeMillis() + minutes * 60_000L
        pausedRemaining = null
        warningFired = false

        // Acquire wake lock
        acquireWakeLock(minutes)

        // Start foreground
        val notif = AppNotifications.timerNotification(this, formatRemaining()).build()
        ServiceCompat.startForeground(
            this, AppNotifications.NOTIF_TIMER, notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        // Persist timer state
        scope.launch {
            app.prefs.setTimerEnd(endTimeMillis)
            app.prefs.setTimerPaused(null)
            app.prefs.setTimerTargets(targets)
            app.prefs.setTimerPlanned(plannedMinutes)
            app.prefs.setTimerExtended(0)

            // Create session record
            val session = SessionEntity(
                deviceAddress = targetAddresses.firstOrNull() ?: "unknown",
                deviceName = getDeviceName(targetAddresses.firstOrNull()),
                startTime = System.currentTimeMillis(),
                plannedDurationMin = plannedMinutes,
                date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
            sessionId = app.db.sessionDao().insert(session)
            app.prefs.setActiveSessionId(sessionId)
        }

        startTickLoop()
        Log.i(TAG, "Timer started: ${minutes}m for ${targetAddresses.size} devices")
    }

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                val remaining = endTimeMillis - System.currentTimeMillis()

                if (remaining <= 0) {
                    onTimerExpired()
                    return@launch
                }

                // Update notification
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.notify(
                    AppNotifications.NOTIF_TIMER,
                    AppNotifications.timerNotification(this@TimerService, formatRemaining()).build()
                )

                // Check warning
                val settings = app.prefs.settings.first()
                if (settings.sleepAlertsEnabled && !warningFired) {
                    val warningMs = settings.warningLeadMinutes * 60_000L
                    if (remaining <= warningMs) {
                        warningFired = true
                        val minLeft = (remaining / 60_000L).toInt().coerceAtLeast(1)
                        nm.notify(
                            AppNotifications.NOTIF_WARNING,
                            AppNotifications.warningNotification(this@TimerService, minLeft).build()
                        )
                    }
                }

                // Broadcast remaining time for UI
                sendBroadcast(Intent("com.btcurfew.TICK").apply {
                    putExtra("remaining", remaining)
                    setPackage(packageName)
                })

                delay(1000)
            }
        }
    }

    /**
     * EXPIRY SEQUENCE (ordered):
     * 1. Playback stop (volume fade)
     * 2. Bluetooth disconnect
     * 3. Screen off
     */
    @SuppressLint("MissingPermission")
    private fun onTimerExpired() {
        Log.i(TAG, "Timer expired — executing expiry sequence")

        scope.launch {
            val settings = app.prefs.settings.first()

            // ── STEP 1: Playback Stop (volume fade) ────────────────────
            if (settings.playbackStopEnabled) {
                Log.i(TAG, "Step 1: Fading out playback over ${settings.fadeOutDurationSeconds}s")
                val fadeResult = app.playbackController.fadeOutAndStop(settings.fadeOutDurationSeconds)

                if (fadeResult == FadeResult.CANCELLED_BY_USER) {
                    Log.i(TAG, "Fade cancelled by volume key — extending timer, skipping disconnect")
                    // Same as tapping notification Extend
                    performExtend()
                    return@launch
                }
                // COMPLETED or DISABLED → continue to step 2
            }

            // ── STEP 2: Bluetooth Disconnect ───────────────────────────
            Log.i(TAG, "Step 2: Bluetooth disconnect")
            val adapter = android.bluetooth.BluetoothManager::class.java
                .let { getSystemService(it) }?.adapter

            val btDevices = mutableListOf<BluetoothDevice>()
            adapter?.bondedDevices?.forEach { dev ->
                if (dev.address in targetAddresses) btDevices.add(dev)
            }

            val result = if (btDevices.isNotEmpty()) {
                app.disconnector.disconnectDevices(
                    devices = btDevices,
                    cooldownSeconds = if (settings.reconnectBlockerEnabled) settings.cooldownSeconds else 0,
                    enableCooldown = settings.reconnectBlockerEnabled
                )
            } else {
                DisconnectResult(true, null, emptyList())
            }

            // ── STEP 3: Screen Off ─────────────────────────────────────
            if (settings.screenOffEnabled) {
                Log.i(TAG, "Step 3: Screen off (lockNow)")
                app.screenController.lockScreen()
            }

            // ── Session & Usage Bookkeeping ────────────────────────────
            val startTime = endTimeMillis - (plannedMinutes + extendedMinutes) * 60_000L
            val actualMin = ((System.currentTimeMillis() - startTime) / 60_000L).toInt()
            app.db.sessionDao().update(
                SessionEntity(
                    id = sessionId,
                    deviceAddress = targetAddresses.firstOrNull() ?: "unknown",
                    deviceName = getDeviceName(targetAddresses.firstOrNull()),
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    plannedDurationMin = plannedMinutes,
                    actualDurationMin = actualMin,
                    disconnectConfirmed = result.success,
                    extendedMinutes = extendedMinutes,
                    date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                )
            )

            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            for (addr in targetAddresses) {
                val existing = app.db.dailyUsageDao().getForDate(today)
                    .find { it.deviceAddress == addr }
                app.db.dailyUsageDao().upsert(
                    DailyUsageEntity(
                        date = today,
                        deviceAddress = addr,
                        totalMinutes = (existing?.totalMinutes ?: 0) + actualMin,
                        sessionCount = (existing?.sessionCount ?: 0) + 1
                    )
                )
            }

            // Notify result
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm.cancel(AppNotifications.NOTIF_WARNING)
            nm.notify(
                AppNotifications.NOTIF_DISCONNECT_RESULT,
                AppNotifications.disconnectResultNotification(
                    this@TimerService, result.success,
                    getDeviceName(targetAddresses.firstOrNull())
                ).build()
            )

            // Start cooldown ticking if active
            if (settings.reconnectBlockerEnabled && settings.cooldownSeconds > 0) {
                startCooldownTick()
            }

            app.prefs.clearTimer()
            releaseWakeLock()

            stopForeground(STOP_FOREGROUND_REMOVE)
            sendBroadcast(Intent("com.btcurfew.TIMER_END").setPackage(packageName))
        }
    }

    private fun cancelTimer() {
        tickJob?.cancel()
        scope.launch {
            if (sessionId > 0) {
                val existing = app.db.sessionDao().getOrphanedSession()
                if (existing != null) {
                    app.db.sessionDao().update(existing.copy(
                        endTime = System.currentTimeMillis(),
                        actualDurationMin = 0,
                        disconnectConfirmed = false
                    ))
                }
            }
            app.prefs.clearTimer()
        }
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        sendBroadcast(Intent("com.btcurfew.TIMER_END").setPackage(packageName))
        Log.i(TAG, "Timer cancelled")
    }

    private fun extendTimer() {
        scope.launch { performExtend() }
    }

    /**
     * Shared extend logic — used by notification Extend button AND playback fade cancel.
     */
    private suspend fun performExtend() {
        val settings = app.prefs.settings.first()
        val addMs = settings.extendMinutes * 60_000L
        endTimeMillis += addMs
        extendedMinutes += settings.extendMinutes
        warningFired = false

        app.prefs.setTimerEnd(endTimeMillis)
        app.prefs.setTimerExtended(extendedMinutes)

        val remainingMs = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val remainingMin = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        acquireWakeLock(remainingMin)

        // Restart tick loop if it was stopped
        if (tickJob?.isActive != true) {
            startTickLoop()
        }

        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(AppNotifications.NOTIF_WARNING)

        Log.i(TAG, "Extended by ${settings.extendMinutes}m (total extended: ${extendedMinutes}m)")
    }

    private fun endNow() {
        tickJob?.cancel()
        onTimerExpired()
    }

    private fun allowReconnect() {
        app.disconnector.endCooldown()
        cooldownTickJob?.cancel()
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(AppNotifications.NOTIF_COOLDOWN)
        Log.i(TAG, "Cooldown ended by user")
        stopSelf()
    }

    private fun startCooldownTick() {
        cooldownTickJob?.cancel()
        cooldownTickJob = scope.launch {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            while (app.disconnector.isCooldownActive) {
                val remaining = (app.disconnector.cooldownState.value.expiresAt - System.currentTimeMillis()) / 1000
                if (remaining <= 0) break
                nm.notify(
                    AppNotifications.NOTIF_COOLDOWN,
                    AppNotifications.cooldownNotification(this@TimerService, remaining.toInt()).build()
                )
                delay(1000)
            }
            nm.cancel(AppNotifications.NOTIF_COOLDOWN)
            app.disconnector.endCooldown()
            Log.i(TAG, "Cooldown expired naturally, stopping service")
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceName(address: String?): String {
        if (address == null) return "Unknown"
        return runCatching {
            val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
            adapter?.bondedDevices?.find { it.address == address }?.name ?: address
        }.getOrDefault(address)
    }

    private fun formatRemaining(): String {
        val ms = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d remaining", h, m, s)
        else String.format("%d:%02d remaining", m, s)
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    private fun acquireWakeLock(minutes: Int) {
        releaseWakeLock()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "btcurfew:timer").apply {
            acquire(minutes * 60_000L + 60_000L) // timer + 1 min buffer
        }
    }

    override fun onDestroy() {
        tickJob?.cancel()
        cooldownTickJob?.cancel()
        app.disconnector.endCooldown()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
