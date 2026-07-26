package com.smartbluetoothsleeptracker.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smartbluetoothsleeptracker.SleepBTApp
import com.smartbluetoothsleeptracker.data.db.SessionEntity
import com.smartbluetoothsleeptracker.service.SleepTimerService
import com.smartbluetoothsleeptracker.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.LocalDate

class BluetoothReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SleepBTApp

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                SleepTimerService.startIfAllowed(context)
                WidgetUpdater.update(context)
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                app.btMonitor.onAdapterStateChanged(state)
                WidgetUpdater.update(context)
            }

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = getDevice(intent) ?: return

                // ── CRITICAL BT-DISCONNECT FIX ──────────────────────────────────
                // When the blocker is active, the device auto-reconnected after we
                // disconnected it. We must force-disconnect it IMMEDIATELY here,
                // synchronously, before the OS finishes establishing audio profiles.
                //
                // forceDisconnectImmediate() calls device.disconnect() (ACL teardown)
                // in the SAME thread as onReceive — no async delay — then schedules
                // profile-level disconnects as a safety net.
                // ────────────────────────────────────────────────────────────────
                if (app.btDisconnector.shouldBlockReconnect()) {
                    app.btDisconnector.forceDisconnectImmediate(device)
                    return
                }

                app.btMonitor.onDeviceConnected(device)
                if (app.timerManager.state.value.isPaused) {
                    app.timerManager.resumeTimer()
                }
                SleepTimerService.startIfAllowed(context)
                WidgetUpdater.update(context)
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = getDevice(intent) ?: return
                val removed = app.btMonitor.onDeviceDisconnected(device)

                if (removed != null) {
                    val endTime  = System.currentTimeMillis()
                    val duration = endTime - removed.startTime
                    if (duration > 5_000L) {
                        app.sessionScope.launch {
                            app.db.sessionDao().insert(
                                SessionEntity(
                                    deviceName = removed.name,
                                    startTime  = removed.startTime,
                                    endTime    = endTime,
                                    duration   = duration,
                                    date       = LocalDate.now().toString()
                                )
                            )
                        }
                    }
                }

                if (!app.btMonitor.hasConnectedDevice() && app.timerManager.isRunning()) {
                    app.timerManager.pauseTimer()
                }
                WidgetUpdater.update(context)
            }
        }
    }

    private fun getDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }
}
