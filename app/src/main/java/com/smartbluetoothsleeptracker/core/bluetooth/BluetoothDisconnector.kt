package com.smartbluetoothsleeptracker.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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

    fun destroy() {
        scope.cancel()
        cooldownJob?.cancel()
        activeEnforcements.values.forEach { it.cancel() }
        activeEnforcements.clear()
    }

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
        val allTried = mutableListOf<Pair<String, Boolean>>()
        var anySuccess = false
        var lastMethod: String? = null
        var systemDialogFired = false

        for (device in devices) {
            val result = disconnectSingleDevice(device, allowSystemDialog = !systemDialogFired)
            allTried.addAll(result.methodsTried)
            if (result.methodsTried.any { it.first == "system_disable_dialog" }) {
                systemDialogFired = true
            }
            if (result.success) {
                anySuccess = true
                lastMethod = result.methodUsed
            }
        }

        // Start cooldown enforcement if enabled
        if (enableCooldown && cooldownSeconds > 0) {
            startCooldown(
                durationSeconds = cooldownSeconds,
                targetAddresses = devices.map { it.address }.toSet().ifEmpty { setOf("all") }
            )
        }

        return DisconnectResult(anySuccess, lastMethod, allTried)
    }

    @SuppressLint("MissingPermission")
    suspend fun disconnectSingleDevice(
        device: BluetoothDevice,
        allowSystemDialog: Boolean = true
    ): DisconnectResult {
        val tried = mutableListOf<Pair<String, Boolean>>()
        val cached = db.deviceDao().getDevice(device.address)?.workingDisconnectMethod

        val allAvailableMethods = buildMethodOrder(device.address, cached)
        val methods = if (!allowSystemDialog) {
            allAvailableMethods.filter { it.id != "system_disable_dialog" }
        } else {
            allAvailableMethods
        }

        var finalSuccess = false
        var winningMethodId: String? = null

        for (method in methods) {
            var exceptionMsg: String? = null
            var executionResult = false

            try {
                withTimeout(5000L) { method.execute(context, device) }
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
            } catch (e: TimeoutCancellationException) {
                exceptionMsg = "Timeout after 5000ms"
                Log.w(TAG, "Method ${method.id} timed out")
            } catch (e: Exception) {
                exceptionMsg = "${e.javaClass.simpleName}: ${e.message}"
                Log.w(TAG, "Method ${method.id} failed: $exceptionMsg")
            }

            var actuallyDisconnected = false
            if (method.id == "system_disable_dialog") {
                // Wait briefly for user interaction, then verify honestly whether Bluetooth or device disconnected
                delay(3000L)
                actuallyDisconnected = !isDeviceActuallyConnected(device) || adapter?.isEnabled == false
                if (!actuallyDisconnected) {
                    exceptionMsg = "System dialog dismissed or Bluetooth remained enabled"
                    Log.w(TAG, "Method ${method.id} verification failed: $exceptionMsg")
                }
            } else if (executionResult) {
                // Poll for disconnection up to 3 seconds to accommodate OEM stack latency
                for (poll in 1..6) {
                    delay(500L)
                    actuallyDisconnected = !isDeviceActuallyConnected(device)
                    if (actuallyDisconnected) break
                }
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
                withTimeout(5000L) { method.execute(context, device) }
                delay(1500L)
                if (method.id == "system_disable_dialog") {
                    !isDeviceActuallyConnected(device) || adapter?.isEnabled == false
                } else {
                    !isDeviceActuallyConnected(device)
                }
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

        // 1. Trigger heads-up popup notification
        val devName = try { device.name ?: address } catch (_: Exception) { address }
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.notify(
                1002, // NOTIF_RECONNECT_BLOCKED
                com.smartbluetoothsleeptracker.core.notification.AppNotifications.reconnectBlockedAlertNotification(context, devName).build()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show reconnect blocked notification: ${e.message}")
        }

        // 2. Show Toast popup on main thread
        scope.launch(Dispatchers.Main) {
            try {
                android.widget.Toast.makeText(
                    context,
                    "Reconnect Blocked: $devName",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {}
        }

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
            // Also attempt HFP and direct device disconnect so device completely drops link
            runCatching { disconnectViaProfile(ctx, BluetoothProfile.HEADSET, dev) }
            runCatching {
                val m = dev.javaClass.getDeclaredMethod("disconnect")
                m.isAccessible = true
                m.invoke(dev)
            }
        },
        DisconnectMethod("hfp_reflection", "HFP Reflection") { ctx, dev ->
            disconnectViaProfile(ctx, BluetoothProfile.HEADSET, dev)
            runCatching { disconnectViaProfile(ctx, BluetoothProfile.A2DP, dev) }
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

    private suspend fun buildMethodOrder(deviceAddress: String, cachedMethod: String?): List<DisconnectMethod> {
        val all = allMethods()
        val recentAttempts = runCatching {
            db.disconnectAttemptDao().getAttemptsForDeviceNow(deviceAddress).take(6)
        }.getOrDefault(emptyList())

        return MethodOrderResolver.resolveOrder(
            allMethods = all,
            getMethodId = { it.id },
            recentAttempts = recentAttempts,
            cachedMethodId = cachedMethod
        )
    }

    // ── Disconnect Implementations ─────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun disconnectViaProfile(
        ctx: Context,
        profileType: Int,
        device: BluetoothDevice
    ) = suspendCancellableCoroutine<Unit> { cont ->
        val ad = adapter
        if (ad == null) {
            if (cont.isActive) cont.resumeWithException(IllegalStateException("No BluetoothAdapter"))
            return@suspendCancellableCoroutine
        }

        var activeProxy: BluetoothProfile? = null
        val resumed = java.util.concurrent.atomic.AtomicBoolean(false)

        cont.invokeOnCancellation {
            activeProxy?.let { proxy ->
                runCatching { ad.closeProfileProxy(profileType, proxy) }
            }
        }

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                activeProxy = proxy
                var exception: Exception? = null
                try {
                    val method = proxy.javaClass.getDeclaredMethod("disconnect", BluetoothDevice::class.java)
                    method.isAccessible = true
                    method.invoke(proxy, device)
                } catch (e: Exception) {
                    exception = e
                }

                scope.launch {
                    delay(250L) // Give Binder IPC transaction time to dispatch
                    runCatching { ad.closeProfileProxy(profile, proxy) }
                }
                activeProxy = null

                if (resumed.compareAndSet(false, true) && cont.isActive) {
                    if (exception != null) {
                        cont.resumeWithException(exception)
                    } else {
                        cont.resume(Unit)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                activeProxy = null
                if (resumed.compareAndSet(false, true) && cont.isActive) {
                    cont.resumeWithException(IllegalStateException("Profile disconnected before execution"))
                }
            }
        }

        val bound = runCatching { ad.getProfileProxy(ctx, listener, profileType) }.getOrDefault(false)
        if (!bound && resumed.compareAndSet(false, true) && cont.isActive) {
            cont.resumeWithException(IllegalStateException("Failed to bind profile proxy"))
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun isDeviceActuallyConnected(device: BluetoothDevice): Boolean {
        val ad = adapter ?: return false
        if (!ad.isEnabled) return false

        // 1. Audio Manager check: find active Bluetooth audio outputs
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val audioDevices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
        val btAudioDevices = audioDevices.filter {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= 31 && (
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            ))
        }

        // 2. Adapter profile connection states
        val a2dpState = runCatching { ad.getProfileConnectionState(BluetoothProfile.A2DP) }.getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
        val hfpState = runCatching { ad.getProfileConnectionState(BluetoothProfile.HEADSET) }.getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
        val leState = if (Build.VERSION.SDK_INT >= 33) {
            runCatching { ad.getProfileConnectionState(22) }.getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
        } else BluetoothProfile.STATE_DISCONNECTED

        val anyAudioProfileActive = a2dpState == BluetoothProfile.STATE_CONNECTED ||
                hfpState == BluetoothProfile.STATE_CONNECTED ||
                leState == BluetoothProfile.STATE_CONNECTED

        // If no Bluetooth audio routing and no audio profiles active, device is NOT connected
        if (btAudioDevices.isEmpty() && !anyAudioProfileActive) {
            return false
        }

        // 3. Check profile query specifically for THIS device
        val isProfileConnected = withTimeoutOrNull(1500L) {
            isDeviceConnectedViaProfiles(device)
        }
        if (isProfileConnected == true) return true

        // 4. Check if active AudioManager output matches this device
        val matchesAudioOutput = btAudioDevices.any { info ->
            info.address.equals(device.address, ignoreCase = true) ||
            (device.name != null && info.productName.toString().equals(device.name, ignoreCase = true))
        }
        if (matchesAudioOutput) return true

        // 5. Only if an audio profile is globally active on the adapter, verify with reflection
        if (anyAudioProfileActive) {
            val reflectConnected = runCatching {
                val method = device.javaClass.getDeclaredMethod("isConnected")
                method.isAccessible = true
                method.invoke(device) as? Boolean ?: false
            }.getOrDefault(false)
            if (reflectConnected) return true
        }

        return false
    }

    @SuppressLint("MissingPermission")
    private suspend fun isDeviceConnectedViaProfiles(device: BluetoothDevice): Boolean = coroutineScope {
        val a2dpDeferred = async { isDeviceConnectedViaProfileType(BluetoothProfile.A2DP, device) }
        val hfpDeferred = async { isDeviceConnectedViaProfileType(BluetoothProfile.HEADSET, device) }
        val leAudioDeferred = if (Build.VERSION.SDK_INT >= 33) {
            async { isDeviceConnectedViaProfileType(22 /* BluetoothProfile.LE_AUDIO */, device) }
        } else null
        a2dpDeferred.await() || hfpDeferred.await() || (leAudioDeferred?.await() == true)
    }

    @SuppressLint("MissingPermission")
    private suspend fun isDeviceConnectedViaProfileType(profileType: Int, device: BluetoothDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            val ad = adapter
            if (ad == null) {
                if (cont.isActive) cont.resume(false)
                return@suspendCancellableCoroutine
            }

            var activeProxy: BluetoothProfile? = null
            val resumed = java.util.concurrent.atomic.AtomicBoolean(false)

            cont.invokeOnCancellation {
                activeProxy?.let { proxy ->
                    runCatching { ad.closeProfileProxy(profileType, proxy) }
                }
            }

            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    activeProxy = proxy
                    val state = try {
                        proxy.getConnectionState(device)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get connection state for profile $profileType: ${e.message}")
                        BluetoothProfile.STATE_DISCONNECTED
                    }
                    runCatching { ad.closeProfileProxy(profile, proxy) }
                    activeProxy = null
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resume(state == BluetoothProfile.STATE_CONNECTED)
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    activeProxy = null
                    if (resumed.compareAndSet(false, true) && cont.isActive) cont.resume(false)
                }
            }

            val bound = runCatching { ad.getProfileProxy(context, listener, profileType) }.getOrDefault(false)
            if (!bound && resumed.compareAndSet(false, true) && cont.isActive) {
                cont.resume(false)
            }
        }

    // ── Utility ────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun getConnectedAudioDevices(): List<BluetoothDevice> {
        val ad = adapter ?: return emptyList()
        val result = mutableListOf<BluetoothDevice>()

        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val audioDevices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
            val audioDeviceAddresses = audioDevices.mapNotNull { it.address.takeIf { addr -> addr.isNotBlank() } }
            val audioDeviceNames = audioDevices.map { it.productName.toString().trim() }.filter { it.isNotBlank() }

            val a2dpConn = try { ad.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED } catch (_: Exception) { false }
            val hfpConn = try { ad.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED } catch (_: Exception) { false }
            val anyAudioConn = a2dpConn || hfpConn

            ad.bondedDevices?.forEach { device ->
                val devAddr = device.address
                val devName = device.name ?: ""

                val matchesAudioOutput = audioDeviceAddresses.contains(devAddr) ||
                        (devName.isNotBlank() && audioDeviceNames.any { it.equals(devName, ignoreCase = true) })

                val isReflectConnected = runCatching {
                    val method = device.javaClass.getDeclaredMethod("isConnected")
                    method.isAccessible = true
                    method.invoke(device) as? Boolean ?: false
                }.getOrDefault(false)

                // Only consider connected if AudioManager has it OR reflection confirmed while an audio profile is actively connected
                val isConn = matchesAudioOutput || (isReflectConnected && anyAudioConn && device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO)

                if (isConn && !result.any { it.address == devAddr }) {
                    result.add(device)
                }
            }
        }
        return result
    }
}
