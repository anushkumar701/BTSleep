package com.smartbluetoothsleeptracker.core.timer

import com.smartbluetoothsleeptracker.core.analytics.FirebaseAnalyticsHelper
import com.smartbluetoothsleeptracker.data.prefs.AppPrefs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TimerState(
    val startWallClock: Long? = null,     // System.currentTimeMillis() when timer started
    val endWallClock: Long? = null,       // System.currentTimeMillis() when timer ends
    val pausedRemaining: Long? = null,    // millis remaining when paused
    val remainingMillis: Long = 0L        // live countdown (updated every second)
) {
    val isRunning: Boolean get() = endWallClock != null && pausedRemaining == null && remainingMillis > 0L
    val isPaused: Boolean  get() = pausedRemaining != null
    // isActive is false once timer has fully expired (remainingMillis == 0 and no pause)
    val isActive: Boolean  get() = isPaused || (endWallClock != null && remainingMillis > 0L)

    /** Total wall-clock duration in millis since the timer was started */
    val totalWallClockMillis: Long
        get() = if (startWallClock != null && endWallClock != null)
            (endWallClock - startWallClock).coerceAtLeast(0L) else 0L
}

class SleepTimerManager(
    private val prefs: AppPrefs,
    private val analytics: FirebaseAnalyticsHelper? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    // Remaining millis as a one-time StateFlow (not re-created on every access)
    val remainingMillis: StateFlow<Long> = state.map { it.remainingMillis }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    init {
        // Restore persisted timer on app startup
        scope.launch {
            val settings = prefs.settings.first()
            val endWall = settings.timerEndWallClock
            val paused = settings.timerPausedRemaining
            when {
                paused != null -> {
                    _state.value = TimerState(pausedRemaining = paused, remainingMillis = paused)
                }
                endWall != null -> {
                    val remaining = endWall - System.currentTimeMillis()
                    if (remaining > 0L) {
                        _state.value = TimerState(endWallClock = endWall, remainingMillis = remaining)
                        startTicking()
                    } else {
                        // Timer already expired while app was closed — clear it
                        prefs.clearTimer()
                    }
                }
            }
        }
    }

    fun startTimer(minutes: Long) {
        analytics?.trackTimerStarted(minutes)
        scope.launch {
            val now = System.currentTimeMillis()
            val endWall = now + minutes * 60_000L
            prefs.setTimerEnd(endWall)
            prefs.setTimerPaused(null)
            _state.value = TimerState(
                startWallClock = now,
                endWallClock = endWall,
                remainingMillis = minutes * 60_000L
            )
            startTicking()
        }
    }

    fun pauseTimer() {
        scope.launch {
            val remaining = (_state.value.endWallClock?.minus(System.currentTimeMillis()))
                ?.coerceAtLeast(0L) ?: return@launch
            tickJob?.cancel()
            prefs.setTimerEnd(null)
            prefs.setTimerPaused(remaining)
            _state.value = TimerState(
                startWallClock = _state.value.startWallClock,
                pausedRemaining = remaining,
                remainingMillis = remaining
            )
        }
    }

    fun resumeTimer() {
        scope.launch {
            val remaining = _state.value.pausedRemaining ?: return@launch
            val endWall = System.currentTimeMillis() + remaining
            prefs.setTimerEnd(endWall)
            prefs.setTimerPaused(null)
            _state.value = TimerState(
                startWallClock = _state.value.startWallClock,
                endWallClock = endWall,
                remainingMillis = remaining
            )
            startTicking()
        }
    }

    fun extendTimer(extraMinutes: Long) {
        analytics?.trackTimerExtended(extraMinutes)
        scope.launch {
            val current = _state.value
            if (current.isRunning) {
                val newEnd = (current.endWallClock ?: System.currentTimeMillis()) + extraMinutes * 60_000L
                prefs.setTimerEnd(newEnd)
                val newRemaining = newEnd - System.currentTimeMillis()
                _state.value = current.copy(endWallClock = newEnd, remainingMillis = newRemaining.coerceAtLeast(0L))
            }
        }
    }

    fun clearTimer() {
        val remaining = _state.value.remainingMillis
        if (_state.value.isRunning || _state.value.isPaused) {
            analytics?.trackTimerCancelled(remaining / 60_000L)
        }
        scope.launch {
            tickJob?.cancel()
            prefs.clearTimer()
            _state.value = TimerState()
        }
    }

    fun isRunning(): Boolean = _state.value.isRunning
    fun getRemainingMillis(): Long = _state.value.remainingMillis

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                delay(1_000L)
                val endWall = _state.value.endWallClock ?: break
                val remaining = (endWall - System.currentTimeMillis()).coerceAtLeast(0L)
                _state.update { it.copy(remainingMillis = remaining) }
                if (remaining == 0L) break
            }
        }
    }
}
