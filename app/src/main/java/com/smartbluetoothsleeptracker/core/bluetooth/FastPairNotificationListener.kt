package com.smartbluetoothsleeptracker.core.bluetooth

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.smartbluetoothsleeptracker.SleepBTApp
import java.lang.ref.WeakReference

/**
 * Listens for system and companion app notifications reporting earbuds & case battery
 * (Google Play Services Fast Pair, OnePlus/realme HeyMelody, Pixel Buds, Galaxy Wearable, etc.).
 *
 * PRIVACY GUARANTEE:
 * This listener strictly filters for battery status notifications and connected Bluetooth
 * audio devices. Non-battery notifications (messages, chats, emails, banking alerts, etc.)
 * are discarded immediately without being inspected, parsed, logged, or retained.
 */
class FastPairNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "FastPairNotifListener"
        const val FAST_PAIR_PACKAGE = "com.google.android.gms"
        const val BATTERY_CHANNEL_ID = "BATTERY_NOTIFICATION_CHANNEL"

        @Volatile
        var instance: WeakReference<FastPairNotificationListener>? = null

        fun isAccessGranted(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!flat.isNullOrEmpty()) {
                val names = flat.split(":")
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && cn.packageName == pkgName) {
                        return true
                    }
                }
            }
            return NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkgName)
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(context, FastPairNotificationListener::class.java).flattenToString()
                    )
                }
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching {
                context.startActivity(intent)
            }.onFailure {
                val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(fallback) }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = WeakReference(this)
        Log.i(TAG, "Notification listener connected. Scanning active notifications...")
        syncActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance?.get() == this) {
            instance = null
        }
        Log.i(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return
        processNotification(sbn)
    }

    fun syncActiveNotifications() {
        runCatching {
            val active = activeNotifications ?: return@runCatching
            for (sbn in active) {
                processNotification(sbn)
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to query active notifications: ${e.message}")
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notif.channelId
        } else null

        // 1. Strict Privacy Filter:
        // Only inspect notifications if:
        // - From GMS with BATTERY_NOTIFICATION_CHANNEL
        // - OR from a known Bluetooth audio companion app
        // - OR channelId contains "battery"
        // - OR extras title contains a currently connected Bluetooth device name
        val isFastPair = pkg == FAST_PAIR_PACKAGE && (channelId == BATTERY_CHANNEL_ID || channelId?.contains("battery", ignoreCase = true) == true)
        val isBatteryChannel = channelId?.contains("battery", ignoreCase = true) == true

        val app = applicationContext as? SleepBTApp
        val connectedDevices = app?.btMonitor?.connectedDevices?.value.orEmpty()

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val ticker = notif.tickerText?.toString()

        val matchesConnectedDevice = connectedDevices.any { dev ->
            FastPairNotificationParser.matchesDevice(title, dev.name)
        }

        if (!isFastPair && !isBatteryChannel && !matchesConnectedDevice) {
            // Discard immediately without parsing
            return
        }

        // 2. Parse battery details
        val parsed = FastPairNotificationParser.parse(
            title = title,
            text = text,
            bigText = bigText,
            tickerText = ticker
        ) ?: return

        if (!parsed.hasAnyBattery) return

        Log.i(TAG, "Detected battery notification from $pkg: dev=${parsed.deviceName}, L=${parsed.left}%, C=${parsed.case}%, R=${parsed.right}%, Main=${parsed.main}%")

        app?.btMonitor?.updateFromNotification(
            deviceName = parsed.deviceName,
            left = parsed.left,
            right = parsed.right,
            case = parsed.case,
            main = parsed.main,
            isCaseCharging = parsed.isCaseCharging
        )
    }
}
