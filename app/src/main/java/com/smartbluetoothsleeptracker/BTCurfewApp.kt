package com.smartbluetoothsleeptracker

import android.app.Application
import com.smartbluetoothsleeptracker.core.bluetooth.BluetoothDisconnector
import com.smartbluetoothsleeptracker.core.bluetooth.BluetoothMonitor
import com.smartbluetoothsleeptracker.core.notification.AppNotifications
import com.smartbluetoothsleeptracker.core.playback.PlaybackController
import com.smartbluetoothsleeptracker.core.screen.ScreenController
import com.smartbluetoothsleeptracker.data.db.AppDatabase
import com.smartbluetoothsleeptracker.data.prefs.AppPrefs

class BTCurfewApp : Application() {

    lateinit var db: AppDatabase
    lateinit var prefs: AppPrefs
    lateinit var disconnector: BluetoothDisconnector
    lateinit var btMonitor: BluetoothMonitor
    lateinit var playbackController: PlaybackController
    lateinit var screenController: ScreenController

    override fun onCreate() {
        super.onCreate()

        db = AppDatabase.get(this)
        prefs = AppPrefs(this)
        disconnector = BluetoothDisconnector(this, db)
        btMonitor = BluetoothMonitor(this, db)
        playbackController = PlaybackController(this)
        screenController = ScreenController(this)

        AppNotifications.createChannels(this)

        // Wire up cooldown enforcement: when a device reconnects, check if it should be blocked
        btMonitor.onDeviceConnected = { device ->
            if (disconnector.shouldBlockDevice(device.address)) {
                disconnector.enforceDisconnect(device)
            }
        }

        btMonitor.start()
    }
}
