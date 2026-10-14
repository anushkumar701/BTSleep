package com.smartbluetoothsleeptracker.core.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.smartbluetoothsleeptracker.SleepBTApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manages distinct, non-jarring haptic vibration patterns for timer lifecycle events and UI interactions.
 * Respects the user's hapticFeedbackEnabled preference and provides universal compatibility across
 * all Android hardware (ERM motors, LRA motors, Samsung, Xiaomi, OnePlus, Pixel, etc.).
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

    @Suppress("DEPRECATION")
    private fun getVibrator(context: Context): Vibrator? {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return if (vibrator != null && vibrator.hasVibrator()) vibrator else null
    }

    private fun playVibration(
        context: Context,
        predefinedEffect: Int? = null,
        fallbackDurationMs: Long,
        waveformTimings: LongArray? = null,
        waveformAmplitudes: IntArray? = null
    ) {
        if (!isEnabledCached) return
        val vibrator = getVibrator(context) ?: return

        runCatching {
            // 1. Try predefined effect if supported
            if (predefinedEffect != null) {
                val isSupported = runCatching {
                    vibrator.areAllEffectsSupported(predefinedEffect) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
                }.getOrDefault(false)

                if (isSupported) {
                    val effect = VibrationEffect.createPredefined(predefinedEffect)
                    vibrateEffect(vibrator, effect)
                    return
                }
            }

            // 2. Try waveform if provided
            if (waveformTimings != null) {
                val hasAmp = runCatching { vibrator.hasAmplitudeControl() }.getOrDefault(false)
                val effect = if (waveformAmplitudes != null && hasAmp) {
                    VibrationEffect.createWaveform(waveformTimings, waveformAmplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(waveformTimings, -1)
                }
                vibrateEffect(vibrator, effect)
                return
            }

            // 3. Fallback one-shot VibrationEffect (API 26+)
            val effect = VibrationEffect.createOneShot(fallbackDurationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrateEffect(vibrator, effect)
        }.recoverCatching {
            // 4. Absolute legacy fallback for any OEM driver / firmware quirk
            @Suppress("DEPRECATION")
            if (waveformTimings != null) {
                vibrator.vibrate(waveformTimings, -1)
            } else {
                vibrator.vibrate(fallbackDurationMs)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateEffect(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // USAGE_ALARM / USAGE_COMMUNICATION_REQUEST prevents Android OS from muting
            // vibrations when the user's global "Touch feedback" toggle is off in phone Settings
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            runCatching { vibrator.vibrate(effect, attributes) }
                .onFailure { vibrator.vibrate(effect) }
        } else {
            val attributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            runCatching { vibrator.vibrate(effect, attributes) }
                .onFailure { vibrator.vibrate(effect) }
        }
    }

    /**
     * Warning pulse: two strong distinct pulses (80ms pulse, 70ms pause, 80ms pulse).
     */
    fun vibrateWarning(context: Context) {
        val timings = longArrayOf(0, 80, 70, 80)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        playVibration(
            context = context,
            fallbackDurationMs = 80L,
            waveformTimings = timings,
            waveformAmplitudes = amplitudes
        )
    }

    /**
     * Extend pulse: one solid pulse (65ms).
     */
    fun vibrateExtend(context: Context) {
        playVibration(
            context = context,
            fallbackDurationMs = 65L
        )
    }

    /**
     * Disconnected pulse: one strong confirmation pulse (180ms).
     */
    fun vibrateDisconnected(context: Context) {
        playVibration(
            context = context,
            fallbackDurationMs = 180L
        )
    }

    @Volatile
    private var lastTickTimeMs = 0L

    @Suppress("DEPRECATION")
    private fun vibrateTouchEffect(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            runCatching { vibrator.vibrate(effect, attributes) }
                .onFailure { vibrator.vibrate(effect) }
        } else {
            val attributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .build()
            runCatching { vibrator.vibrate(effect, attributes) }
                .onFailure {
                    val alarmAttrs = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                    runCatching { vibrator.vibrate(effect, alarmAttrs) }
                        .onFailure { vibrator.vibrate(effect) }
                }
        }
    }

    /**
     * Dial tick pulse: ultra-smooth, gentle tactile tick for rotary dial adjustments only.
     * Calibrated duration and amplitude to guarantee physical motor actuation across all phone models.
     */
    fun vibrateTick(context: Context) {
        if (!isEnabledCached) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTickTimeMs < 20L) return
        lastTickTimeMs = now

        val vibrator = getVibrator(context) ?: return
        runCatching {
            val isTickSupported = runCatching {
                vibrator.areAllEffectsSupported(VibrationEffect.EFFECT_TICK) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
            }.getOrDefault(false)

            if (isTickSupported) {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                vibrateTouchEffect(vibrator, effect)
                return
            }

            // Calibrated smooth pulse (25ms, amplitude 180): overcoming motor inertia without buzzing
            val hasAmp = runCatching { vibrator.hasAmplitudeControl() }.getOrDefault(false)
            val amp = if (hasAmp) 180 else VibrationEffect.DEFAULT_AMPLITUDE
            val effect = VibrationEffect.createOneShot(25L, amp)
            vibrateTouchEffect(vibrator, effect)
        }.recoverCatching {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25L)
        }
    }

    /**
     * Click pulse: kept empty as user explicitly requested vibration only for the rotary dialer.
     */
    fun vibrateClick(@Suppress("UNUSED_PARAMETER") context: Context) {
        // No-op: Vibration is restricted exclusively to the rotary dialer
    }

    /**
     * Test vibration triggered from Settings screen to prove haptic motor is functional.
     */
    fun testVibration(context: Context) {
        val vibrator = getVibrator(context) ?: return
        runCatching {
            val effect = VibrationEffect.createOneShot(100L, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrateEffect(vibrator, effect)
        }.recoverCatching {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100L)
        }
    }
}
