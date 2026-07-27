package com.smartbluetoothsleeptracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.core.bluetooth.BluetoothDisconnector
import com.smartbluetoothsleeptracker.core.bluetooth.BluetoothMonitor
import com.smartbluetoothsleeptracker.core.timer.SleepTimerManager
import com.smartbluetoothsleeptracker.data.prefs.AppPrefs
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val deviceName: String? = null,
    val isConnected: Boolean = false,
    val bluetoothEnabled: Boolean = true,
    val timerRunning: Boolean = false,
    val timerPaused: Boolean = false,
    val remainingMillis: Long = 0L,
    val totalTimerMillis: Long = 0L,
    val selectedMinutes: Long = 30L,
    val blockerActive: Boolean = false,
    val countdownText: String = "00:00"
)

class HomeViewModel(
    private val timerManager: SleepTimerManager,
    private val btMonitor: BluetoothMonitor,
    private val btDisconnector: BluetoothDisconnector,
    private val prefs: AppPrefs
) : ViewModel() {

    private val _selectedMinutes = MutableStateFlow(30L)

    val state: StateFlow<HomeUiState> = combine(
        timerManager.state,
        btMonitor.devices,
        btMonitor.isEnabled,
        btDisconnector.blockerState,
        _selectedMinutes
    ) { timerState, devices, btEnabled, blockerState, selMin ->
        val deviceName = devices.firstOrNull()?.name
        val blockerActive = blockerState.active && System.currentTimeMillis() < blockerState.blockedUntil
        // Use persisted total when timer is active so arc % stays correct after extend/resume
        val totalMillis = when {
            timerState.isRunning && timerState.endWallClock != null ->
                (timerState.endWallClock - System.currentTimeMillis() + timerState.remainingMillis)
                    .coerceAtLeast(timerState.remainingMillis)
            timerState.isPaused && timerState.pausedRemaining != null ->
                timerState.pausedRemaining
            else -> selMin * 60_000L
        }
        HomeUiState(
            deviceName = deviceName,
            isConnected = devices.isNotEmpty(),
            bluetoothEnabled = btEnabled,
            timerRunning = timerState.isRunning,
            timerPaused = timerState.isPaused,
            remainingMillis = timerState.remainingMillis,
            totalTimerMillis = totalMillis.coerceAtLeast(1L),
            selectedMinutes = selMin,
            blockerActive = blockerActive,
            countdownText = formatCountdown(timerState.remainingMillis)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            val s = prefs.settings.first()
            _selectedMinutes.value = s.selectedMinutes
        }
    }

    fun adjustMinutes(delta: Long) {
        val current = _selectedMinutes.value
        val new = (current + delta).coerceIn(5L, 120L)
        _selectedMinutes.value = new
        viewModelScope.launch { prefs.setSelectedMinutes(new) }
    }

    fun setMinutes(minutes: Long) {
        _selectedMinutes.value = minutes.coerceIn(5L, 120L)
        viewModelScope.launch { prefs.setSelectedMinutes(minutes) }
    }

    fun cancelBlocker() {
        btDisconnector.clearBlocker()
    }

    private fun formatCountdown(millis: Long): String {
        val totalSec = (millis / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
