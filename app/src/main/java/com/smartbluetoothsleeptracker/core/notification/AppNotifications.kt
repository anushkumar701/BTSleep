package com.smartbluetoothsleeptracker.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.smartbluetoothsleeptracker.R
import com.smartbluetoothsleeptracker.service.TimerService

object AppNotifications {

    const val CHANNEL_TIMER = "sleepbt_timer"
    const val CHANNEL_ALERTS = "sleepbt_alerts"
    const val CHANNEL_COOLDOWN = "sleepbt_cooldown"
    const val CHANNEL_DEVICE_STATUS = "sleepbt_device_status"

    const val NOTIF_TIMER = 1
    const val NOTIF_WARNING = 2
    const val NOTIF_COOLDOWN = 3
    const val NOTIF_DISCONNECT_RESULT = 4
    const val NOTIF_BATTERY_STATUS = 1000

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, "Timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Active sleep timer countdown"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Timer warnings and disconnect results"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_COOLDOWN, "Cooldown", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Reconnect blocker active"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DEVICE_STATUS, "Device Status & Battery", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Shows battery percentage when Bluetooth audio connects"
                setShowBadge(false)
            }
        )
    }

    /**
     * Clean, informative notification displayed when a Bluetooth audio device connects,
     * highlighting the device name and battery levels.
     */
    fun batteryStatusNotification(
        ctx: Context,
        deviceName: String,
        batterySummary: String
    ): NotificationCompat.Builder {
        val dev = if (deviceName.isNotBlank() && deviceName != "Unknown") deviceName else "Bluetooth Audio"
        return NotificationCompat.Builder(ctx, CHANNEL_DEVICE_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$dev Connected")
            .setContentText(batterySummary)
            .setSubText("Connected")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchIntent(ctx))
    }

    /**
     * Foreground service notification showing countdown, connected device branding, battery, and accurate time.
     * Clean, modern, minimalist layout.
     */
    fun timerNotification(
        ctx: Context,
        remainingText: String,
        warningText: String? = null,
        isPaused: Boolean = false,
        endTimeMillis: Long = 0L,
        deviceName: String? = null,
        batteryPercent: Int? = null,
        extendMinutes: Int = 5
    ): NotificationCompat.Builder {
        val pauseResumeIntent = PendingIntent.getService(
            ctx, 102,
            Intent(ctx, TimerService::class.java).setAction(
                if (isPaused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val extendIntent = PendingIntent.getService(
            ctx, 101,
            Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_EXTEND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val endNowIntent = PendingIntent.getService(
            ctx, 103,
            Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_END_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dev = if (!deviceName.isNullOrBlank() && deviceName != "Unknown") deviceName else "Bluetooth Audio"

        val title = when {
            isPaused -> "Paused • $dev"
            warningText != null -> "Ending Soon • $dev"
            else -> dev
        }

        val content = when {
            isPaused -> "Timer paused • Tap Resume to continue"
            warningText != null -> warningText
            endTimeMillis > System.currentTimeMillis() -> {
                val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                val endFormatted = timeFormat.format(java.util.Date(endTimeMillis))
                "Turns off at $endFormatted"
            }
            else -> "Finishing session..."
        }

        val subText = when {
            batteryPercent != null && batteryPercent in 0..100 -> {
                if (warningText != null) "Ending Soon • $batteryPercent%"
                else if (isPaused) "Paused • $batteryPercent%"
                else "$batteryPercent%"
            }
            isPaused -> "Paused"
            warningText != null -> "Ending Soon"
            else -> "SleepBT"
        }

        val builder = NotificationCompat.Builder(ctx, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(subText)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .addAction(0, if (isPaused) "Resume" else "Pause", pauseResumeIntent)
            .addAction(0, "+${extendMinutes}m", extendIntent)
            .addAction(0, "Stop", endNowIntent)
            .setContentIntent(launchIntent(ctx))

        if (!isPaused && endTimeMillis > System.currentTimeMillis()) {
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
            builder.setWhen(endTimeMillis)
            builder.setShowWhen(true)
        } else {
            builder.setUsesChronometer(false)
            builder.setShowWhen(false)
        }

        return builder
    }

    /**
     * Immediate non-ticking notification shown when timer finishes and audio fade/disconnect sequence begins.
     * Guarantees zero chronometer display and prevents negative time (-0:12) from rendering.
     */
    fun finishingNotification(ctx: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(ctx, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Finishing SleepBT")
            .setContentText("Stopping audio & disconnecting...")
            .setSubText("Finishing")
            .setOngoing(true)
            .setSilent(true)
            .setUsesChronometer(false)
            .setShowWhen(false)
            .setContentIntent(launchIntent(ctx))
    }

    /**
     * Cooldown active notification with "Allow reconnect" action.
     */
    fun cooldownNotification(
        ctx: Context,
        secondsLeft: Int,
        deviceName: String? = null
    ): NotificationCompat.Builder {
        val allowIntent = PendingIntent.getService(
            ctx, 104,
            Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_ALLOW_RECONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dev = if (!deviceName.isNullOrBlank() && deviceName != "Unknown") deviceName else "Bluetooth device"

        return NotificationCompat.Builder(ctx, CHANNEL_COOLDOWN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sleep Protection Active")
            .setContentText("${secondsLeft}s cooldown — blocking reconnects from $dev")
            .setSubText("Cooldown")
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Allow Reconnect", allowIntent)
            .setContentIntent(launchIntent(ctx))
    }

    /**
     * Heads-up popup notification shown when a Bluetooth device attempts to reconnect during cooldown.
     */
    fun reconnectBlockedAlertNotification(ctx: Context, deviceName: String): NotificationCompat.Builder {
        val allowIntent = PendingIntent.getService(
            ctx, 105,
            Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_ALLOW_RECONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dev = if (deviceName.isNotBlank() && deviceName != "Unknown") deviceName else "Bluetooth device"

        return NotificationCompat.Builder(ctx, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reconnect Blocked")
            .setContentText("$dev tried to reconnect and was disconnected to protect your sleep.")
            .setSubText("Blocked")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .addAction(0, "Allow Reconnect", allowIntent)
            .setContentIntent(launchIntent(ctx))
    }

    /**
     * Result notification after disconnect attempt.
     */
    fun disconnectResultNotification(ctx: Context, success: Boolean, deviceName: String): NotificationCompat.Builder {
        val dev = if (deviceName.isNotBlank() && deviceName != "Unknown") deviceName else "Bluetooth device"

        return NotificationCompat.Builder(ctx, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (success) "Audio Paused & Disconnected" else "Disconnect Action Needed")
            .setContentText(
                if (success) "$dev disconnected. Sleep well! 🌙"
                else "Couldn't disconnect $dev automatically — tap to open Bluetooth settings"
            )
            .setSubText(if (success) "Sleep Complete" else "Manual Action")
            .setAutoCancel(true)
            .setContentIntent(
                if (success) launchIntent(ctx)
                else bluetoothSettingsIntent(ctx)
            )
    }

    private fun launchIntent(ctx: Context): PendingIntent {
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        return PendingIntent.getActivity(
            ctx, 0, intent ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun bluetoothSettingsIntent(ctx: Context): PendingIntent {
        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
