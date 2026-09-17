package com.smartbluetoothsleeptracker.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.smartbluetoothsleeptracker.SleepBTApp
import com.smartbluetoothsleeptracker.core.haptics.HapticManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile allowing users to quickly start or stop the sleep timer
 * directly from the Android status bar / notification shade without opening the app.
 */
@RequiresApi(Build.VERSION_CODES.N)
class SleepBTTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as SleepBTApp

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val isRunning = TimerService.isRunning
            if (isRunning) {
                // Tapping active tile cancels the timer
                HapticManager.vibrateClick(this@SleepBTTileService)
                val cancelIntent = Intent(this@SleepBTTileService, TimerService::class.java).apply {
                    action = TimerService.ACTION_CANCEL
                }
                startService(cancelIntent)
            } else {
                // Start timer with saved preference
                HapticManager.vibrateClick(this@SleepBTTileService)
                val settings = app.prefs.settings.first()
                val minutes = settings.selectedMinutes.toInt().coerceAtLeast(15)

                // Get connected devices or bonded devices
                val connected = app.btMonitor.connectedDevices.value
                val targets = connected.filter { it.isFavorite }
                    .ifEmpty { connected }
                    .map { it.address }
                    .joinToString(",")

                val startIntent = TimerService.startIntent(this@SleepBTTileService, minutes, targets)
                ContextCompat.startForegroundService(this@SleepBTTileService, startIntent)
            }
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val running = TimerService.isRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Sleep Timer"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) "Active" else "Tap to start"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
