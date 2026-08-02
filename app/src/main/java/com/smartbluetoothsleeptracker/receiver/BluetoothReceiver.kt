package com.smartbluetoothsleeptracker.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smartbluetoothsleeptracker.SleepBTApp

class BluetoothReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BtReceiver"
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? SleepBTApp ?: return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                Log.d(TAG, "ACL_CONNECTED: ${device.name ?: device.address}")

                // If cooldown is active and this device is being blocked, enforce disconnect
                if (app.disconnector.shouldBlockDevice(device.address)) {
                    Log.i(TAG, "Cooldown active — enforcing re-disconnect on ${device.address}")
                    app.disconnector.enforceDisconnect(device)
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                Log.d(TAG, "ACL_DISCONNECTED: ${device.name ?: device.address}")
            }
        }
    }
}
