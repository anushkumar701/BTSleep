package com.smartbluetoothsleeptracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
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

class WiredHeadsetReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WiredHeadsetReceiver"
        const val WIRED_ADDRESS = "WIRED_HEADPHONES"
        const val WIRED_NAME = "Wired Headphones"
        private const val PREFS_NAME = "bt_connection_tracker"
        private const val KEY_START_TIME = "start_time_WIRED_HEADPHONES"

        fun getWiredConnectTime(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getLong(KEY_START_TIME, 0L)
        }

        fun isWiredConnected(context: Context): Boolean {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val plugTime = getWiredConnectTime(context)
            val isPlugged = audioManager?.isWiredHeadsetOn == true
            return isPlugged || plugTime > 0L
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != AudioManager.ACTION_HEADSET_PLUG && action != Intent.ACTION_HEADSET_PLUG) return

        val app = context.applicationContext as? SleepBTApp ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val state = intent.getIntExtra("state", -1)
        val headsetName = intent.getStringExtra("name")?.ifBlank { WIRED_NAME } ?: WIRED_NAME
        val now = System.currentTimeMillis()

        Log.d(TAG, "onReceive ACTION_HEADSET_PLUG state=$state, name=$headsetName")

        when (state) {
            1 -> {
                // Wired Headset Plugged In
                Log.i(TAG, "Wired Headset Plugged: $headsetName")
                if (!prefs.contains(KEY_START_TIME)) {
                    prefs.edit().putLong(KEY_START_TIME, now).apply()
                }

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val existing = app.db.deviceDao().getDevice(WIRED_ADDRESS)
                        if (existing == null) {
                            app.db.deviceDao().upsert(
                                DeviceEntity(
                                    address = WIRED_ADDRESS,
                                    name = headsetName,
                                    deviceType = DeviceType.WIRED_HEADPHONES,
                                    lastConnectedAt = now
                                )
                            )
                        } else {
                            app.db.deviceDao().updateLastConnected(WIRED_ADDRESS, now)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error registering wired headset: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            0 -> {
                // Wired Headset Unplugged
                Log.i(TAG, "Wired Headset Unplugged: $headsetName")
                val startTime = prefs.getLong(KEY_START_TIME, 0L)
                prefs.edit().remove(KEY_START_TIME).apply()

                if (startTime > 0L && now > startTime) {
                    val durationMs = now - startTime
                    val minutes = (durationMs / 60_000L).toInt()

                    if (minutes >= 1) {
                        val today = LocalDate.now().toString()
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                app.db.sessionDao().insert(
                                    SessionEntity(
                                        deviceAddress = WIRED_ADDRESS,
                                        deviceName = headsetName,
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
                                    .find { it.deviceAddress == WIRED_ADDRESS }

                                app.db.dailyUsageDao().upsert(
                                    DailyUsageEntity(
                                        date = today,
                                        deviceAddress = WIRED_ADDRESS,
                                        totalMinutes = (existingUsage?.totalMinutes ?: 0) + minutes,
                                        sessionCount = (existingUsage?.sessionCount ?: 0) + 1
                                    )
                                )
                                Log.i(TAG, "Saved wired headset session: $minutes min")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error recording wired usage: ${e.message}")
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                }
            }
        }
    }
}
