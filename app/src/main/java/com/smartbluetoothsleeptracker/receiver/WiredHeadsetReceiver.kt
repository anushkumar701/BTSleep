package com.smartbluetoothsleeptracker.receiver

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Utility for wired headset detection and address constants.
 */
object WiredHeadsetReceiver {
    const val WIRED_ADDRESS = "WIRED_HEADPHONES"
    const val WIRED_NAME = "Wired Headphones"

    fun isWiredConnected(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { dev ->
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }
}
