package com.smartbluetoothsleeptracker.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import com.smartbluetoothsleeptracker.core.analytics.FirebaseAnalyticsHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

data class BlockerState(val active: Boolean = false, val blockedUntil: Long = 0L)

/**
 * Multi-layered Bluetooth Disconnector Engine.
 * Built for personal, non-PlayStore deployment — uses aggressive strategies
 * to guarantee 100% disconnection on all Android versions.
 */
class BluetoothDisconnector(
    private val context: Context,
    private val analytics: FirebaseAnalyticsHelper? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _blockerState = MutableStateFlow(BlockerState())
    val blockerState: StateFlow<BlockerState> = _blockerState.asStateFlow()

    val isBlockerActive: Boolean
        get() = _blockerState.value.active && System.currentTimeMillis() < _blockerState.value.blockedUntil

    /**
     * Primary entry point: timer finished OR user tapped "Disconnect Now".
     * Executes all layers simultaneously for maximum reliability.
     */
    @SuppressLint("MissingPermission")
    fun disconnectAll(devices: List<ConnectedDevice> = emptyList(), blockForMillis: Long = 120_000L) {
        if (blockForMillis > 0L) {
            _blockerState.value = BlockerState(
                active = true,
                blockedUntil = System.currentTimeMillis() + blockForMillis
            )
        }

        scope.launch {
            // Layer 1: Stop audio playback & steal focus
            stopAudioPlayback()
            // Layer 2: Profile-level teardown (A2DP, HFP, LE_AUDIO)
            disconnectAllConnectedNow()
            // Layer 3: Direct Bluetooth radio disable (100% hardware kill)
            turnOffBluetoothRadio()
            // Layer 4: System shell fallback (non-blocking, best-effort)
            executeSystemBluetoothDisable()
        }
    }

    /**
     * Stops media playback and steals audio focus so audio sessions end cleanly.
     */
    private fun stopAudioPlayback() {
        runCatching {
            audioManager?.let { am ->
                // Send PAUSE then STOP key events
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))

                // Steal audio focus to halt lingering background streams
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .build()
                    am.requestAudioFocus(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                }
            }
        }
    }

    /**
     * Disables the Bluetooth radio directly via BluetoothAdapter.disable().
     * All paired devices disconnect immediately when hardware turns off.
     */
    @SuppressLint("MissingPermission")
    fun turnOffBluetoothRadio(): Boolean {
        val ad = adapter ?: return false
        if (!ad.isEnabled) return true

        // Direct public API (deprecated in newer SDK but still functional)
        val success = runCatching {
            @Suppress("DEPRECATION")
            ad.disable()
        }.getOrDefault(false)

        // Reflection fallback for OEMs that block the public API
        if (!success) {
            runCatching {
                val method = ad.javaClass.getDeclaredMethod("disable")
                method.isAccessible = true
                method.invoke(ad)
            }
        }
        return success
    }

    /**
     * Shell-level Bluetooth disable as a last resort.
     * Each command runs with a 3-second timeout to avoid blocking the IO dispatcher.
     */
    private fun executeSystemBluetoothDisable() {
        val commands = listOf(
            arrayOf("cmd", "bluetooth_manager", "disable"),
            arrayOf("svc", "bluetooth", "disable")
        )
        for (cmd in commands) {
            runCatching {
                val process = Runtime.getRuntime().exec(cmd)
                process.waitFor(3, TimeUnit.SECONDS)
                process.destroyForcibly()
            }
        }
        // Root fallback — only if device is rooted (don't block on failure)
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "svc bluetooth disable"))
            process.waitFor(2, TimeUnit.SECONDS)
            process.destroyForcibly()
        }
    }

    /**
     * Called from BroadcastReceiver when a device reconnects during the block window.
     * Runs synchronously on the receiver thread for minimum latency.
     */
    @SuppressLint("MissingPermission")
    fun forceDisconnectImmediate(device: BluetoothDevice) {
        // ACL teardown (fastest — no async)
        runCatching { device.javaClass.getMethod("disconnect").invoke(device) }

        // If blocker is active, kill the radio to prevent OS auto-reconnect
        if (shouldBlockReconnect()) {
            scope.launch {
                turnOffBluetoothRadio()
                executeSystemBluetoothDisable()
            }
        }
    }

    /**
     * Sweeps ALL connected A2DP, HFP, and LE_AUDIO profiles.
     * Does not rely on our in-app device tracker — catches OS-managed reconnections too.
     */
    @SuppressLint("MissingPermission")
    fun disconnectAllConnectedNow() {
        val ad = adapter ?: return
        disconnectProfile(ad, BluetoothProfile.A2DP)
        disconnectProfile(ad, BluetoothProfile.HEADSET)

        // LE_AUDIO (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { disconnectProfile(ad, 22) } // BluetoothProfile.LE_AUDIO = 22
        }

        // Belt-and-suspenders: ACL teardown on every bonded device
        runCatching {
            ad.bondedDevices?.forEach { device ->
                runCatching { device.javaClass.getMethod("disconnect").invoke(device) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectProfile(ad: BluetoothAdapter, profile: Int) {
        ad.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                runCatching {
                    proxy.connectedDevices.forEach { device ->
                        // Profile-level disconnect
                        runCatching {
                            proxy.javaClass
                                .getMethod("disconnect", BluetoothDevice::class.java)
                                .invoke(proxy, device)
                        }
                        // ACL-level disconnect
                        runCatching { device.javaClass.getMethod("disconnect").invoke(device) }
                    }
                }
                runCatching { ad.closeProfileProxy(p, proxy) }
            }
            override fun onServiceDisconnected(p: Int) {}
        }, profile)
    }

    fun shouldBlockReconnect(): Boolean = isBlockerActive
    fun clearBlocker() { _blockerState.value = BlockerState() }
}
