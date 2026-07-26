package com.smartbluetoothsleeptracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdater {
    fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)

        // Control widget
        val controlIds = manager.getAppWidgetIds(
            ComponentName(context, ControlWidgetProvider::class.java)
        )
        controlIds.forEach { ControlWidgetProvider.updateWidget(context, manager, it) }

        // Minimal widget
        val minimalIds = manager.getAppWidgetIds(
            ComponentName(context, MinimalWidgetProvider::class.java)
        )
        minimalIds.forEach { MinimalWidgetProvider.updateWidget(context, manager, it) }
    }
}
