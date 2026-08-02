package com.smartbluetoothsleeptracker.core.playback

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.smartbluetoothsleeptracker.service.MediaListenerService
import kotlinx.coroutines.*

/**
 * Result of a playback fade operation.
 * - COMPLETED: fade finished uninterrupted → proceed with disconnect
 * - CANCELLED_BY_USER: volume key pressed during fade → skip disconnect, extend timer
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
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var fadeJob: Job? = null
    @Volatile private var priorVolume: Int = -1
    @Volatile private var userCancelled = false
    private var volumeReceiver: BroadcastReceiver? = null

    /**
     * Primary entry point. Gradually fades STREAM_MUSIC volume to 0 over [durationSeconds].
     * Monitors volume keys during fade. Returns the result.
     */
    suspend fun fadeOutAndStop(durationSeconds: Int): FadeResult = withContext(Dispatchers.Main) {
        userCancelled = false
        priorVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        if (priorVolume == 0) {
            // Already muted — just steal focus and pause
            stealAudioFocusAndPause()
            return@withContext FadeResult.COMPLETED
        }

        // Register volume key listener
        registerVolumeReceiver()

        try {
            val totalSteps = (durationSeconds * 1000L / FADE_TICK_MS).toInt()
            val volumeDecrement = priorVolume.toFloat() / totalSteps.toFloat()
            var currentStep = 0

            while (currentStep < totalSteps) {
                if (userCancelled) {
                    Log.i(TAG, "Fade cancelled by user — restoring volume to $priorVolume")
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC, priorVolume,
                        0 // no flags = silent
                    )
                    return@withContext FadeResult.CANCELLED_BY_USER
                }

                currentStep++
                val newVol = (priorVolume - (volumeDecrement * currentStep)).toInt().coerceAtLeast(0)
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "setStreamVolume failed: ${e.message}")
                }
                delay(FADE_TICK_MS)
            }

            // Ensure volume is 0
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            Log.i(TAG, "Fade completed — volume at 0")

            // Steal focus and pause media sessions
            stealAudioFocusAndPause()

            // Restore volume after a short delay so next playback isn't muted
            handler.postDelayed({
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, priorVolume, 0)
                Log.d(TAG, "Volume restored to $priorVolume")
            }, 3000)

            return@withContext FadeResult.COMPLETED

        } finally {
            unregisterVolumeReceiver()
        }
    }

    /**
     * Steals audio focus (most apps pause on focus loss) and directly pauses
     * all active media sessions if Notification Listener permission is granted.
     */
    private fun stealAudioFocusAndPause() {
        // 1. Audio focus steal
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
            Log.d(TAG, "Audio focus stolen")
        } catch (e: Exception) {
            Log.w(TAG, "AudioFocus request failed: ${e.message}")
        }

        // 2. Send media key PAUSE + STOP
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))
        } catch (e: Exception) {
            Log.w(TAG, "Media key dispatch failed: ${e.message}")
        }

        // 3. MediaSessionManager — pause all active sessions (requires Notification Listener)
        pauseActiveMediaSessions()
    }

    /**
     * Uses MediaSessionManager to fetch and pause all active media sessions.
     * Only works if the user has granted Notification Listener access.
     */
    private fun pauseActiveMediaSessions() {
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return
            val componentName = ComponentName(context, MediaListenerService::class.java)
            val sessions: List<MediaController> = msm.getActiveSessions(componentName)

            for (session in sessions) {
                try {
                    session.transportControls.pause()
                    Log.d(TAG, "Paused media session: ${session.packageName}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to pause ${session.packageName}: ${e.message}")
                }
            }
            Log.i(TAG, "Paused ${sessions.size} active media session(s)")
        } catch (e: SecurityException) {
            Log.d(TAG, "Notification Listener not granted — skipping MediaSession pause")
        } catch (e: Exception) {
            Log.w(TAG, "MediaSession pause error: ${e.message}")
        }
    }

    // ── Volume key detection ───────────────────────────────────────────

    private fun registerVolumeReceiver() {
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                // Any volume change during our fade window = user pressed volume key
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC) {
                        Log.i(TAG, "Volume key detected during fade — cancelling")
                        userCancelled = true
                    }
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    private fun unregisterVolumeReceiver() {
        volumeReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
            volumeReceiver = null
        }
    }
}
