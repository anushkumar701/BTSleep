package com.smartbluetoothsleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.BTCurfewApp
import com.smartbluetoothsleeptracker.data.prefs.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val shizukuAvailable: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BTCurfewApp
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            app.prefs.settings.collect { s ->
                _state.update { it.copy(settings = s) }
            }
        }
        checkShizuku()
    }

    private fun checkShizuku() {
        // Check if Shizuku is installed and running
        val available = try {
            rikka.shizuku.Shizuku.pingBinder()
        } catch (_: Exception) { false }
        _state.update { it.copy(shizukuAvailable = available) }
    }

    fun setExtendMinutes(m: Int)          = viewModelScope.launch { app.prefs.setExtendMinutes(m) }
    fun setReconnectBlocker(on: Boolean)  = viewModelScope.launch { app.prefs.setReconnectBlocker(on) }
    fun setCooldownSeconds(s: Int)        = viewModelScope.launch { app.prefs.setCooldownSeconds(s) }
    fun setShizukuEnabled(on: Boolean)    = viewModelScope.launch { app.prefs.setShizukuEnabled(on) }
    fun setSleepAlerts(on: Boolean)       = viewModelScope.launch { app.prefs.setSleepAlerts(on) }
    fun setWarningLeadMinutes(m: Int)     = viewModelScope.launch { app.prefs.setWarningLeadMinutes(m) }
    fun setForegroundService(on: Boolean) = viewModelScope.launch { app.prefs.setForegroundService(on) }
    fun setThemeMode(mode: String)        = viewModelScope.launch { app.prefs.setThemeMode(mode) }
}
