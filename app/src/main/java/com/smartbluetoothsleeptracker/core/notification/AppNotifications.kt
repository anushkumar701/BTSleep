package com.smartbluetoothsleeptracker.core.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smartbluetoothsleeptracker.MainActivity
import com.smartbluetoothsleeptracker.R
import com.smartbluetoothsleeptracker.service.SleepTimerService

class AppNotifications(private val context: Context) {

    companion object {
        const val CHANNEL_TIMER = "sleepbt_timer"
        const val CHANNEL_ALERT = "sleepbt_alert"
        const val ID_FOREGROUND = 1001
        const val ID_WARNING = 1002
        const val ID_ACTION = 1003
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, "Sleep Timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Running sleep timer countdown"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Sleep Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "2-minute disconnect warnings"
            }
        )
    }

    fun buildForegroundNotification(
        remainingMillis: Long,
        deviceName: String?,
        isPaused: Boolean,
        blockerActive: Boolean
    ): Notification {
        val contentTitle = when {
            blockerActive -> "Reconnect Blocked"
            isPaused -> "Timer Paused"
            remainingMillis > 0L -> formatTime(remainingMillis) + " until disconnect"
            else -> "SleepBT Active"
        }
        val contentText = when {
            blockerActive -> "Preventing auto-reconnect..."
            deviceName != null -> "Device: $deviceName"
            else -> "Waiting for Bluetooth device"
        }

        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelIntent = PendingIntent.getService(
            context, 1,
            SleepTimerService.cancelIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val extendIntent = PendingIntent.getService(
            context, 2,
            SleepTimerService.extendIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_bt_notify)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (remainingMillis > 0L && !isPaused) {
            builder.addAction(0, "Stop", cancelIntent)
            builder.addAction(0, "+10m", extendIntent)
        } else if (isPaused) {
            builder.addAction(0, "Cancel", cancelIntent)
        } else if (blockerActive) {
            val dismissIntent2 = PendingIntent.getService(
                context, 4,
                SleepTimerService.dismissWarningIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Dismiss", dismissIntent2)
        }

        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun updateForegroundNotification(
        remainingMillis: Long,
        deviceName: String?,
        isPaused: Boolean,
        blockerActive: Boolean
    ) {
        runCatching {
            NotificationManagerCompat.from(context).notify(
                ID_FOREGROUND,
                buildForegroundNotification(remainingMillis, deviceName, isPaused, blockerActive)
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun showTwoMinuteWarning(deviceName: String?) {
        val dismissIntent = PendingIntent.getService(
            context, 10,
            SleepTimerService.dismissWarningIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_bt_notify)
            .setContentTitle("Disconnecting Soon")
            .setContentText("${deviceName ?: "Your device"} will disconnect in 2 minutes")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Dismiss", dismissIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_WARNING, notification)
        }
    }

    fun cancelWarning() {
        NotificationManagerCompat.from(context).cancel(ID_WARNING)
    }

    private fun formatTime(millis: Long): String {
        val totalSec = millis / 1000L
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
