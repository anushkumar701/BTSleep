package com.smartbluetoothsleeptracker.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.smartbluetoothsleeptracker.ActiveTimerState
import com.smartbluetoothsleeptracker.MainActivity
import com.smartbluetoothsleeptracker.SleepBTApp
import com.smartbluetoothsleeptracker.core.bluetooth.DisconnectResult
import com.smartbluetoothsleeptracker.core.haptics.HapticManager
import com.smartbluetoothsleeptracker.core.notification.AppNotifications
import com.smartbluetoothsleeptracker.core.playback.FadeResult
import com.smartbluetoothsleeptracker.core.sensor.ShakeDetector
import com.smartbluetoothsleeptracker.data.db.SessionEntity
import com.smartbluetoothsleeptracker.data.prefs.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TimerService : Service() {
    companion object {
        private const val TAG = "TimerService"

        const val ACTION_START = "com.sleepbt.START"
        const val ACTION_CANCEL = "com.sleepbt.CANCEL"
        const val ACTION_EXTEND = "com.sleepbt.EXTEND"
        const val ACTION_END_NOW = "com.sleepbt.END_NOW"
        const val ACTION_ALLOW_RECONNECT = "com.sleepbt.ALLOW_RECONNECT"
        const val ACTION_PAUSE = "com.sleepbt.PAUSE"
        const val ACTION_RESUME = "com.sleepbt.RESUME"
        const val ACTION_ALARM_BACKSTOP = "com.sleepbt.ALARM_BACKSTOP"

        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_TARGETS = "targets" // comma-separated MAC addresses

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startIntent(ctx: Context, minutes: Int, targets: String): Intent =
            Intent(ctx, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MINUTES, minutes)
                putExtra(EXTRA_TARGETS, targets)
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickJob: Job? = null
    private var settingsJob: Job? = null
    private var cooldownTickJob: Job? = null
    private var autoResumeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Timer state
    @Volatile
    private var isExpiring = false
    private var endTimeMillis = 0L
    private var targetElapsedRealtime = 0L
    private var pausedRemaining: Long? = null
    private var isPaused = false
    private var pauseStartTime = 0L
    private var totalPausedMs = 0L
    private var plannedMinutes = 0
    private var extendedMinutes = 0
    private var targetAddresses = listOf<String>()
    private var sessionId = 0L
    private var sessionStartTime = 0L
    private var warningFired = false
    private var cachedSettings: AppSettings = AppSettings()
    private var warningVolumeReceiver: android.content.BroadcastReceiver? = null

    private val app get() = application as SleepBTApp
    private var shakeDetector: ShakeDetector? = null

    override fun onCreate() {
        super.onCreate()
        shakeDetector = ShakeDetector(this) {
            Log.i(TAG, "Shake gesture detected during warning! Extending timer")
            extendTimer()
        }
        settingsJob = scope.launch {
            app.prefs.settings.collect { cachedSettings = it }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            Log.i(TAG, "TimerService restarted by system with null intent (START_STICKY)")
            restoreTimerFromPrefs()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 30)
                val targets = intent.getStringExtra(EXTRA_TARGETS) ?: ""
                startTimer(minutes, targets)
            }
            ACTION_CANCEL -> cancelTimer()
            ACTION_EXTEND -> extendTimer()
            ACTION_END_NOW -> endNow()
            ACTION_ALLOW_RECONNECT -> allowReconnect()
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_ALARM_BACKSTOP -> {
                Log.i(TAG, "Alarm backstop triggered")
                if (isRunning && !isPaused) {
                    onTimerExpired(fromUserEndNow = false)
                } else if (!isRunning) {
                    restoreTimerFromPrefs()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundSafe(id: Int, notification: android.app.Notification): Boolean {
        return try {
            ServiceCompat.startForeground(
                this, id, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    "SleepBT background service failed to start: ${e.localizedMessage ?: "Permission or security error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
            isRunning = false
            app.updateTimerState(ActiveTimerState())
            stopSelf()
            false
        }
    }

    private fun restoreTimerFromPrefs() {
        scope.launch {
            cachedSettings = app.prefs.settings.first()
            val settings = cachedSettings
            val endWall = settings.timerEndWallClock
            val pausedRem = settings.timerPausedRemaining
            val targets = settings.timerTargetDevices
            val planned = settings.timerPlannedMinutes
            val extended = settings.timerExtendedMinutes
            val activeSession = settings.activeSessionId

            if (endWall == null && pausedRem == null) {
                Log.i(TAG, "No persisted timer found on restart — stopping service")
                val notif = AppNotifications.timerNotification(
                    ctx = this@TimerService,
                    remainingText = "Finishing...",
                    isPaused = false,
                    endTimeMillis = 0L,
                    deviceName = "SleepBT"
                ).build()
                startForegroundSafe(AppNotifications.NOTIF_TIMER, notif)
                isRunning = false
                app.updateTimerState(ActiveTimerState())
                ServiceCompat.stopForeground(this@TimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            isRunning = true
            plannedMinutes = planned
            extendedMinutes = extended
            targetAddresses = targets.split(",").filter { it.isNotBlank() }
            sessionId = activeSession
            sessionStartTime = settings.timerSessionStartTime
            totalPausedMs = settings.timerTotalPausedMs
            pauseStartTime = settings.timerPauseStartTime
            warningFired = false

            if (pausedRem != null) {
                isPaused = true
                pausedRemaining = pausedRem
                if (pauseStartTime <= 0L) {
                    pauseStartTime = System.currentTimeMillis()
                    app.prefs.setTimerPauseStart(pauseStartTime)
                }
                val notif = buildTimerNotification()
                if (!startForegroundSafe(AppNotifications.NOTIF_TIMER, notif)) return@launch

                app.updateTimerState(
                    ActiveTimerState(
                        isRunning = true,
                        isPaused = true,
                        remainingMs = pausedRem,
                        endTimeMillis = 0L,
                        plannedMinutes = plannedMinutes,
                        extendedMinutes = extendedMinutes,
                        targetAddresses = targetAddresses
                    )
                )
            } else if (endWall != null) {
                val now = System.currentTimeMillis()
                endTimeMillis = endWall
                if (TimerCalculations.isTimerExpired(endTimeMillis, now)) {
                    Log.i(TAG, "Persisted timer expired while process was dead — running expiry immediately")
                    val notif = AppNotifications.timerNotification(
                        ctx = this@TimerService,
                        remainingText = "Stopping audio...",
                        isPaused = false,
                        endTimeMillis = 0L,
                        deviceName = getDeviceName(targetAddresses.firstOrNull())
                    ).build()
                    if (!startForegroundSafe(AppNotifications.NOTIF_TIMER, notif)) return@launch

                    onTimerExpired(fromUserEndNow = false)
                } else {
                    val remainingMs = TimerCalculations.calculateRemainingMs(endTimeMillis, now)
                    val remainingMin = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
                    acquireWakeLock(remainingMin)
                    scheduleExpiryAlarm(endTimeMillis)

                    val notif = buildTimerNotification()
                    if (!startForegroundSafe(AppNotifications.NOTIF_TIMER, notif)) return@launch

                    app.updateTimerState(
                        ActiveTimerState(
                            isRunning = true,
                            isPaused = false,
                            remainingMs = remainingMs,
                            endTimeMillis = endTimeMillis,
                            plannedMinutes = plannedMinutes,
                            extendedMinutes = extendedMinutes,
                            targetAddresses = targetAddresses
                        )
                    )
                    startTickLoop()
                }
            }
        }
    }

    private fun startTimer(minutes: Int, targets: String) {
        isRunning = true
        plannedMinutes = minutes
        extendedMinutes = 0
        targetAddresses = targets.split(",").filter { it.isNotBlank() }
        if (targetAddresses.isEmpty()) {
            targetAddresses = app.btMonitor.connectedDevices.value.map { it.address }
        }
        endTimeMillis = System.currentTimeMillis() + minutes * 60_000L
        targetElapsedRealtime = SystemClock.elapsedRealtime() + minutes * 60_000L
        pausedRemaining = null
        isPaused = false
        totalPausedMs = 0L
        pauseStartTime = 0L
        sessionStartTime = System.currentTimeMillis()
        warningFired = false

        // Acquire wake lock & schedule alarm backstop
        acquireWakeLock(minutes)
        scheduleExpiryAlarm(endTimeMillis)

        // Start foreground
        val notif = buildTimerNotification()
        if (!startForegroundSafe(AppNotifications.NOTIF_TIMER, notif)) return

        app.updateTimerState(
            ActiveTimerState(
                isRunning = true,
                isPaused = false,
                remainingMs = minutes * 60_000L,
                endTimeMillis = endTimeMillis,
                plannedMinutes = plannedMinutes,
                extendedMinutes = extendedMinutes,
                targetAddresses = targetAddresses
            )
        )

        // Persist timer state
        scope.launch {
            cachedSettings = app.prefs.settings.first()
            app.prefs.setTimerEnd(endTimeMillis)
            app.prefs.setTimerPaused(null)
            app.prefs.setTimerTargets(targets)
            app.prefs.setTimerPlanned(plannedMinutes)
            app.prefs.setTimerExtended(0)
            app.prefs.setTimerSessionStart(sessionStartTime)
            app.prefs.setTimerTotalPaused(0L)
            app.prefs.setTimerPauseStart(0L)

            // Check if any target device had an active background connection prior to timer start,
            // log that prior session so it is accurately recorded without overlapping with the timer
            for (addr in targetAddresses) {
                val connectTime = com.smartbluetoothsleeptracker.receiver.BluetoothReceiver.getActiveConnectTime(app, addr)
                if (connectTime > 0L && sessionStartTime > connectTime) {
                    val priorMs = sessionStartTime - connectTime
                    val priorMin = TimerCalculations.calculateDurationMinutes(priorMs)
                    if (priorMin >= 1) {
                        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val dev = app.db.deviceDao().getDevice(addr)
                        app.db.sessionDao().insert(
                            SessionEntity(
                                deviceAddress = addr,
                                deviceName = dev?.name ?: addr,
                                startTime = connectTime,
                                endTime = sessionStartTime,
                                plannedDurationMin = priorMin,
                                actualDurationMin = priorMin,
                                disconnectConfirmed = false,
                                date = today
                            )
                        )
                        app.db.dailyUsageDao().recordUsage(today, addr, priorMin)
                    }
                }
                // Set connect time to sessionStartTime so future background time does not double-count
                com.smartbluetoothsleeptracker.receiver.BluetoothReceiver.setActiveConnectTime(app, addr, sessionStartTime)
            }

            // Create initial session record
            val session = SessionEntity(
                deviceAddress = targetAddresses.firstOrNull() ?: "unknown",
                deviceName = getDeviceName(targetAddresses.firstOrNull()),
                startTime = sessionStartTime,
                plannedDurationMin = plannedMinutes,
                date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
            sessionId = app.db.sessionDao().insert(session)
            app.prefs.setActiveSessionId(sessionId)
        }

        startTickLoop()
        Log.i(TAG, "Timer started: ${minutes}m for ${targetAddresses.size} devices")
    }

    private fun pauseTimer() {
        if (isPaused) return
        isPaused = true
        pauseStartTime = System.currentTimeMillis()
        val remainingFromElapsed = targetElapsedRealtime - SystemClock.elapsedRealtime()
        pausedRemaining = remainingFromElapsed.coerceAtLeast(0L)
        cancelExpiryAlarm()
        releaseWakeLock()
        tickJob?.cancel()

        scope.launch {
            app.prefs.setTimerPaused(pausedRemaining)
            app.prefs.setTimerPauseStart(pauseStartTime)
            app.prefs.setTimerTotalPaused(totalPausedMs)
        }

        app.updateTimerState(
            ActiveTimerState(
                isRunning = true,
                isPaused = true,
                remainingMs = pausedRemaining ?: 0L,
                endTimeMillis = 0L,
                plannedMinutes = plannedMinutes,
                extendedMinutes = extendedMinutes,
                targetAddresses = targetAddresses
            )
        )

        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(
            AppNotifications.NOTIF_TIMER,
            buildTimerNotification()
        )

        // Auto-resume after 2 hours (120 minutes) if paused continuously
        autoResumeJob?.cancel()
        autoResumeJob = scope.launch {
            delay(2 * 60 * 60 * 1000L)
            if (isPaused) {
                Log.i(TAG, "Auto-resuming timer after 2h max pause limit")
                resumeTimer()
            }
        }
        Log.i(TAG, "Timer paused — remaining: ${pausedRemaining?.div(1000)}s")
    }

    private fun resumeTimer() {
        if (!isPaused) return
        isPaused = false
        autoResumeJob?.cancel()

        val now = System.currentTimeMillis()
        val pausedDuration = TimerCalculations.calculatePausedAccumulation(pauseStartTime, now)
        totalPausedMs += pausedDuration
        pauseStartTime = 0L
        val remainingMs = (pausedRemaining ?: 0L).coerceAtLeast(0L)
        acquireWakeLock(((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1))
        endTimeMillis = TimerCalculations.calculateResumeEndTime(now, remainingMs)
        targetElapsedRealtime = SystemClock.elapsedRealtime() + remainingMs
        pausedRemaining = null

        scheduleExpiryAlarm(endTimeMillis)

        scope.launch {
            app.prefs.setTimerEnd(endTimeMillis)
            app.prefs.setTimerPaused(null)
            app.prefs.setTimerPauseStart(0L)
            app.prefs.setTimerTotalPaused(totalPausedMs)
        }

        app.updateTimerState(
            ActiveTimerState(
                isRunning = true,
                isPaused = false,
                remainingMs = remainingMs,
                endTimeMillis = endTimeMillis,
                plannedMinutes = plannedMinutes,
                extendedMinutes = extendedMinutes,
                targetAddresses = targetAddresses
            )
        )

        startTickLoop()
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(
            AppNotifications.NOTIF_TIMER,
            buildTimerNotification()
        )
        Log.i(TAG, "Timer resumed — new end time calculated")
    }

    private fun startTickLoop() {
        tickJob?.cancel()
        targetElapsedRealtime = SystemClock.elapsedRealtime() + (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)

        tickJob = scope.launch {
            var tickCount = 0
            var consecutiveDisconnectedSeconds = 0
            while (isActive && !isPaused) {
                val remaining = (targetElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

                if (remaining <= 0) {
                    val nm = getSystemService(android.app.NotificationManager::class.java)
                    nm.cancel(AppNotifications.NOTIF_TIMER)
                    app.updateTimerState(ActiveTimerState(isRunning = false, remainingMs = 0L))
                    onTimerExpired(fromUserEndNow = false)
                    return@launch
                }

                // If earbuds or Bluetooth device are manually disconnected mid-session:
                // Check if target devices (or all connected audio devices) are disconnected.
                // A 5-second grace period handles momentary handoff between left & right bud or codec switch.
                val connectedAddrs = app.btMonitor.connectedDevices.value.map { it.address }.toSet()
                val isAnyTargetConnected = if (targetAddresses.isNotEmpty()) {
                    targetAddresses.any { it in connectedAddrs }
                } else {
                    connectedAddrs.isNotEmpty()
                }

                val hasWiredTarget = targetAddresses.contains(com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.WIRED_ADDRESS)
                val isWiredConnected = com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.isWiredConnected(this@TimerService)
                val isBtEnabled = getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled ?: false

                val isTargetDisconnected = when {
                    hasWiredTarget -> !isWiredConnected
                    targetAddresses.isNotEmpty() -> !isBtEnabled || !isAnyTargetConnected
                    else -> false // phone speaker mode
                }

                if (isTargetDisconnected) {
                    consecutiveDisconnectedSeconds++
                    if (consecutiveDisconnectedSeconds >= 5) {
                        Log.i(TAG, "Device manually disconnected for 5s (or BT turned off). Finishing session cleanly.")
                        val nm = getSystemService(android.app.NotificationManager::class.java)
                        nm.cancel(AppNotifications.NOTIF_TIMER)
                        app.updateTimerState(ActiveTimerState(isRunning = false, remainingMs = 0L))
                        finishSessionOnManualDisconnect()
                        return@launch
                    }
                } else {
                    consecutiveDisconnectedSeconds = 0
                }

                app.updateTimerState(
                    ActiveTimerState(
                        isRunning = true,
                        isPaused = false,
                        remainingMs = remaining,
                        endTimeMillis = endTimeMillis,
                        plannedMinutes = plannedMinutes,
                        extendedMinutes = extendedMinutes,
                        targetAddresses = targetAddresses
                    )
                )

                val settings = cachedSettings
                val nm = getSystemService(android.app.NotificationManager::class.java)

                var justFiredWarning = false
                if (settings.sleepAlertsEnabled && !warningFired) {
                    val plannedMs = plannedMinutes * 60_000L
                    val rawWarningMs = settings.warningLeadMinutes * 60_000L
                    val warningMs = minOf(rawWarningMs, plannedMs / 2)
                    if (remaining <= warningMs && warningMs > 0) {
                        warningFired = true
                        justFiredWarning = true
                        shakeDetector?.startListening()
                        startWarningVolumeListener()
                        val leadStr = TimerCalculations.formatRemainingLeadText(remaining)
                        nm.notify(
                            AppNotifications.NOTIF_TIMER,
                            buildTimerNotification(warningText = "$leadStr remaining • Press volume key to extend")
                        )
                        HapticManager.vibrateWarning(this@TimerService)
                    }
                }

                // Throttle notification updates: chronometer animates natively on lockscreen/shade,
                // so we only update every 5 seconds (or immediately when warning triggers) to prevent GC churn
                if (justFiredWarning || (tickCount % 5 == 0)) {
                    val warnText = if (warningFired) {
                        val leadStr = TimerCalculations.formatRemainingLeadText(remaining)
                        "$leadStr remaining • Press volume key to extend"
                    } else null
                    nm.notify(
                        AppNotifications.NOTIF_TIMER,
                        buildTimerNotification(warningText = warnText)
                    )
                }

                tickCount++
                delay(1000)
            }
        }
    }

    /**
     * EXPIRY SEQUENCE:
     * 1. Playback stop (volume fade)
     * 2. Bluetooth disconnect
     * 3. Screen off
     */
    @SuppressLint("MissingPermission")
    private fun onTimerExpired(fromUserEndNow: Boolean = false) {
        if (isExpiring) {
            Log.w(TAG, "Expiry sequence already in progress — ignoring duplicate trigger")
            return
        }
        isExpiring = true
        Log.i(TAG, "Timer expired — executing expiry sequence (fromUserEndNow=$fromUserEndNow)")
        stopWarningVolumeListener()
        shakeDetector?.stopListening()
        cancelExpiryAlarm()
        tickJob?.cancel()

        scope.launch {
            try {
                cachedSettings = app.prefs.settings.first()
                val settings = cachedSettings

                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.cancel(AppNotifications.NOTIF_TIMER)

                // ── Safety Guard: Active Call Protection (Telephony & VoIP e.g. WhatsApp/Meet) ─
                val am = getSystemService(android.media.AudioManager::class.java)
                val isCallActive = am != null && (
                    am.mode == android.media.AudioManager.MODE_IN_CALL ||
                    am.mode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                    am.mode == android.media.AudioManager.MODE_RINGTONE
                )
                if (isCallActive && !fromUserEndNow) {
                    Log.i(TAG, "Active phone call, VoIP call, or ringtone in progress (mode=${am?.mode}) — auto-extending timer by ${settings.extendMinutes}m to prevent call disconnect")
                    isExpiring = false
                    performExtend()
                    return@launch
                }

                // Immediately notify UI that timer countdown has ended, returning to dial view
                app.updateTimerState(ActiveTimerState(isRunning = false, remainingMs = 0L))

                // ── STEP 1: Playback Stop ──────────────────────────────────
                val audioStopped = if (settings.playbackStopEnabled) {
                    if (fromUserEndNow) {
                        Log.i(TAG, "User requested End Now: Pausing playback immediately")
                        app.playbackController.stealAudioFocusAndPause()
                        true
                    } else {
                        Log.i(TAG, "Step 1: Fading out playback over ${settings.fadeOutDurationSeconds}s")
                        val fadeResult = app.playbackController.fadeOutAndStop(settings.fadeOutDurationSeconds)

                        if (fadeResult == FadeResult.CANCELLED_BY_USER) {
                            Log.i(TAG, "Fade cancelled by volume key — extending timer, skipping disconnect")
                            isExpiring = false
                            performExtend()
                            return@launch
                        }
                        fadeResult == FadeResult.COMPLETED
                    }
                } else {
                    true
                }

                // ── STEP 2: Bluetooth Disconnect ───────────────────────────
                Log.i(TAG, "Step 2: Bluetooth disconnect")
                val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter

                val btDevices = mutableListOf<BluetoothDevice>()
                // 1. Bonded devices matching targetAddresses
                for (addr in targetAddresses) {
                    val dev = adapter?.bondedDevices?.find { it.address.equals(addr, ignoreCase = true) }
                        ?: runCatching { adapter?.getRemoteDevice(addr) }.getOrNull()
                    if (dev != null && !btDevices.any { it.address.equals(dev.address, ignoreCase = true) }) {
                        btDevices.add(dev)
                    }
                }
                // 2. Currently connected devices in btMonitor (fallback ONLY if no target devices resolved, preventing smartwatch disconnect)
                if (btDevices.isEmpty()) {
                    app.btMonitor.connectedDevices.value.mapNotNull { it.device }.forEach { dev ->
                        if (!btDevices.any { it.address.equals(dev.address, ignoreCase = true) }) {
                            btDevices.add(dev)
                        }
                    }
                }
                // 3. Fallback to adapter connected audio devices
                if (btDevices.isEmpty() && adapter != null && adapter.isEnabled) {
                    btDevices.addAll(app.disconnector.getConnectedAudioDevices())
                }

                val disconnectResult = withTimeoutOrNull(15_000L) {
                    app.disconnector.disconnectDevices(
                        devices = btDevices,
                        cooldownSeconds = if (settings.reconnectBlockerEnabled) settings.cooldownSeconds else 0,
                        enableCooldown = settings.reconnectBlockerEnabled
                    )
                }

                val disconnectSuccess = if (targetAddresses.contains(com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.WIRED_ADDRESS) && btDevices.isEmpty()) {
                    true
                } else {
                    disconnectResult?.success ?: false
                }

                // Restore volume to initial level now that Bluetooth disconnect is done
                if (settings.playbackStopEnabled && !fromUserEndNow) {
                    delay(300L) // Allow AudioService time to complete A2DP/sink unbinding
                    Log.i(TAG, "Restoring media volume to pre-fade level after disconnect")
                    app.playbackController.restoreVolume()
                }

                // ── STEP 3: Screen Off ─────────────────────────────────────
                if (settings.screenOffEnabled) {
                    Log.i(TAG, "Step 3: Screen off (lockNow)")
                    app.screenController.lockScreen()
                }

                // ── Session & Usage Bookkeeping ─────────────────────────────
                val now = System.currentTimeMillis()
                val startTime = if (sessionStartTime > 0L) sessionStartTime else (endTimeMillis - (plannedMinutes + extendedMinutes) * 60_000L)
                val totalActiveMs = TimerCalculations.calculateActiveDurationMs(startTime, now, totalPausedMs)
                val actualMin = TimerCalculations.calculateDurationMinutes(totalActiveMs).coerceAtLeast(plannedMinutes + extendedMinutes)

                if (sessionId == 0L) {
                    sessionId = settings.activeSessionId
                }

                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                app.db.sessionDao().upsert(
                    SessionEntity(
                        id = if (sessionId > 0L) sessionId else 0L,
                        deviceAddress = targetAddresses.firstOrNull() ?: "unknown",
                        deviceName = getDeviceName(targetAddresses.firstOrNull()),
                        startTime = startTime,
                        endTime = now,
                        plannedDurationMin = plannedMinutes,
                        actualDurationMin = actualMin,
                        audioStopped = audioStopped,
                        disconnectConfirmed = disconnectSuccess,
                        extendedMinutes = extendedMinutes,
                        date = today
                    )
                )
                app.db.sessionDao().pruneOldSessions(500)

                for (addr in targetAddresses) {
                    if (actualMin > 0) {
                        app.db.dailyUsageDao().recordUsage(today, addr, actualMin)
                    }
                    val stillConn = app.btMonitor.connectedDevices.value.any { it.address == addr }
                    if (stillConn && !disconnectSuccess) {
                        com.smartbluetoothsleeptracker.receiver.BluetoothReceiver.setActiveConnectTime(app, addr, now)
                    } else {
                        com.smartbluetoothsleeptracker.receiver.BluetoothReceiver.clearActiveConnectTime(app, addr)
                    }
                }

                // ── HAPTICS & OUTCOME NOTIFICATION ──────────────────────────
                HapticManager.vibrateDisconnected(this@TimerService)

                val devName = getDeviceName(targetAddresses.firstOrNull())
                val outcomeMsg = when {
                    disconnectSuccess -> "Audio paused & $devName disconnected"
                    audioStopped -> "Audio paused ($devName stayed connected)"
                    else -> "Sleep timer finished"
                }

                if (settings.sleepAlertsEnabled) {
                    val notifMgr = getSystemService(android.app.NotificationManager::class.java)
                    notifMgr.notify(
                        AppNotifications.NOTIF_DISCONNECT_RESULT,
                        AppNotifications.disconnectResultNotification(this@TimerService, disconnectSuccess, devName).build()
                    )
                }

                // Show toast for immediate feedback
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, outcomeMsg, Toast.LENGTH_LONG).show()
                }

                // Start cooldown ticking if active
                if (settings.reconnectBlockerEnabled && settings.cooldownSeconds > 0) {
                    startCooldownTick()
                }

                app.prefs.clearTimer()
            } finally {
                isExpiring = false
                cancelExpiryAlarm()
                releaseWakeLock()
                app.playbackController.abandonAudioFocus()
                ServiceCompat.stopForeground(this@TimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                app.updateTimerState(ActiveTimerState())
                if (!app.disconnector.isCooldownActive) {
                    isRunning = false
                    stopSelf()
                }
            }
        }
    }

    private fun cancelTimer() {
        Log.i(TAG, "Cancelling timer — stopping countdown without disconnecting devices")
        isRunning = false
        isExpiring = false
        cancelExpiryAlarm()
        tickJob?.cancel()
        autoResumeJob?.cancel()
        cooldownTickJob?.cancel()

        scope.launch {
            if (sessionId > 0L) {
                val now = System.currentTimeMillis()
                val startTime = if (sessionStartTime > 0L) sessionStartTime else (endTimeMillis - (plannedMinutes + extendedMinutes) * 60_000L)
                val totalActiveMs = TimerCalculations.calculateActiveDurationMs(startTime, now, totalPausedMs)
                val actualMin = TimerCalculations.calculateDurationMinutes(totalActiveMs)
                if (actualMin <= 0) {
                    app.db.sessionDao().deleteById(sessionId)
                } else {
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    app.db.sessionDao().upsert(
                        SessionEntity(
                            id = sessionId,
                            deviceAddress = targetAddresses.firstOrNull() ?: "unknown",
                            deviceName = getDeviceName(targetAddresses.firstOrNull()),
                            startTime = startTime,
                            endTime = now,
                            plannedDurationMin = plannedMinutes,
                            actualDurationMin = actualMin,
                            audioStopped = false,
                            disconnectConfirmed = false,
                            extendedMinutes = extendedMinutes,
                            date = today
                        )
                    )
                    for (addr in targetAddresses) {
                        app.db.dailyUsageDao().recordUsage(today, addr, actualMin)
                    }
                }
                for (addr in targetAddresses) {
                    val stillConn = app.btMonitor.connectedDevices.value.any { it.address == addr }
                    if (stillConn) {
                        com.smartbluetoothsleeptracker.receiver.BluetoothReceiver.setActiveConnectTime(app, addr, now)
                    } else {
                        com.smartbluetoothsleeptracker.receiver.BluetoothReceiver.clearActiveConnectTime(app, addr)
                    }
                }
            }
            app.prefs.clearTimer()
        }

        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(AppNotifications.NOTIF_TIMER)
        nm.cancel(AppNotifications.NOTIF_WARNING)
        shakeDetector?.stopListening()
        stopWarningVolumeListener()

        // Restore volume and abandon audio focus if timer was fading or cancelled
        app.playbackController.restoreVolume()
        app.playbackController.abandonAudioFocus()

        HapticManager.vibrateClick(this)

        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        app.updateTimerState(ActiveTimerState())
        stopSelf()
    }

    private fun finishSessionOnManualDisconnect() {
        Log.i(TAG, "Device manually disconnected by user — completing session cleanly")
        isRunning = false
        isExpiring = false
        cancelExpiryAlarm()
        tickJob?.cancel()
        autoResumeJob?.cancel()
        cooldownTickJob?.cancel()
        shakeDetector?.stopListening()
        stopWarningVolumeListener()

        // Ensure playback pauses so media doesn't leak out of phone speaker
        app.playbackController.stealAudioFocusAndPause()

        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(AppNotifications.NOTIF_TIMER)
        nm.cancel(AppNotifications.NOTIF_WARNING)

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "Earbuds disconnected • Sleep timer finished", Toast.LENGTH_SHORT).show()
        }

        scope.launch {
            if (sessionId > 0L) {
                val now = System.currentTimeMillis()
                val startTime = if (sessionStartTime > 0L) sessionStartTime else (endTimeMillis - (plannedMinutes + extendedMinutes) * 60_000L)
                val totalActiveMs = TimerCalculations.calculateActiveDurationMs(startTime, now, totalPausedMs)
                val actualMin = TimerCalculations.calculateDurationMinutes(totalActiveMs)
                if (actualMin <= 0) {
                    app.db.sessionDao().deleteById(sessionId)
                } else {
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    app.db.sessionDao().upsert(
                        SessionEntity(
                            id = sessionId,
                            deviceAddress = targetAddresses.firstOrNull() ?: "unknown",
                            deviceName = getDeviceName(targetAddresses.firstOrNull()),
                            startTime = startTime,
                            endTime = now,
                            plannedDurationMin = plannedMinutes,
                            actualDurationMin = actualMin,
                            audioStopped = true,
                            disconnectConfirmed = true,
                            extendedMinutes = extendedMinutes,
                            date = today
                        )
                    )
                    for (addr in targetAddresses) {
                        app.db.dailyUsageDao().recordUsage(today, addr, actualMin)
                    }
                }
                for (addr in targetAddresses) {
                    com.smartbluetoothsleeptracker.receiver.BluetoothReceiver.clearActiveConnectTime(app, addr)
                }
            }
            app.prefs.clearTimer()
            releaseWakeLock()
            app.playbackController.abandonAudioFocus()
            ServiceCompat.stopForeground(this@TimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            app.updateTimerState(ActiveTimerState(isRunning = false, remainingMs = 0L))
            stopSelf()
        }
    }

    private fun extendTimer() {
        scope.launch {
            performExtend()
            HapticManager.vibrateExtend(this@TimerService)
        }
    }

    private suspend fun performExtend() {
        shakeDetector?.stopListening()
        stopWarningVolumeListener()

        if (!TimerCalculations.canAutoExtend(extendedMinutes)) {
            Log.w(TAG, "Maximum auto-extend reached (${extendedMinutes}m) — extend request ignored")
            return
        }

        val settings = cachedSettings
        val addMs = settings.extendMinutes * 60_000L
        endTimeMillis += addMs
        targetElapsedRealtime += addMs
        extendedMinutes += settings.extendMinutes
        warningFired = false

        app.prefs.setTimerEnd(endTimeMillis)
        app.prefs.setTimerExtended(extendedMinutes)

        val remainingMs = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val remainingMin = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        acquireWakeLock(remainingMin)
        scheduleExpiryAlarm(endTimeMillis)

        app.updateTimerState(
            ActiveTimerState(
                isRunning = true,
                isPaused = isPaused,
                remainingMs = remainingMs,
                endTimeMillis = endTimeMillis,
                plannedMinutes = plannedMinutes,
                extendedMinutes = extendedMinutes,
                targetAddresses = targetAddresses
            )
        )

        if (tickJob?.isActive != true && !isPaused) {
            startTickLoop()
        }

        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(AppNotifications.NOTIF_WARNING)

        Log.i(TAG, "Extended by ${settings.extendMinutes}m (total extended: ${extendedMinutes}m)")
    }

    private fun startWarningVolumeListener() {
        if (warningVolumeReceiver != null) return
        val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val initialVol = am?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: -1
        warningVolumeReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == android.media.AudioManager.STREAM_MUSIC) {
                        val newVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                        if (newVol != -1 && newVol != initialVol) {
                            Log.i(TAG, "Volume key pressed during warning period ($initialVol -> $newVol) — extending timer")
                            stopWarningVolumeListener()
                            extendTimer()
                        }
                    }
                }
            }
        }
        val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            warningVolumeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun stopWarningVolumeListener() {
        warningVolumeReceiver?.let {
            runCatching { unregisterReceiver(it) }
            warningVolumeReceiver = null
        }
    }

    private fun endNow() {
        Log.i(TAG, "Ending timer now — triggering disconnect sequence immediately")
        tickJob?.cancel()
        autoResumeJob?.cancel()
        onTimerExpired(fromUserEndNow = true)
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
            val devName = getDeviceName(targetAddresses.firstOrNull())
            while (app.disconnector.isCooldownActive) {
                val remaining = (app.disconnector.cooldownState.value.expiresAt - System.currentTimeMillis()) / 1000
                if (remaining <= 0) break
                nm.notify(
                    AppNotifications.NOTIF_COOLDOWN,
                    AppNotifications.cooldownNotification(this@TimerService, remaining.toInt(), devName).build()
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
        if (address == null) return "Connected Device"
        if (address == com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.WIRED_ADDRESS) {
            return com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.WIRED_NAME
        }
        val liveName = app.btMonitor.connectedDevices.value.find { it.address == address }?.name
        if (!liveName.isNullOrBlank() && liveName != address) return liveName

        val dbName = runCatching {
            runBlocking { app.db.deviceDao().getDevice(address)?.name }
        }.getOrNull()
        if (!dbName.isNullOrBlank() && dbName != address) return dbName

        return runCatching {
            val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
            adapter?.bondedDevices?.find { it.address == address }?.name?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "Connected Device"
    }

    private fun getDeviceBattery(address: String?): Int? {
        val dev = app.btMonitor.connectedDevices.value.find { it.address == address }
            ?: app.btMonitor.connectedDevices.value.firstOrNull()
        val b = dev?.battery
        return when {
            b != null && b.mainLevel in 0..100 -> b.mainLevel
            b != null && b.leftLevel in 0..100 -> b.leftLevel
            b != null && b.rightLevel in 0..100 -> b.rightLevel
            else -> null
        }
    }

    private fun formatRemaining(ms: Long = if (isPaused) (pausedRemaining ?: 0L) else (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.ROOT, "%d:%02d", m, s)
    }

    private fun buildTimerNotification(warningText: String? = null): android.app.Notification {
        val targetAddr = targetAddresses.firstOrNull()
        val devName = getDeviceName(targetAddr)
        val batteryPct = getDeviceBattery(targetAddr)
        val remainingMs = if (isPaused) {
            pausedRemaining ?: (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        } else {
            (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        return AppNotifications.timerNotification(
            ctx = this,
            remainingText = formatRemaining(remainingMs),
            warningText = warningText,
            isPaused = isPaused,
            endTimeMillis = if (isPaused) 0L else endTimeMillis,
            deviceName = devName,
            batteryPercent = batteryPct,
            extendMinutes = cachedSettings.extendMinutes
        ).build()
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    private fun acquireWakeLock(minutes: Int) {
        releaseWakeLock()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sleepbt:timer").apply {
            acquire(minutes * 60_000L + 60_000L) // timer + 1 min buffer
        }
    }

    @SuppressLint("ScheduleExactAlarm", "MissingPermission")
    private fun scheduleExpiryAlarm(timeMillis: Long) {
        if (timeMillis <= System.currentTimeMillis()) return
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, TimerService::class.java).apply {
                action = ACTION_ALARM_BACKSTOP
            }
            val pi = PendingIntent.getForegroundService(
                this, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val showIntent = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(timeMillis, showIntent), pi)
            Log.d(TAG, "Expiry alarm scheduled for $timeMillis")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule expiry alarm: ${e.message}")
        }
    }

    private fun cancelExpiryAlarm() {
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, TimerService::class.java).apply {
                action = ACTION_ALARM_BACKSTOP
            }
            val pi = PendingIntent.getForegroundService(
                this, 1001, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel expiry alarm: ${e.message}")
        }
    }

    override fun onDestroy() {
        isRunning = false
        isExpiring = false
        cancelExpiryAlarm()
        tickJob?.cancel()
        settingsJob?.cancel()
        cooldownTickJob?.cancel()
        autoResumeJob?.cancel()
        shakeDetector?.stopListening()
        shakeDetector = null
        stopWarningVolumeListener()
        app.disconnector.endCooldown()
        releaseWakeLock()
        app.updateTimerState(ActiveTimerState())
        scope.cancel()
        super.onDestroy()
    }
}
