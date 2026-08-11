package com.smartbluetoothsleeptracker.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smartbluetoothsleeptracker.SleepBTApp
import com.smartbluetoothsleeptracker.data.db.DailyUsageEntity
import com.smartbluetoothsleeptracker.data.db.DeviceEntity
import com.smartbluetoothsleeptracker.data.db.DeviceType
import com.smartbluetoothsleeptracker.data.db.SessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BluetoothReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BtReceiver"
        private const val PREFS_NAME = "bt_connection_tracker"
        private const val KEY_PREFIX_START = "start_time_"

        fun getActiveConnectTime(context: Context, address: String): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getLong("$KEY_PREFIX_START$address", 0L)
        }

        fun setActiveConnectTime(context: Context, address: String, timestamp: Long) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains("$KEY_PREFIX_START$address")) {
                prefs.edit().putLong("$KEY_PREFIX_START$address", timestamp).apply()
            }
        }

        fun clearActiveConnectTime(context: Context, address: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove("$KEY_PREFIX_START$address").apply()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as? SleepBTApp ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        Log.d(TAG, "onReceive action: $action")

        val device = try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?: @Suppress("DEPRECATION") intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            }
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        }

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (device == null) return
                val address = device.address
                val now = System.currentTimeMillis()

                Log.i(TAG, "ACL_CONNECTED: ${device.name ?: address}")

                // Cooldown enforcement check
                if (app.disconnector.shouldBlockDevice(address)) {
                    Log.i(TAG, "Cooldown active — enforcing re-disconnect on $address")
                    app.disconnector.enforceDisconnect(device)
                    return
                }

                // Record connection start timestamp in persistent SharedPreferences
                if (!prefs.contains("$KEY_PREFIX_START$address")) {
                    prefs.edit().putLong("$KEY_PREFIX_START$address", now).apply()
                }

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val devType = inferDeviceType(device)
                        val existing = app.db.deviceDao().getDevice(address)
                        val method = app.disconnector.probeWorkingMethodSilently(device)
                        if (existing == null) {
                            app.db.deviceDao().upsert(
                                DeviceEntity(
                                    address = address,
                                    name = device.name ?: address,
                                    deviceType = devType,
                                    workingDisconnectMethod = method,
                                    lastConnectedAt = now
                                )
                            )
                        } else {
                            app.db.deviceDao().updateLastConnected(address, now)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving connected device: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (device == null) return
                val address = device.address
                val now = System.currentTimeMillis()

                Log.i(TAG, "ACL_DISCONNECTED: ${device.name ?: address}")

                val startTime = prefs.getLong("$KEY_PREFIX_START$address", 0L)
                prefs.edit().remove("$KEY_PREFIX_START$address").apply()

                if (startTime > 0L && now > startTime) {
                    val durationMs = now - startTime
                    val minutes = (durationMs / 60_000L).toInt()

                    // Record session and daily usage for background connections lasting >= 1 minute
                    if (minutes >= 1) {
                        val today = LocalDate.now().toString()
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // Skip if TimerService created or updated a session for this device recently (last 5 minutes)
                                val recentSession = app.db.sessionDao().getRecentSessionForDevice(address, now - 300_000L)
                                if (recentSession != null) {
                                    Log.i(TAG, "Skipping background session insertion — already recorded by TimerService")
                                    return@launch
                                }

                                app.db.sessionDao().insert(
                                    SessionEntity(
                                        deviceAddress = address,
                                        deviceName = device.name ?: address,
                                        startTime = startTime,
                                        endTime = now,
                                        plannedDurationMin = minutes,
                                        actualDurationMin = minutes,
                                        disconnectConfirmed = true,
                                        date = today
                                    )
                                )
                                app.db.sessionDao().pruneOldSessions(10)

                                val existingUsage = app.db.dailyUsageDao().getForDate(today)
                                    .find { it.deviceAddress == address }

                                app.db.dailyUsageDao().upsert(
                                    DailyUsageEntity(
                                        date = today,
                                        deviceAddress = address,
                                        totalMinutes = (existingUsage?.totalMinutes ?: 0) + minutes,
                                        sessionCount = (existingUsage?.sessionCount ?: 0) + 1
                                    )
                                )
                                Log.i(TAG, "Saved background session: $minutes min for ${device.name ?: address}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error recording usage on disconnect: ${e.message}")
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
                if (adapter.isEnabled) {
                    val now = System.currentTimeMillis()
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            adapter.bondedDevices?.forEach { dev ->
                                val isConn = try {
                                    dev.javaClass.getMethod("isConnected").invoke(dev) as? Boolean ?: false
                                } catch (_: Exception) { false }

                                if (isConn) {
                                    if (!prefs.contains("$KEY_PREFIX_START${dev.address}")) {
                                        prefs.edit().putLong("$KEY_PREFIX_START${dev.address}", now).apply()
                                    }
                                    val devType = inferDeviceType(dev)
                                    val existing = app.db.deviceDao().getDevice(dev.address)
                                    if (existing == null) {
                                        app.db.deviceDao().upsert(
                                            DeviceEntity(
                                                address = dev.address,
                                                name = dev.name ?: dev.address,
                                                deviceType = devType,
                                                lastConnectedAt = now
                                            )
                                        )
                                    } else {
                                        app.db.deviceDao().updateLastConnected(dev.address, now)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing connected devices on boot/state change: ${e.message}")
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun inferDeviceType(device: BluetoothDevice): DeviceType {
        val major = device.bluetoothClass?.majorDeviceClass ?: return DeviceType.OTHER
        return when (major) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                val name = (device.name ?: "").lowercase()
                when {
                    name.contains("buds") || name.contains("pod") || name.contains("ear") -> DeviceType.EARBUDS
                    name.contains("neck") || name.contains("band") -> DeviceType.NECKBAND
                    name.contains("speaker") || name.contains("soundbar") || name.contains("theatre") -> DeviceType.HOME_THEATRE
                    else -> DeviceType.EARBUDS
                }
            }
            BluetoothClass.Device.Major.COMPUTER -> DeviceType.PC
            BluetoothClass.Device.Major.WEARABLE -> DeviceType.SMARTWATCH
            else -> DeviceType.OTHER
        }
    }
}
