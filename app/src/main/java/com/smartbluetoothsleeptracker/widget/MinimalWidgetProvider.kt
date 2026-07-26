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

class MinimalWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val app = context.applicationContext as SleepBTApp
            val timerState = app.timerManager.state.value
            val devices = app.btMonitor.devices.value

            val views = RemoteViews(context.packageName, R.layout.widget_minimal)

            val label = when {
                timerState.isRunning -> "⏱ ${formatMillis(timerState.remainingMillis)}"
                timerState.isPaused -> "⏸ Paused"
                devices.isNotEmpty() -> "● Connected"
                else -> "○ No Device"
            }
            views.setTextViewText(R.id.widget_minimal_text, label)

            val openIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_minimal_root, openIntent)

            manager.updateAppWidget(widgetId, views)
        }

        private fun formatMillis(millis: Long): String {
            val m = millis / 60_000L
            val s = (millis % 60_000L) / 1000L
            return "%02d:%02d".format(m, s)
        }
    }
}
