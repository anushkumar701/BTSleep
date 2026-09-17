package com.smartbluetoothsleeptracker.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.smartbluetoothsleeptracker.SleepBTApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manages distinct, non-jarring haptic vibration patterns for timer lifecycle events.
 * Respects the user's hapticFeedbackEnabled preference.
 */
object HapticManager {

    @Volatile
    private var isEnabledCached = true

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val app = context.applicationContext as? SleepBTApp ?: return
        app.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            app.prefs.settings.collect { settings ->
                isEnabledCached = settings.hapticFeedbackEnabled
            }
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Warning pulse: two short gentle pulses (50ms pulse, 50ms pause, 50ms pulse).
     */
    fun vibrateWarning(context: Context) {
        if (!isEnabledCached) return
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

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
        if (!isEnabledCached) return
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

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
        if (!isEnabledCached) return
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(150L)
        }
    }

    /**
     * Dial tick pulse: crisp tick for minute-by-minute rotary dial adjustments.
     */
    fun vibrateTick(context: Context) {
        if (!isEnabledCached) return
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(15L, 100))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15L)
        }
    }

    /**
     * Click pulse: crisp feedback for button presses.
     */
    fun vibrateClick(context: Context) {
        if (!isEnabledCached) return
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20L)
        }
    }
}
