package com.smartbluetoothsleeptracker.core.wifi

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Wifi controller for the timer expiry sequence.
 *
 * Strategy:
 * 1. If Shizuku is available: disable via privileged shell command
 * 2. Fallback: launch system Wifi panel (user taps to disable)
 *
 * Does NOT use WifiManager.setWifiEnabled() — it's a no-op on API 29+.
 */
class WifiController(private val context: Context) {

    companion object {
        private const val TAG = "WifiCtrl"
    }

    /**
     * Attempt to disable wifi. Runs shell operations asynchronously on Dispatchers.IO.
     * @param useShizuku whether Shizuku is configured and should be tried first
     * @return true if wifi was disabled silently, false if system panel was launched
     */
    suspend fun disableWifi(useShizuku: Boolean): Boolean {
        if (useShizuku) {
            val silentSuccess = disableViaShell()
            if (silentSuccess) return true
        }

        // Fallback: open system wifi panel (must run on Main/UI context)
        withContext(Dispatchers.Main) {
            launchWifiPanel()
        }
        return false
    }

    /**
     * Tries to disable wifi via shell commands (works with root or Shizuku).
     * Isolated to Dispatchers.IO to prevent main-thread blocking / ANRs.
     */
    private suspend fun disableViaShell(): Boolean = withContext(Dispatchers.IO) {
        val commands = listOf(
            arrayOf("cmd", "wifi", "set-wifi-enabled", "disabled"),
            arrayOf("svc", "wifi", "disable")
        )

        for (cmd in commands) {
            try {
                val process = Runtime.getRuntime().exec(cmd)
                val exited = process.waitFor(3, TimeUnit.SECONDS)
                process.destroyForcibly()
                if (exited && process.exitValue() == 0) {
                    Log.i(TAG, "Wifi disabled via: ${cmd.joinToString(" ")}")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shell command failed: ${cmd.joinToString(" ")} — ${e.message}")
            }
        }

        // Root fallback
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "svc wifi disable"))
            val exited = process.waitFor(2, TimeUnit.SECONDS)
            process.destroyForcibly()
            if (exited && process.exitValue() == 0) {
                Log.i(TAG, "Wifi disabled via root")
                return@withContext true
            }
        } catch (_: Exception) {}

        false
    }

    /**
     * Opens the system Wifi settings panel (non-silent fallback).
     */
    private fun launchWifiPanel() {
        try {
            val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Launched system Wifi panel")
        } catch (e: Exception) {
            // Fallback to regular wifi settings
            try {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open Wifi settings: ${e2.message}")
            }
        }
    }
}
