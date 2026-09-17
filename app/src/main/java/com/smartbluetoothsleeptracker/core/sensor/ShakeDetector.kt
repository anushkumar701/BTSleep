package com.smartbluetoothsleeptracker.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Lightweight shake detector that only listens during the warning window
 * before timer expiration. Allows sleepy users to gently nudge/shake their
 * device to extend the sleep timer without opening their eyes or turning on the screen.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"
        // Acceleration threshold for shake detection (in m/s^2)
        // 13.0 m/s^2 represents a deliberate gentle shake/nudge above normal gravity (9.8 m/s^2)
        private const val SHAKE_THRESHOLD_GRAVITY = 13.5f
        private const val SHAKE_SLOP_TIME_MS = 500L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isListening = false
    private var lastShakeTimestamp = 0L

    fun startListening() {
        if (isListening || accelerometer == null) return
        isListening = sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        ) ?: false
        Log.d(TAG, "ShakeDetector started listening: $isListening")
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        Log.d(TAG, "ShakeDetector stopped listening")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            if (lastShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }
            lastShakeTimestamp = now
            Log.i(TAG, "Shake detected (gForce=$gForce)! Triggering onShake callback")
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
