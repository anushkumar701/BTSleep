package com.smartbluetoothsleeptracker.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.smartbluetoothsleeptracker.SleepBTApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Manages distinct, non-jarring haptic vibration patterns for timer lifecycle events.
 * Respects the user's hapticFeedbackEnabled preference.
 */
object HapticManager {

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private suspend fun isHapticsEnabled(context: Context): Boolean {
        val app = context.applicationContext as? SleepBTApp ?: return true
        return app.prefs.settings.first().hapticFeedbackEnabled
    }

    /**
     * Warning pulse: two short gentle pulses (50ms pulse, 50ms pause, 50ms pulse).
     */
    fun vibrateWarning(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val app = context.applicationContext as? SleepBTApp
        if (app != null) {
            val enabled = runBlocking { isHapticsEnabled(context) }
            if (!enabled) return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 50, 50, 50)
            val amplitudes = intArrayOf(0, 100, 0, 100)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 50, 50, 50), -1)
        }
    }

    /**
     * Extend pulse: one short pulse (40ms).
     */
    fun vibrateExtend(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val app = context.applicationContext as? SleepBTApp
        if (app != null) {
            val enabled = runBlocking { isHapticsEnabled(context) }
            if (!enabled) return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40L)
        }
    }

    /**
     * Disconnected pulse: one gentle longer pulse (150ms).
     */
    fun vibrateDisconnected(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val app = context.applicationContext as? SleepBTApp
        if (app != null) {
            val enabled = runBlocking { isHapticsEnabled(context) }
            if (!enabled) return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(150L)
        }
    }
}
