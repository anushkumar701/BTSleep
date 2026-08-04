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

    const val NOTIF_TIMER = 1
    const val NOTIF_WARNING = 2
    const val NOTIF_COOLDOWN = 3
    const val NOTIF_DISCONNECT_RESULT = 4

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
    }

    /**
     * Foreground service notification showing countdown.
     * When [warningText] is provided, it replaces the subtitle with a warning line.
     */
    fun timerNotification(
        ctx: Context,
        remainingText: String,
        warningText: String? = null
    ): NotificationCompat.Builder {
        val cancelIntent = PendingIntent.getService(
            ctx, 100,
            Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val extendIntent = PendingIntent.getService(
            ctx, 101,
            Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_EXTEND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(ctx, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SleepBT Active")
            .setContentText(if (warningText != null) "$remainingText  •  $warningText" else remainingText)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Extend", extendIntent)
            .addAction(0, "Cancel", cancelIntent)
            .setContentIntent(launchIntent(ctx))
    }

    /**
     * Cooldown active notification with "Allow reconnect" action.
     */
    fun cooldownNotification(ctx: Context, secondsLeft: Int): NotificationCompat.Builder {
        val allowIntent = PendingIntent.getService(
            ctx, 104,
            Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_ALLOW_RECONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(ctx, CHANNEL_COOLDOWN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reconnect blocked")
            .setContentText("${secondsLeft}s remaining — device will be re-disconnected if it reconnects")
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Allow reconnect now", allowIntent)
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

        return NotificationCompat.Builder(ctx, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reconnect Blocked")
            .setContentText("Blocked reconnect attempt from $deviceName (Cooldown Active)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .addAction(0, "Allow Reconnect Now", allowIntent)
            .setContentIntent(launchIntent(ctx))
    }

    /**
     * Result notification after disconnect attempt.
     */
    fun disconnectResultNotification(ctx: Context, success: Boolean, deviceName: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(ctx, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (success) "Disconnected" else "Disconnect failed")
            .setContentText(
                if (success) "$deviceName disconnected successfully"
                else "Couldn't disconnect $deviceName automatically — tap to open Bluetooth settings"
            )
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
