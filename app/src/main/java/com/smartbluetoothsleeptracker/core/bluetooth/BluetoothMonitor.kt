package com.smartbluetoothsleeptracker.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectedDevice(val name: String, val address: String, val startTime: Long)

// MAC-like name patterns produced by BLE random addresses — e.g. "22:BF:04" or "22iop1a3"
private val MAC_PATTERN = Regex("^([0-9A-Fa-f]{2}[:\\-]){2,}[0-9A-Fa-f]{2}$")
private val HEX_SHORT   = Regex("^[0-9A-Fa-f]{4,12}$")

class BluetoothMonitor(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _devices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val devices: StateFlow<List<ConnectedDevice>> = _devices.asStateFlow()

    private val _isEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun onAdapterStateChanged(state: Int) {
        _isEnabled.value = state == BluetoothAdapter.STATE_ON
        if (state != BluetoothAdapter.STATE_ON) _devices.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun onDeviceConnected(device: BluetoothDevice) {
        // Reject non-audio device classes
        if (!isAudioDevice(device)) return
        val name = device.safeName()
        val address = device.address ?: return
        // Reject ghost devices: BLE random addresses shown as hex names or MAC strings
        if (isGhostName(name)) return
        val current = _devices.value.toMutableList()
        if (current.none { it.address == address }) {
            current.add(ConnectedDevice(name = name, address = address, startTime = System.currentTimeMillis()))
            _devices.value = current
        }
    }

    fun onDeviceDisconnected(device: BluetoothDevice): ConnectedDevice? {
        val address = device.address ?: return null
        val current = _devices.value.toMutableList()
        val removed = current.firstOrNull { it.address == address }
        if (removed != null) {
            current.removeAll { it.address == address }
            _devices.value = current
        }
        return removed
    }

    @SuppressLint("MissingPermission")
    fun reconcile() {
        if (bluetoothAdapter?.isEnabled != true) {
            _devices.value = emptyList()
            _isEnabled.value = false
            return
        }
        _isEnabled.value = true
        // Verify every tracked device is still actually connected via audio profile
        runCatching {
            val current = _devices.value
            if (current.isEmpty()) return
            bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val connected = proxy.connectedDevices.map { it.address }.toSet()
                    _devices.value = current.filter { it.address in connected }
                    bluetoothAdapter?.closeProfileProxy(profile, proxy)
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.A2DP)
        }
    }

    fun isAudioPlaying(): Boolean =
        context.getSystemService(AudioManager::class.java)?.isMusicActive == true

    fun hasConnectedDevice(): Boolean = _devices.value.isNotEmpty()
    fun firstDevice(): ConnectedDevice? = _devices.value.firstOrNull()
    fun deviceNames(): List<String> = _devices.value.map { it.name }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String =
        runCatching { name }.getOrNull()?.takeIf { it.isNotBlank() } ?: address ?: "Unknown"

    /** Returns true for names that look like BLE random MAC addresses */
    private fun isGhostName(name: String): Boolean {
        if (name.length < 3) return true
        if (MAC_PATTERN.matches(name)) return true          // "AB:CD:EF:01:23:45"
        if (HEX_SHORT.matches(name)) return true            // "22bf04a1" — pure hex
        if (name == "Unknown" || name.startsWith("00:")) return true
        return false
    }

    @SuppressLint("MissingPermission")
    private fun isAudioDevice(device: BluetoothDevice): Boolean {
        return runCatching {
            val cls = device.bluetoothClass ?: return@runCatching false // reject null-class (BLE advertising)
            val major = cls.majorDeviceClass
            major == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO ||
            major == android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL
        }.getOrDefault(false) // safe default: reject unknown
    }
}
