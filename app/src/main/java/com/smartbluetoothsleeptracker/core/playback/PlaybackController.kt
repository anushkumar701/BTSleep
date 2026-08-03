package com.smartbluetoothsleeptracker.core.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.*

/**
 * Result of a playback fade operation.
 * - COMPLETED: fade finished uninterrupted -> proceed with disconnect
 * - CANCELLED_BY_USER: volume key pressed during fade -> skip disconnect, extend timer
 * - DISABLED: playback stop not enabled in settings
 */
enum class FadeResult { COMPLETED, CANCELLED_BY_USER, DISABLED }

/**
 * Handles gradual volume fade-out, audio focus stealing, and media session pausing.
 *
 * During the fade window, monitors VOLUME_CHANGED_ACTION. If the user presses a
 * volume key, the fade is cancelled, prior volume restored, and the caller is
 * signalled to extend the timer instead of disconnecting.
 */
class PlaybackController(private val context: Context) {

    companion object {
        private const val TAG = "PlaybackCtrl"
        private const val FADE_TICK_MS = 200L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile private var priorVolume: Int = -1
    @Volatile private var userCancelled = false
    private var volumeReceiver: BroadcastReceiver? = null
    private val internalVolumeChanges = java.util.Collections.synchronizedList(mutableListOf<Pair<Int, Long>>())

    /**
     * Primary entry point. Gradually fades STREAM_MUSIC volume to 0 over [durationSeconds].
     * Monitors volume keys during fade. Returns the result.
     */
    suspend fun fadeOutAndStop(durationSeconds: Int): FadeResult = withContext(Dispatchers.Main) {
        userCancelled = false
        priorVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        Log.i(TAG, "Starting fadeOutAndStop: duration=${durationSeconds}s, initial STREAM_MUSIC volume=$priorVolume")

        if (priorVolume == 0) {
            Log.i(TAG, "Initial volume is 0, skipping fade. Stealing audio focus and pausing.")
            stealAudioFocusAndPause()
            return@withContext FadeResult.COMPLETED
        }

        // 1. Register volume key listener BEFORE first volume adjustment
        registerVolumeReceiver()

        try {
            val totalSteps = (durationSeconds * 1000L / FADE_TICK_MS).toInt().coerceAtLeast(1)
            val volumeDecrement = priorVolume.toFloat() / totalSteps.toFloat()
            var currentStep = 0
            var lastSetVol = priorVolume

            Log.d(TAG, "Fade parameters: totalSteps=$totalSteps, decrementPerStep=$volumeDecrement")

            while (currentStep < totalSteps) {
                if (userCancelled) {
                    Log.i(TAG, "Fade cancelled by user volume key press! Restoring volume to exact initial level: $priorVolume")
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC, priorVolume,
                        0 // silent
                    )
                    return@withContext FadeResult.CANCELLED_BY_USER
                }

                currentStep++
                val newVol = (priorVolume - (volumeDecrement * currentStep)).toInt().coerceAtLeast(0)
                if (newVol != lastSetVol) {
                    try {
                        internalVolumeChanges.add(Pair(newVol, System.currentTimeMillis()))
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        lastSetVol = newVol
                        Log.d(TAG, "Fade step $currentStep/$totalSteps: set volume to $newVol")
                    } catch (e: Exception) {
                        Log.w(TAG, "setStreamVolume failed: ${e.message}")
                    }
                }
                delay(FADE_TICK_MS)
            }

            if (userCancelled) {
                Log.i(TAG, "Fade cancelled at end of loop by user. Restoring volume to $priorVolume")
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, priorVolume, 0)
                return@withContext FadeResult.CANCELLED_BY_USER
            }

            // Ensure volume is 0
            if (lastSetVol != 0) {
                try {
                    internalVolumeChanges.add(Pair(0, System.currentTimeMillis()))
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "setStreamVolume failed at finish: ${e.message}")
                }
            }
            Log.i(TAG, "Fade completed naturally — STREAM_MUSIC volume reached 0")

            // Steal focus and pause media sessions
            stealAudioFocusAndPause()

            // Restore volume after a short delay so user's next session isn't muted
            val savedPriorVol = priorVolume
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000)
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedPriorVol, 0)
                    Log.i(TAG, "Post-fade: restored STREAM_MUSIC volume to $savedPriorVol after 3s delay")
                } catch (e: Exception) {
                    Log.w(TAG, "Post-fade volume restore failed: ${e.message}")
                }
            }

            return@withContext FadeResult.COMPLETED

        } finally {
            // Unregister receiver immediately when fade completes or is cancelled
            unregisterVolumeReceiver()
            internalVolumeChanges.clear()
            Log.d(TAG, "Unregistered volume receiver and cleared internal change history")
        }
    }

    private fun stealAudioFocusAndPause() {
        try {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { /* no-op */ }
                .build()
            audioManager.requestAudioFocus(focusRequest)
            Log.d(TAG, "Audio focus stolen successfully")
        } catch (e: Exception) {
            Log.w(TAG, "AudioFocus request failed: ${e.message}")
        }

        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))
            Log.d(TAG, "Dispatched MEDIA_PAUSE and MEDIA_STOP key events")
        } catch (e: Exception) {
            Log.w(TAG, "Media key dispatch failed: ${e.message}")
        }
    }

    // ── Volume key detection ───────────────────────────────────────────

    private fun registerVolumeReceiver() {
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC) {
                        val newVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                        val currentVol = if (newVol != -1) newVol else audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        
                        val now = System.currentTimeMillis()
                        // Clean up old entries (older than 2 seconds)
                        internalVolumeChanges.removeAll { now - it.second > 2000L }
                        
                        val isInternal = internalVolumeChanges.any { it.first == currentVol }
                        if (isInternal) {
                            Log.d(TAG, "Volume change event ($currentVol) matched internal fade step — ignoring")
                        } else {
                            Log.i(TAG, "Volume change event ($currentVol) detected from EXTERNAL USER KEY PRESS! Triggering cancellation.")
                            userCancelled = true
                        }
                    }
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            volumeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        Log.i(TAG, "Registered VOLUME_CHANGED_ACTION receiver for fade window")
    }

    private fun unregisterVolumeReceiver() {
        volumeReceiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d(TAG, "Successfully unregistered volumeReceiver")
            } catch (e: Exception) {
                Log.w(TAG, "Unregistering volumeReceiver failed: ${e.message}")
            }
            volumeReceiver = null
        }
    }
}
