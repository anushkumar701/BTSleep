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
        // 18.5 m/s^2 represents a deliberate intentional shake, well above normal gravity (9.8 m/s^2) and bed movement
        private const val SHAKE_THRESHOLD_GRAVITY = 18.5f
        private const val SHAKE_RESET_TIMEOUT_MS = 1000L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isListening = false
    private var shakeCount = 0
    private var lastShakeTimestamp = 0L

    fun startListening() {
        if (isListening || accelerometer == null) return
        shakeCount = 0
        lastShakeTimestamp = 0L
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
        shakeCount = 0
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
            if (now - lastShakeTimestamp > SHAKE_RESET_TIMEOUT_MS) {
                shakeCount = 1
                lastShakeTimestamp = now
                Log.d(TAG, "First deliberate shake pulse detected (gForce=$gForce)")
            } else if (now - lastShakeTimestamp > 250L) {
                // Second shake within 1 second, separated by at least 250ms
                shakeCount++
                lastShakeTimestamp = now
                if (shakeCount >= 2) {
                    Log.i(TAG, "Deliberate double-shake confirmed! Triggering onShake callback")
                    shakeCount = 0
                    onShake()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
