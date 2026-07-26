package com.smartbluetoothsleeptracker.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import com.smartbluetoothsleeptracker.core.analytics.FirebaseAnalyticsHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BlockerState(val active: Boolean = false, val blockedUntil: Long = 0L)

class BluetoothDisconnector(
    private val context: Context,
    private val analytics: FirebaseAnalyticsHelper? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _blockerState = MutableStateFlow(BlockerState())
    val blockerState: StateFlow<BlockerState> = _blockerState.asStateFlow()

    val isBlockerActive: Boolean
        get() = _blockerState.value.active && System.currentTimeMillis() < _blockerState.value.blockedUntil

    /** Disconnect our tracked devices and activate the reconnect blocker. */
    @SuppressLint("MissingPermission")
    fun disconnectAll(devices: List<ConnectedDevice>, blockForMillis: Long = 120_000L) {
        val ad = adapter ?: return
        if (blockForMillis > 0L) {
            _blockerState.value = BlockerState(
                active = true,
                blockedUntil = System.currentTimeMillis() + blockForMillis
            )
        }
        scope.launch {
            // Disconnect our tracked devices via profile proxies
            val bonded = ad.bondedDevices ?: emptySet()
            val targets = bonded.filter { bd -> devices.any { it.address == bd.address } }
            targets.forEach { disconnectDevice(ad, it) }
            // Also sweep ALL currently connected audio devices from the BT stack
            disconnectAllConnectedNow()
        }
    }

    /**
     * Called from BroadcastReceiver when a device reconnects during block period.
     * Runs device.disconnect() synchronously on the caller's thread (fastest),
     * then schedules profile-level cleanup asynchronously.
     */
    @SuppressLint("MissingPermission")
    fun forceDisconnectImmediate(device: BluetoothDevice) {
        // Strategy 1 – ACL teardown (works on Android ≤ 11, some OEM ROMs on 12+)
        runCatching { device.javaClass.getMethod("disconnect").invoke(device) }
        // Strategy 2 – profile-level cleanup (async, catches any lingering streams)
        val ad = adapter ?: return
        scope.launch { disconnectDevice(ad, device) }
    }

    /**
     * Sweeps ALL A2DP + HFP connected devices directly from the Bluetooth stack.
     * Use this for periodic retry — doesn't rely on our in-app tracker.
     */
    @SuppressLint("MissingPermission")
    fun disconnectAllConnectedNow() {
        val ad = adapter ?: return
        disconnectProfile(ad, BluetoothProfile.A2DP)
        disconnectProfile(ad, BluetoothProfile.HEADSET)
    }

    @SuppressLint("MissingPermission")
    private fun disconnectProfile(ad: BluetoothAdapter, profile: Int) {
        ad.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                proxy.connectedDevices.forEach { device ->
                    runCatching {
                        proxy.javaClass
                            .getMethod("disconnect", BluetoothDevice::class.java)
                            .invoke(proxy, device)
                    }
                    // ACL teardown as belt-and-suspenders
                    runCatching { device.javaClass.getMethod("disconnect").invoke(device) }
                }
                ad.closeProfileProxy(p, proxy)
            }
            override fun onServiceDisconnected(p: Int) {}
        }, profile)
    }

    @SuppressLint("MissingPermission")
    private fun disconnectDevice(ad: BluetoothAdapter, device: BluetoothDevice) {
        // Profile proxy disconnects (A2DP + HFP)
        disconnectProfile(ad, BluetoothProfile.A2DP)
        disconnectProfile(ad, BluetoothProfile.HEADSET)
        // ACL teardown fallback
        runCatching {
            device.javaClass.getMethod("disconnect").invoke(device)
        }.onFailure { e ->
            // Fail-safe: log to Crashlytics and retry once via profile proxy
            val name = runCatching { device.name }.getOrNull()
            analytics?.logDisconnectFailure(name, e.message ?: "unknown")
            // Retry: force through profile proxy
            disconnectProfile(ad, BluetoothProfile.A2DP)
            disconnectProfile(ad, BluetoothProfile.HEADSET)
        }
    }

    fun shouldBlockReconnect(): Boolean = isBlockerActive
    fun clearBlocker() { _blockerState.value = BlockerState() }
}
