package com.smartbluetoothsleeptracker.core.screen

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log

/**
 * Screen-off controller. Calls DevicePolicyManager.lockNow() if Device Admin is granted.
 * Runs last in the expiry sequence, after playback stop / BT disconnect / wifi off.
 */
class ScreenController(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCtrl"
    }

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /**
     * Whether this app is currently a Device Admin.
     */
    fun isDeviceAdminActive(): Boolean {
        return dpm.isAdminActive(BTCurfewDeviceAdmin.componentName(context))
    }

    /**
     * Lock the screen immediately. Requires Device Admin.
     * @return true if lockNow() was called, false if not admin
     */
    fun lockScreen(): Boolean {
        return if (isDeviceAdminActive()) {
            try {
                dpm.lockNow()
                Log.i(TAG, "Screen locked via DevicePolicyManager")
                true
            } catch (e: Exception) {
                Log.e(TAG, "lockNow() failed: ${e.message}")
                false
            }
        } else {
            Log.w(TAG, "Device Admin not active — cannot lock screen")
            false
        }
    }
}
