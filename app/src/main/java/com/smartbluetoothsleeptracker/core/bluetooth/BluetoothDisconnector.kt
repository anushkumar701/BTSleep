package com.smartbluetoothsleeptracker.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import com.smartbluetoothsleeptracker.data.db.AppDatabase
import com.smartbluetoothsleeptracker.data.db.DisconnectAttemptEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Represents a single disconnect strategy.
 */
data class DisconnectMethod(
    val id: String,
    val displayName: String,
    val execute: suspend (Context, BluetoothDevice) -> Boolean
)

data class CooldownState(
    val active: Boolean = false,
    val expiresAt: Long = 0L,
    val targetAddresses: Set<String> = emptySet()
)

data class DisconnectResult(
    val success: Boolean,
    val methodUsed: String?,
    val methodsTried: List<Pair<String, Boolean>> // methodId -> success
)

/**
 * Self-learning Bluetooth disconnection engine with method registry.
 *
 * Strategy order:
 * 1. a2dp_reflection — reflection on hidden BluetoothA2dp.disconnect()
 * 2. hfp_reflection — reflection on hidden BluetoothHeadset.disconnect()
 * 3. shizuku_privileged — Shizuku-based privileged control (if enabled)
 * 4. system_dialog — ACTION_REQUEST_DISABLE intent (always works, not silent)
 */
class BluetoothDisconnector(
    private val context: Context,
    private val db: AppDatabase
) {
    companion object {
        private const val TAG = "BtDisconnector"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _cooldownState = MutableStateFlow(CooldownState())
    val cooldownState: StateFlow<CooldownState> = _cooldownState.asStateFlow()

    private var cooldownJob: Job? = null

    val isCooldownActive: Boolean
        get() = _cooldownState.value.active && System.currentTimeMillis() < _cooldownState.value.expiresAt

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Primary disconnect: try the best method for each target device, log results.
     * Returns aggregate result.
     */
    @SuppressLint("MissingPermission")
    suspend fun disconnectDevices(
        devices: List<BluetoothDevice>,
        cooldownSeconds: Int = 30,
        enableCooldown: Boolean = true
    ): DisconnectResult {
        // Stop media first
        stopAudioPlayback()

        val allTried = mutableListOf<Pair<String, Boolean>>()
        var anySuccess = false
        var lastMethod: String? = null

        for (device in devices) {
            val result = disconnectSingleDevice(device)
            allTried.addAll(result.methodsTried)
            if (result.success) {
                anySuccess = true
                lastMethod = result.methodUsed
            }
        }

        // Start cooldown enforcement if enabled
        if (enableCooldown && cooldownSeconds > 0 && anySuccess) {
            startCooldown(
                durationSeconds = cooldownSeconds,
                targetAddresses = devices.map { it.address }.toSet()
            )
        }

        return DisconnectResult(anySuccess, lastMethod, allTried)
    }

    /**
     * Disconnect a single device, trying methods in priority order.
     * Self-learning: checks cached working method first.
     */
    @SuppressLint("MissingPermission")
    suspend fun disconnectSingleDevice(device: BluetoothDevice): DisconnectResult {
        val tried = mutableListOf<Pair<String, Boolean>>()
        val cached = db.deviceDao().getDevice(device.address)?.workingDisconnectMethod

        // Build ordered method list: cached first if available
        val methods = buildMethodOrder(cached)

        for (method in methods) {
            val success = try {
                withTimeout(5000) { method.execute(context, device) }
            } catch (e: Exception) {
                Log.w(TAG, "Method ${method.id} threw: ${e.message}")
                false
            }

            tried.add(method.id to success)

            // Log to DB
            scope.launch {
                db.disconnectAttemptDao().insert(
                    DisconnectAttemptEntity(
                        deviceAddress = device.address,
                        androidVersion = Build.VERSION.SDK_INT,
                        manufacturer = Build.MANUFACTURER,
                        methodId = method.id,
                        succeeded = success,
                        lastTestedAt = System.currentTimeMillis()
                    )
                )
            }

            if (success) {
                // Cache the winning method
                scope.launch { db.deviceDao().setWorkingMethod(device.address, method.id) }
                return DisconnectResult(true, method.id, tried)
            }
        }

        return DisconnectResult(false, null, tried)
    }

    /**
     * Manual test: run all methods on a device and report what works.
     */
    @SuppressLint("MissingPermission")
    suspend fun testAllMethods(device: BluetoothDevice): List<Pair<String, Boolean>> {
        val results = mutableListOf<Pair<String, Boolean>>()
        for (method in allMethods()) {
            val ok = try {
                withTimeout(5000) { method.execute(context, device) }
            } catch (_: Exception) { false }
            results.add(method.id to ok)

            db.disconnectAttemptDao().insert(
                DisconnectAttemptEntity(
                    deviceAddress = device.address,
                    androidVersion = Build.VERSION.SDK_INT,
                    manufacturer = Build.MANUFACTURER,
                    methodId = method.id,
                    succeeded = ok,
                    lastTestedAt = System.currentTimeMillis()
                )
            )
        }
        return results
    }

    // ── Cooldown Enforcement ───────────────────────────────────────────────

    fun startCooldown(durationSeconds: Int, targetAddresses: Set<String>) {
        val expiresAt = System.currentTimeMillis() + durationSeconds * 1000L
        _cooldownState.value = CooldownState(true, expiresAt, targetAddresses)

        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            delay(durationSeconds * 1000L)
            endCooldown()
        }
        Log.i(TAG, "Cooldown started: ${durationSeconds}s for ${targetAddresses.size} devices")
    }

    fun endCooldown() {
        cooldownJob?.cancel()
        _cooldownState.value = CooldownState()
        Log.i(TAG, "Cooldown ended")
    }

    fun shouldBlockDevice(address: String): Boolean {
        val state = _cooldownState.value
        return state.active
                && System.currentTimeMillis() < state.expiresAt
                && address in state.targetAddresses
    }

    /**
     * Called from BroadcastReceiver when a device reconnects during cooldown.
     */
    @SuppressLint("MissingPermission")
    fun enforceDisconnect(device: BluetoothDevice) {
        scope.launch {
            Log.i(TAG, "Enforcing re-disconnect on ${device.address}")
            disconnectSingleDevice(device)
        }
    }

    // ── Method Registry ────────────────────────────────────────────────────

    private fun allMethods(): List<DisconnectMethod> = listOf(
        DisconnectMethod("a2dp_reflection", "A2DP Reflection") { ctx, dev ->
            disconnectViaProfile(ctx, BluetoothProfile.A2DP, dev)
        },
        DisconnectMethod("hfp_reflection", "HFP Reflection") { ctx, dev ->
            disconnectViaProfile(ctx, BluetoothProfile.HEADSET, dev)
        },
        DisconnectMethod("le_audio_reflection", "LE Audio Reflection") { ctx, dev ->
            if (Build.VERSION.SDK_INT >= 33) {
                disconnectViaProfile(ctx, 22, dev) // BluetoothProfile.LE_AUDIO = 22
            } else false
        },
        DisconnectMethod("acl_reflection", "ACL Direct Disconnect") { _, dev ->
            disconnectViaAcl(dev)
        },
        // Shizuku omitted from core list — added dynamically when enabled
    )

    private fun buildMethodOrder(cachedMethod: String?): List<DisconnectMethod> {
        val all = allMethods()
        if (cachedMethod == null) return all
        val cached = all.find { it.id == cachedMethod } ?: return all
        return listOf(cached) + all.filter { it.id != cachedMethod }
    }

    // ── Disconnect Implementations ─────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun disconnectViaProfile(
        ctx: Context,
        profileType: Int,
        device: BluetoothDevice
    ): Boolean = suspendCoroutine { cont ->
        val ad = adapter
        if (ad == null) { cont.resume(false); return@suspendCoroutine }

        val resumed = java.util.concurrent.atomic.AtomicBoolean(false)

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val success = try {
                    val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                    method.invoke(proxy, device)
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Profile $profileType reflection failed: ${e.message}")
                    false
                }
                runCatching { ad.closeProfileProxy(profile, proxy) }
                if (resumed.compareAndSet(false, true)) cont.resume(success)
            }

            override fun onServiceDisconnected(profile: Int) {
                if (resumed.compareAndSet(false, true)) cont.resume(false)
            }
        }

        val bound = ad.getProfileProxy(ctx, listener, profileType)
        if (!bound && resumed.compareAndSet(false, true)) {
            cont.resume(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectViaAcl(device: BluetoothDevice): Boolean {
        return try {
            device.javaClass.getMethod("disconnect").invoke(device)
            true
        } catch (e: Exception) {
            Log.w(TAG, "ACL disconnect failed: ${e.message}")
            false
        }
    }

    // ── Audio Playback Stop ────────────────────────────────────────────────

    private fun stopAudioPlayback() {
        runCatching {
            audioManager?.let { am ->
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))

                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
                am.requestAudioFocus(focusRequest)
            }
        }
    }

    // ── Utility ────────────────────────────────────────────────────────────

    /**
     * Returns all currently connected audio devices (A2DP).
     */
    @SuppressLint("MissingPermission")
    fun getConnectedAudioDevices(): List<BluetoothDevice> {
        val ad = adapter ?: return emptyList()
        val result = mutableListOf<BluetoothDevice>()

        // Check bonded devices connection state
        runCatching {
            ad.bondedDevices?.forEach { device ->
                val connected = try {
                    device.javaClass.getMethod("isConnected").invoke(device) as? Boolean ?: false
                } catch (_: Exception) { false }
                if (connected) result.add(device)
            }
        }
        return result
    }
}
