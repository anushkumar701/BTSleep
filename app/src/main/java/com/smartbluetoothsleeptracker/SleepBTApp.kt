package com.smartbluetoothsleeptracker

import android.app.Application
import com.google.firebase.analytics.FirebaseAnalytics
import com.smartbluetoothsleeptracker.core.analytics.FirebaseAnalyticsHelper
import com.smartbluetoothsleeptracker.core.bluetooth.BluetoothDisconnector
import com.smartbluetoothsleeptracker.core.bluetooth.BluetoothMonitor
import com.smartbluetoothsleeptracker.core.notification.AppNotifications
import com.smartbluetoothsleeptracker.core.timer.SleepTimerManager
import com.smartbluetoothsleeptracker.data.db.AppDatabase
import com.smartbluetoothsleeptracker.data.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SleepBTApp : Application() {

    // App-level coroutine scope for DB writes from BroadcastReceiver
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Firebase
    val analyticsHelper by lazy { FirebaseAnalyticsHelper(FirebaseAnalytics.getInstance(this)) }

    // All singletons — lazy so they're created on first access, not at startup
    val prefs by lazy { AppPrefs(this) }
    val db by lazy { AppDatabase.get(this) }
    val timerManager by lazy { SleepTimerManager(prefs, analyticsHelper) }
    val btMonitor by lazy { BluetoothMonitor(this) }
    val btDisconnector by lazy { BluetoothDisconnector(this, analyticsHelper) }
    val notifications by lazy { AppNotifications(this) }

    override fun onCreate() {
        super.onCreate()
        // Create notification channels — safe to call multiple times
        notifications.createChannels()
        // Reconcile BT state on cold start
        btMonitor.reconcile()
    }
}
