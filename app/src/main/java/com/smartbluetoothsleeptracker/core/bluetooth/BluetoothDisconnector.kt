package com.smartbluetoothsleeptracker.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
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
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Represents a single disconnect strategy.
 */
data class DisconnectMethod(
    val id: String,
    val displayName: String,
    val execute: suspend (Context, BluetoothDevice) -> Unit
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
 * 3. system_disable_dialog — ACTION_REQUEST_DISABLE intent (always works, not silent)
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
    private val activeEnforcements = java.util.concurrent.ConcurrentHashMap<String, Job>()

    val isCooldownActive: Boolean
        get() = _cooldownState.value.active && System.currentTimeMillis() < _cooldownState.value.expiresAt

    @SuppressLint("MissingPermission")
    suspend fun probeWorkingMethodSilently(device: BluetoothDevice): String? {
        // Return the already-working cached method if present (populated by a real successful disconnect).
        // Do NOT pre-assign a speculative method here — the method IDs must match allMethods() registry exactly.
        return db.deviceDao().getDevice(device.address)?.workingDisconnectMethod
    }

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

    @SuppressLint("MissingPermission")
    suspend fun disconnectSingleDevice(device: BluetoothDevice): DisconnectResult {
        val tried = mutableListOf<Pair<String, Boolean>>()
        val cached = db.deviceDao().getDevice(device.address)?.workingDisconnectMethod

        val methods = buildMethodOrder(cached)

        var finalSuccess = false
        var winningMethodId: String? = null

        for (method in methods) {
            var exceptionMsg: String? = null
            var executionResult = false
            
            try {
                withTimeout(5000) { method.execute(context, device) }
                executionResult = true
            } catch (e: NoSuchMethodException) {
                exceptionMsg = "NoSuchMethodException: ${e.message}"
                Log.w(TAG, "Method ${method.id} failed: $exceptionMsg")
            } catch (e: java.lang.reflect.InvocationTargetException) {
                exceptionMsg = "InvocationTargetException: ${e.cause?.message ?: e.message}"
                Log.w(TAG, "Method ${method.id} failed: $exceptionMsg")
            } catch (e: SecurityException) {
                exceptionMsg = "SecurityException: ${e.message}"
                Log.w(TAG, "Method ${method.id} failed: $exceptionMsg")
            } catch (e: Exception) {
                exceptionMsg = "${e.javaClass.simpleName}: ${e.message}"
                Log.w(TAG, "Method ${method.id} failed: $exceptionMsg")
            }

            var actuallyDisconnected = false
            if (method.id == "system_disable_dialog") {
                // system_disable_dialog forces system dialog, wait for user. Assume success for logic flow.
                actuallyDisconnected = true
            } else if (executionResult) {
                delay(2500L) // Wait for profile connections to drop (MIUI/OneUI can lag up to 2s)
                actuallyDisconnected = !isDeviceActuallyConnected(device)
                if (!actuallyDisconnected) {
                    exceptionMsg = "Silent no-op (device still connected)"
                    Log.w(TAG, "Method ${method.id} failed: $exceptionMsg")
                }
            }

            val success = executionResult && actuallyDisconnected
            tried.add(method.id to success)

            scope.launch {
                db.disconnectAttemptDao().insert(
                    DisconnectAttemptEntity(
                        deviceAddress = device.address,
                        androidVersion = Build.VERSION.SDK_INT,
                        manufacturer = Build.MANUFACTURER,
                        methodId = method.id,
                        succeeded = success,
                        lastTestedAt = System.currentTimeMillis(),
                        errorMessage = exceptionMsg
                    )
                )
                db.disconnectAttemptDao().deleteOldAttempts(device.address, 20)
            }

            if (success) {
                winningMethodId = method.id
                finalSuccess = true
                scope.launch { db.deviceDao().setWorkingMethod(device.address, method.id) }
                break
            }
        }

        return DisconnectResult(finalSuccess, winningMethodId, tried)
    }

    @SuppressLint("MissingPermission")
    suspend fun testAllMethods(device: BluetoothDevice): List<Pair<String, Boolean>> {
        val results = mutableListOf<Pair<String, Boolean>>()
        for (method in allMethods()) {
            var exceptionMsg: String? = null
            val ok = try {
                withTimeout(5000) { method.execute(context, device) }
                delay(1500L)
                if (method.id == "system_disable_dialog") true else !isDeviceActuallyConnected(device)
            } catch (e: Exception) {
                exceptionMsg = "${e.javaClass.simpleName}: ${e.message}"
                false
            }
            results.add(method.id to ok)

            db.disconnectAttemptDao().insert(
                DisconnectAttemptEntity(
                    deviceAddress = device.address,
                    androidVersion = Build.VERSION.SDK_INT,
                    manufacturer = Build.MANUFACTURER,
                    methodId = method.id,
                    succeeded = ok,
                    lastTestedAt = System.currentTimeMillis(),
                    errorMessage = exceptionMsg
                )
            )
            db.disconnectAttemptDao().deleteOldAttempts(device.address, 20)
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
        // Cancel all active enforcement jobs
        val keys = activeEnforcements.keys().toList()
        for (key in keys) {
            activeEnforcements.remove(key)?.cancel()
        }
        Log.i(TAG, "Cooldown ended")
    }

    fun shouldBlockDevice(address: String): Boolean {
        val state = _cooldownState.value
        return state.active
                && System.currentTimeMillis() < state.expiresAt
                && address in state.targetAddresses
    }

    @SuppressLint("MissingPermission")
    fun enforceDisconnect(device: BluetoothDevice) {
        val address = device.address
        // Cancel any existing enforcement job for this device to prevent concurrent loops
        activeEnforcements[address]?.cancel()

        val job = scope.launch {
            Log.i(TAG, "Enforcing re-disconnect on $address")
            for (i in 1..3) {
                if (!shouldBlockDevice(address)) break
                val result = disconnectSingleDevice(device)
                if (result.success) {
                    Log.i(TAG, "Enforce disconnect success on attempt $i for $address")
                } else {
                    Log.w(TAG, "Enforce disconnect failed on attempt $i for $address")
                }
                delay(1500L) // Wait for profile connections before trying again
            }
            activeEnforcements.remove(address)
        }
        activeEnforcements[address] = job
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
            } else throw UnsupportedOperationException("LE Audio requires API 33+")
        },
        DisconnectMethod("acl_reflection", "ACL Direct Disconnect") { _, dev ->
            val method = dev.javaClass.getDeclaredMethod("disconnect")
            method.isAccessible = true
            method.invoke(dev)
        },
        DisconnectMethod("system_disable_dialog", "System Disable Dialog") { ctx, _ ->
            val intent = Intent("android.bluetooth.adapter.action.REQUEST_DISABLE").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }
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
    ) = suspendCoroutine<Unit> { cont ->
        val ad = adapter
        if (ad == null) { cont.resumeWithException(IllegalStateException("No BluetoothAdapter")); return@suspendCoroutine }

        val resumed = java.util.concurrent.atomic.AtomicBoolean(false)

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                var exception: Exception? = null
                try {
                    val method = proxy.javaClass.getDeclaredMethod("disconnect", BluetoothDevice::class.java)
                    method.isAccessible = true
                    method.invoke(proxy, device)
                } catch (e: Exception) {
                    exception = e
                }
                
                runCatching { ad.closeProfileProxy(profile, proxy) }
                
                if (resumed.compareAndSet(false, true)) {
                    if (exception != null) {
                        cont.resumeWithException(exception)
                    } else {
                        cont.resume(Unit)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (resumed.compareAndSet(false, true)) cont.resumeWithException(IllegalStateException("Profile disconnected before execution"))
            }
        }

        val bound = ad.getProfileProxy(ctx, listener, profileType)
        if (!bound && resumed.compareAndSet(false, true)) {
            cont.resumeWithException(IllegalStateException("Failed to bind profile proxy"))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun isDeviceActuallyConnected(device: BluetoothDevice): Boolean {
        val reflectConnected = try {
            val method = device.javaClass.getDeclaredMethod("isConnected")
            method.isAccessible = true
            method.invoke(device) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
        if (reflectConnected) return true

        return isDeviceConnectedViaProfiles(device)
    }

    @SuppressLint("MissingPermission")
    private suspend fun isDeviceConnectedViaProfiles(device: BluetoothDevice): Boolean {
        val a2dpConnected = isDeviceConnectedViaProfileType(BluetoothProfile.A2DP, device)
        val hfpConnected = isDeviceConnectedViaProfileType(BluetoothProfile.HEADSET, device)
        val leAudioConnected = if (Build.VERSION.SDK_INT >= 33) {
            isDeviceConnectedViaProfileType(22 /* BluetoothProfile.LE_AUDIO */, device)
        } else false
        return a2dpConnected || hfpConnected || leAudioConnected
    }

    @SuppressLint("MissingPermission")
    private suspend fun isDeviceConnectedViaProfileType(profileType: Int, device: BluetoothDevice): Boolean = suspendCoroutine { cont ->
        val ad = adapter
        if (ad == null) { cont.resume(false); return@suspendCoroutine }

        val resumed = java.util.concurrent.atomic.AtomicBoolean(false)

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val state = try {
                    proxy.getConnectionState(device)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get connection state for profile $profileType: ${e.message}")
                    BluetoothProfile.STATE_DISCONNECTED
                }
                runCatching { ad.closeProfileProxy(profile, proxy) }
                if (resumed.compareAndSet(false, true)) {
                    cont.resume(state == BluetoothProfile.STATE_CONNECTED)
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (resumed.compareAndSet(false, true)) cont.resume(false)
            }
        }

        val bound = ad.getProfileProxy(context, listener, profileType)
        if (!bound && resumed.compareAndSet(false, true)) {
            cont.resume(false)
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

    @SuppressLint("MissingPermission")
    fun getConnectedAudioDevices(): List<BluetoothDevice> {
        val ad = adapter ?: return emptyList()
        val result = mutableListOf<BluetoothDevice>()

        runCatching {
            val a2dpConn = try { ad.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED } catch (_: Exception) { false }
            val hfpConn = try { ad.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED } catch (_: Exception) { false }
            val leConn = try {
                if (Build.VERSION.SDK_INT >= 33) ad.getProfileConnectionState(22) == BluetoothProfile.STATE_CONNECTED else false
            } catch (_: Exception) { false }

            val anyAudioProfileConn = a2dpConn || hfpConn || leConn

            ad.bondedDevices?.forEach { device ->
                val isConn = try {
                    val method = device.javaClass.getDeclaredMethod("isConnected")
                    method.isAccessible = true
                    method.invoke(device) as? Boolean ?: false
                } catch (_: Exception) {
                    false
                } || (anyAudioProfileConn && (device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO))

                if (isConn) result.add(device)
            }
        }
        return result
    }
}
