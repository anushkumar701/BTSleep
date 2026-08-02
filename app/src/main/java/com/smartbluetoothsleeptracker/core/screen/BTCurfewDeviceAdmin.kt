package com.smartbluetoothsleeptracker.core.screen

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * DeviceAdminReceiver for the lockNow() functionality.
 * The user must manually grant Device Admin via:
 *   Settings > Security > Device admin apps > SleepBT
 */
class BTCurfewDeviceAdmin : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceAdmin"

        fun componentName(context: Context): ComponentName =
            ComponentName(context, BTCurfewDeviceAdmin::class.java)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling Device Admin will prevent SleepBT from locking the screen on timer expiry."
    }
}
