package com.smartbluetoothsleeptracker.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.smartbluetoothsleeptracker.data.db.AppDatabase
import com.smartbluetoothsleeptracker.data.db.DeviceEntity
import com.smartbluetoothsleeptracker.data.db.DeviceType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ConnectedDevice(
    val address: String,
    val name: String,
    val isFavorite: Boolean = false,
    val type: DeviceType = DeviceType.OTHER,
    val device: BluetoothDevice? = null
)

/**
 * Monitors Bluetooth connection state changes, maintains connected device list,
 * and auto-registers new devices in the Room database.
 */
class BluetoothMonitor(
    private val context: Context,
    private val db: AppDatabase
) {
    companion object {
        private const val TAG = "BtMonitor"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedDevice>> = _connectedDevices.asStateFlow()

    private val _btEnabled = MutableStateFlow(adapter?.isEnabled ?: false)
    val btEnabled: StateFlow<Boolean> = _btEnabled.asStateFlow()

    // Callback for external listeners (e.g. cooldown enforcer)
    var onDeviceConnected: ((BluetoothDevice) -> Unit)? = null
    var onDeviceDisconnected: ((BluetoothDevice) -> Unit)? = null

    private val aclConnectedAddresses = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            val device = try {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        ?: @Suppress("DEPRECATION") intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                }
            } catch (_: Exception) {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            }

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (device == null) return
                    aclConnectedAddresses.add(device.address)
                    Log.i(TAG, "ACL connected: ${device.name ?: device.address}")
                    scope.launch {
                        registerDevice(device)
                        refreshConnectedDevices()
                    }
                    onDeviceConnected?.invoke(device)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (device == null) return
                    aclConnectedAddresses.remove(device.address)
                    Log.i(TAG, "ACL disconnected: ${device.name ?: device.address}")
                    scope.launch { refreshConnectedDevices() }
                    onDeviceDisconnected?.invoke(device)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _btEnabled.value = state == BluetoothAdapter.STATE_ON
                    if (state == BluetoothAdapter.STATE_OFF) {
                        aclConnectedAddresses.clear()
                        _connectedDevices.value = emptyList()
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        scope.launch { refreshConnectedDevices() }
                    }
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        _btEnabled.value = adapter?.isEnabled ?: false
        scope.launch { refreshConnectedDevices() }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    @SuppressLint("MissingPermission")
    suspend fun refreshConnectedDevices() {
        val ad = adapter ?: return
        if (!ad.isEnabled) { _connectedDevices.value = emptyList(); return }

        val connected = mutableListOf<ConnectedDevice>()

        runCatching {
            val bonded = ad.bondedDevices ?: emptySet()
            val a2dpConn = try { ad.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED } catch (_: Exception) { false }
            val hfpConn = try { ad.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED } catch (_: Exception) { false }
            val leConn = try {
                if (android.os.Build.VERSION.SDK_INT >= 33) ad.getProfileConnectionState(22) == BluetoothProfile.STATE_CONNECTED else false
            } catch (_: Exception) { false }

            val anyAudioProfileConn = a2dpConn || hfpConn || leConn

            bonded.forEach { device ->
                val reflectConn = try {
                    val method = device.javaClass.getMethod("isConnected")
                    method.isAccessible = true
                    method.invoke(device) as? Boolean ?: false
                } catch (_: Exception) { false }

                val aclConn = aclConnectedAddresses.contains(device.address)

                // On Android 13+, non-SDK reflection may fail. Combine ACL state, reflection, and active profile state.
                val isConn = aclConn || reflectConn || (anyAudioProfileConn && (device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO))

                if (isConn) {
                    com.smartbluetoothsleeptracker.receiver.BluetoothReceiver.setActiveConnectTime(
                        context, device.address, System.currentTimeMillis()
                    )
                    val dbDevice = db.deviceDao().getDevice(device.address)
                    connected.add(
                        ConnectedDevice(
                            address = device.address,
                            name = device.name ?: device.address,
                            isFavorite = dbDevice?.isFavorite ?: false,
                            type = dbDevice?.deviceType ?: inferDeviceType(device),
                            device = device
                        )
                    )
                }
            }
        }

        _connectedDevices.value = connected
    }

    @SuppressLint("MissingPermission")
    private suspend fun registerDevice(device: BluetoothDevice) {
        val existing = db.deviceDao().getDevice(device.address)
        if (existing == null) {
            db.deviceDao().upsert(
                DeviceEntity(
                    address = device.address,
                    name = device.name ?: device.address,
                    deviceType = inferDeviceType(device),
                    lastConnectedAt = System.currentTimeMillis()
                )
            )
        } else {
            db.deviceDao().updateLastConnected(device.address, System.currentTimeMillis())
        }
    }

    @SuppressLint("MissingPermission")
    private fun inferDeviceType(device: BluetoothDevice): DeviceType {
        val major = device.bluetoothClass?.majorDeviceClass ?: return DeviceType.OTHER
        return when (major) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                val name = (device.name ?: "").lowercase()
                when {
                    name.contains("buds") || name.contains("pod") || name.contains("ear") -> DeviceType.EARBUDS
                    name.contains("neck") || name.contains("band") -> DeviceType.NECKBAND
                    name.contains("speaker") || name.contains("soundbar") || name.contains("theatre") -> DeviceType.HOME_THEATRE
                    else -> DeviceType.EARBUDS // Default audio devices to earbuds
                }
            }
            BluetoothClass.Device.Major.COMPUTER -> DeviceType.PC
            BluetoothClass.Device.Major.WEARABLE -> DeviceType.SMARTWATCH
            else -> DeviceType.OTHER
        }
    }
}
