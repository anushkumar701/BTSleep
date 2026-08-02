package com.smartbluetoothsleeptracker.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.BTCurfewApp
import com.smartbluetoothsleeptracker.core.bluetooth.ConnectedDevice
import com.smartbluetoothsleeptracker.core.bluetooth.CooldownState
import com.smartbluetoothsleeptracker.data.prefs.AppSettings
import com.smartbluetoothsleeptracker.service.TimerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val btEnabled: Boolean = false,
    val isTimerRunning: Boolean = false,
    val remainingMs: Long = 0L,
    val cooldown: CooldownState = CooldownState(),
    val selectedMinutes: Long = 30L
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BTCurfewApp
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.btcurfew.TICK" -> {
                    val remaining = intent.getLongExtra("remaining", 0L)
                    _state.update { it.copy(remainingMs = remaining, isTimerRunning = remaining > 0) }
                }
                "com.btcurfew.TIMER_END" -> {
                    _state.update { it.copy(isTimerRunning = false, remainingMs = 0L) }
                }
            }
        }
    }

    init {
        // Register tick receiver
        val filter = IntentFilter().apply {
            addAction("com.btcurfew.TICK")
            addAction("com.btcurfew.TIMER_END")
        }
        application.registerReceiver(tickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Observe settings
        viewModelScope.launch {
            app.prefs.settings.collect { settings ->
                _state.update { it.copy(
                    settings = settings,
                    selectedMinutes = settings.selectedMinutes,
                    isTimerRunning = settings.timerEndWallClock != null && settings.timerEndWallClock > System.currentTimeMillis()
                ) }
            }
        }

        // Observe connected devices
        viewModelScope.launch {
            app.btMonitor.connectedDevices.collect { devices ->
                _state.update { it.copy(connectedDevices = devices) }
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
    }

    fun setMinutes(m: Long) {
        _state.update { it.copy(selectedMinutes = m.coerceIn(1, 480)) }
        viewModelScope.launch { app.prefs.setSelectedMinutes(m) }
    }

    fun startTimer() {
        val s = _state.value
        val targets = s.connectedDevices
            .filter { it.isFavorite }
            .ifEmpty { s.connectedDevices } // If no favorites, target all connected
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
