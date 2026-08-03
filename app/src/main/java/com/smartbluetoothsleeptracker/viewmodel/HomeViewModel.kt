package com.smartbluetoothsleeptracker.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.SleepBTApp
import com.smartbluetoothsleeptracker.core.bluetooth.ConnectedDevice
import com.smartbluetoothsleeptracker.core.bluetooth.CooldownState
import com.smartbluetoothsleeptracker.data.prefs.AppSettings
import com.smartbluetoothsleeptracker.service.TimerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val btEnabled: Boolean = false,
    val isTimerRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isDisconnectReady: Boolean = false,
    val remainingMs: Long = 0L,
    val cooldown: CooldownState = CooldownState(),
    val selectedMinutes: Long = 30L,
    val lastUsedPreset: Long = 0L,
    // Smart suggestion
    val suggestedMinutes: Int? = null,
    val suggestionLabel: String = ""
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SleepBTApp
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.sleepbt.TICK" -> {
                    val remaining = intent.getLongExtra("remaining", 0L)
                    _state.update { it.copy(remainingMs = remaining, isTimerRunning = remaining > 0) }
                }
                "com.sleepbt.TIMER_END" -> {
                    _state.update { it.copy(isTimerRunning = false, remainingMs = 0L, isPaused = false) }
                }
            }
        }
    }

    init {
        // Register tick receiver
        val filter = IntentFilter().apply {
            addAction("com.sleepbt.TICK")
            addAction("com.sleepbt.TIMER_END")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            application,
            tickReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Observe settings
        viewModelScope.launch {
            app.prefs.settings.collect { settings ->
                val paused = settings.timerPausedRemaining != null
                _state.update { it.copy(
                    settings = settings,
                    selectedMinutes = settings.selectedMinutes,
                    lastUsedPreset = settings.lastUsedPreset,
                    isTimerRunning = (settings.timerEndWallClock != null && settings.timerEndWallClock > System.currentTimeMillis()) || paused,
                    isPaused = paused,
                    remainingMs = settings.timerPausedRemaining ?: (settings.timerEndWallClock?.minus(System.currentTimeMillis())?.coerceAtLeast(0L) ?: 0L)
                ) }
            }
        }

        // Observe connected devices & compute disconnect readiness
        viewModelScope.launch {
            app.btMonitor.connectedDevices.collect { devices ->
                val addresses = devices.map { it.address }
                var ready = false
                if (addresses.isNotEmpty()) {
                    val dbDevs = app.db.deviceDao().getAllNow().associateBy { it.address }
                    ready = addresses.any { addr ->
                        dbDevs[addr]?.workingDisconnectMethod != null
                    }
                }
                _state.update { it.copy(connectedDevices = devices, isDisconnectReady = ready) }
            }
        }

        // Observe BT state
        viewModelScope.launch {
            app.btMonitor.btEnabled.collect { enabled ->
                _state.update { it.copy(btEnabled = enabled) }
            }
        }

        // Observe cooldown
        viewModelScope.launch {
            app.disconnector.cooldownState.collect { cd ->
                _state.update { it.copy(cooldown = cd) }
            }
        }

        // Compute smart duration suggestion
        viewModelScope.launch {
            computeSmartSuggestion()
        }
    }

    private suspend fun computeSmartSuggestion() {
        try {
            val sessions = app.db.sessionDao().recentSessionsNow()
            if (sessions.size < 5) {
                _state.update { it.copy(suggestedMinutes = null) }
                return
            }

            val now = LocalDateTime.now()
            val currentHour = now.hour

            // Filter sessions within ±1 hour of current time-of-day
            val relevantSessions = sessions.filter { session ->
                val sessionHour = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(session.startTime),
                    ZoneId.systemDefault()
                ).hour
                val diff = kotlin.math.abs(sessionHour - currentHour)
                diff <= 1 || diff >= 23 // handle midnight wrap
            }

            if (relevantSessions.size < 3) {
                _state.update { it.copy(suggestedMinutes = null) }
                return
            }

            // Find mode (most common duration)
            val mode = relevantSessions
                .groupBy { it.plannedDurationMin }
                .maxByOrNull { it.value.size }
                ?.key

            if (mode != null && mode > 0) {
                val timeLabel = when {
                    currentHour < 6 -> "late night"
                    currentHour < 12 -> "morning"
                    currentHour < 17 -> "afternoon"
                    currentHour < 21 -> "evening"
                    else -> "night"
                }
                _state.update { it.copy(
                    suggestedMinutes = mode,
                    suggestionLabel = "Try ${formatSuggestionMinutes(mode)} — your usual pick around $timeLabel"
                ) }
            }
        } catch (_: Exception) {
            _state.update { it.copy(suggestedMinutes = null) }
        }
    }

    private fun formatSuggestionMinutes(min: Int): String {
        val h = min / 60; val m = min % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun setMinutesEphemeral(m: Long) {
        _state.update { it.copy(selectedMinutes = m.coerceIn(1, 480)) }
    }

    fun saveMinutes(m: Long) {
        viewModelScope.launch { app.prefs.setSelectedMinutes(m) }
    }

    fun saveLastUsedPreset(preset: Long) {
        viewModelScope.launch { app.prefs.setLastUsedPreset(preset) }
    }

    fun applySuggestion(minutes: Int) {
        val m = minutes.toLong()
        _state.update { it.copy(selectedMinutes = m) }
        viewModelScope.launch { app.prefs.setSelectedMinutes(m) }
    }

    fun startTimer() {
        val s = _state.value
        val targets = s.connectedDevices
            .filter { it.isFavorite }
            .ifEmpty { s.connectedDevices }
            .map { it.address }
            .joinToString(",")

        if (targets.isBlank()) return

        val intent = TimerService.startIntent(
            getApplication(),
            s.selectedMinutes.toInt(),
            targets
        )
        androidx.core.content.ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun pauseTimer() {
        val intent = Intent(getApplication<Application>(), TimerService::class.java).apply {
            action = TimerService.ACTION_PAUSE
        }
        getApplication<Application>().startService(intent)
    }

    fun resumeTimer() {
        val intent = Intent(getApplication<Application>(), TimerService::class.java).apply {
            action = TimerService.ACTION_RESUME
        }
        getApplication<Application>().startService(intent)
    }

    fun cancelTimer() {
        val intent = Intent(getApplication<Application>(), TimerService::class.java).apply {
            action = TimerService.ACTION_CANCEL
        }
        getApplication<Application>().startService(intent)
    }

    fun extendTimer() {
        val intent = Intent(getApplication<Application>(), TimerService::class.java).apply {
            action = TimerService.ACTION_EXTEND
        }
        getApplication<Application>().startService(intent)
    }

    fun endNow() {
        val intent = Intent(getApplication<Application>(), TimerService::class.java).apply {
            action = TimerService.ACTION_END_NOW
        }
        getApplication<Application>().startService(intent)
    }

    fun allowReconnect() {
        val intent = Intent(getApplication<Application>(), TimerService::class.java).apply {
            action = TimerService.ACTION_ALLOW_RECONNECT
        }
        getApplication<Application>().startService(intent)
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(tickReceiver) }
        super.onCleared()
    }
}
