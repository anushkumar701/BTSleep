package com.smartbluetoothsleeptracker.viewmodel

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.app.Application
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.BTCurfewApp
import com.smartbluetoothsleeptracker.core.screen.BTCurfewDeviceAdmin
import com.smartbluetoothsleeptracker.data.prefs.AppSettings
import com.smartbluetoothsleeptracker.service.MediaListenerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PermissionStatus(
    val name: String,
    val description: String,
    val granted: Boolean,
    val settingsAction: String? = null, // intent action to deep-link
    val settingsUri: String? = null     // optional data uri
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val deviceAdminActive: Boolean = false,
    val permissions: List<PermissionStatus> = emptyList()
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
        refreshStatus()
    }

    fun refreshStatus() {
        val ctx = getApplication<Application>()

        val deviceAdminActive = app.screenController.isDeviceAdminActive()

        val permissions = buildPermissionsList(ctx)

        _state.update {
            it.copy(
                deviceAdminActive = deviceAdminActive,
                permissions = permissions
            )
        }
    }

    private fun buildPermissionsList(ctx: Context): List<PermissionStatus> {
        val list = mutableListOf<PermissionStatus>()

        // 1. Notifications
        list.add(PermissionStatus(
            name = "Notifications",
            description = "Timer alerts, warnings, and disconnect results",
            granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            settingsAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            settingsUri = null
        ))

        // 2. Bluetooth
        list.add(PermissionStatus(
            name = "Bluetooth",
            description = "Connect to and disconnect audio devices",
            granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED,
            settingsAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            settingsUri = "package:${ctx.packageName}"
        ))

        // 3. Battery Optimization
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        list.add(PermissionStatus(
            name = "Battery Optimization",
            description = "Keeps timer running in background",
            granted = pm.isIgnoringBatteryOptimizations(ctx.packageName),
            settingsAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            settingsUri = "package:${ctx.packageName}"
        ))

        // 4. Notification Listener
        val enabledListeners = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        val componentName = ComponentName(ctx, MediaListenerService::class.java).flattenToString()
        list.add(PermissionStatus(
            name = "Notification Listener",
            description = "Pause active media sessions on timer end",
            granted = enabledListeners.contains(componentName),
            settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        ))

        // 5. Device Admin
        list.add(PermissionStatus(
            name = "Device Admin",
            description = "Lock screen on timer expiry",
            granted = app.screenController.isDeviceAdminActive(),
            settingsAction = Settings.ACTION_SECURITY_SETTINGS
        ))

        return list
    }

    // Timer
    fun setExtendMinutes(m: Int)          = viewModelScope.launch { app.prefs.setExtendMinutes(m) }

    // Bluetooth
    fun setReconnectBlocker(on: Boolean)  = viewModelScope.launch { app.prefs.setReconnectBlocker(on) }
    fun setCooldownSeconds(s: Int)        = viewModelScope.launch { app.prefs.setCooldownSeconds(s) }

    // Playback
    fun setPlaybackStop(on: Boolean)      = viewModelScope.launch { app.prefs.setPlaybackStop(on) }
    fun setFadeOutDuration(s: Int)        = viewModelScope.launch { app.prefs.setFadeOutDuration(s) }

    // Screen Off
    fun setScreenOff(on: Boolean)         = viewModelScope.launch { app.prefs.setScreenOff(on) }


    // Haptic
    fun setHapticFeedback(on: Boolean)    = viewModelScope.launch { app.prefs.setHapticFeedback(on) }

    // Notifications
    fun setSleepAlerts(on: Boolean)       = viewModelScope.launch { app.prefs.setSleepAlerts(on) }
    fun setWarningLeadMinutes(m: Int)     = viewModelScope.launch { app.prefs.setWarningLeadMinutes(m) }

    // Service
    fun setForegroundService(on: Boolean) = viewModelScope.launch { app.prefs.setForegroundService(on) }

    // Appearance
    fun setThemeMode(mode: String)        = viewModelScope.launch { app.prefs.setThemeMode(mode) }
}
