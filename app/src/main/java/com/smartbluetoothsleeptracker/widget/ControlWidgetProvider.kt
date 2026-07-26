package com.smartbluetoothsleeptracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.smartbluetoothsleeptracker.MainActivity
import com.smartbluetoothsleeptracker.R
import com.smartbluetoothsleeptracker.SleepBTApp
import com.smartbluetoothsleeptracker.service.SleepTimerService

class ControlWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val app = context.applicationContext as SleepBTApp
            val timerState = app.timerManager.state.value
            val devices = app.btMonitor.devices.value

            val views = RemoteViews(context.packageName, R.layout.widget_control)

            val deviceName = devices.firstOrNull()?.name ?: "No Device"
            val statusText = when {
                timerState.isRunning -> formatMillis(timerState.remainingMillis)
                timerState.isPaused -> "Paused"
                devices.isNotEmpty() -> "Connected"
                else -> "No Device"
            }

            views.setTextViewText(R.id.widget_device_name, deviceName)
            views.setTextViewText(R.id.widget_status, statusText)
            views.setCharSequence(
                R.id.widget_btn, "setText",
                if (timerState.isRunning || timerState.isPaused) "STOP" else "START"
            )

            // Open app on click
            val openIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, openIntent)

            // Start/Stop action
            val actionIntent = if (timerState.isRunning || timerState.isPaused) {
                PendingIntent.getService(
                    context, 1, SleepTimerService.cancelIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    context, 1, SleepTimerService.startTimerIntent(context, 30L),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            views.setOnClickPendingIntent(R.id.widget_btn, actionIntent)

            manager.updateAppWidget(widgetId, views)
        }

        private fun formatMillis(millis: Long): String {
            val s = millis / 1000L
            val m = s / 60L
            val h = m / 60L
            return if (h > 0L) "%d:%02d:%02d".format(h, m % 60, s % 60)
            else "%02d:%02d".format(m, s % 60)
        }
    }
}
