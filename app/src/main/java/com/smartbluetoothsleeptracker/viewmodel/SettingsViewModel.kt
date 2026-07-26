package com.smartbluetoothsleeptracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.data.prefs.AppPrefs
import com.smartbluetoothsleeptracker.data.prefs.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val prefs: AppPrefs) : ViewModel() {

    val settings: StateFlow<AppSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setExtendMinutes(minutes: Int)    = viewModelScope.launch { prefs.setExtendMinutes(minutes) }
    fun setBatterySaver(enabled: Boolean) = viewModelScope.launch { prefs.setBatterySaver(enabled) }
    fun setIdleMinutes(minutes: Int)      = viewModelScope.launch { prefs.setIdleMinutes(minutes) }
    fun setNotifications(enabled: Boolean)= viewModelScope.launch { prefs.setNotifications(enabled) }
    fun setThemeMode(mode: String)        = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setForegroundService(enabled: Boolean) = viewModelScope.launch { prefs.setForegroundService(enabled) }
    fun setReconnectBlocker(enabled: Boolean)  = viewModelScope.launch { prefs.setReconnectBlocker(enabled) }
    fun completeOnboarding()              = viewModelScope.launch {
        prefs.setOnboardingComplete(true)
        prefs.setPrivacyAgreed(true)
    }
}
