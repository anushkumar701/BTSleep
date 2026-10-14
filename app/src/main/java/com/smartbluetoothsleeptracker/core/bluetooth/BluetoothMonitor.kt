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
import kotlin.coroutines.resume

data class BatteryInfo(
    val mainLevel: Int = -1,    // 0..100, -1 = unknown
    val leftLevel: Int = -1,    // 0..100, -1 = unknown
    val rightLevel: Int = -1,   // 0..100, -1 = unknown
    val caseLevel: Int = -1     // 0..100, -1 = unknown
) {
    val isKnown: Boolean
        get() = (mainLevel in 0..100) || (leftLevel in 0..100) || (rightLevel in 0..100) || (caseLevel in 0..100)
}

data class ConnectedDevice(
    val address: String,
    val name: String,
    val isFavorite: Boolean = false,
    val type: DeviceType = DeviceType.OTHER,
    val device: BluetoothDevice? = null,
    val battery: BatteryInfo? = null
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
    private val deviceBatteryCache = java.util.concurrent.ConcurrentHashMap<String, BatteryInfo>()

    private var a2dpProfile: BluetoothProfile? = null
    private var headsetProfile: BluetoothProfile? = null

    private val profileServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = proxy
                BluetoothProfile.HEADSET -> headsetProfile = proxy
            }
            scope.launch { refreshConnectedDevices() }
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = null
                BluetoothProfile.HEADSET -> headsetProfile = null
            }
            scope.launch { refreshConnectedDevices() }
        }
    }

    private fun parseBatteryBytes(bytes: ByteArray?): Int? {
        if (bytes == null || bytes.isEmpty()) return null
        val str = String(bytes, Charsets.UTF_8).trim()
        val numStr = str.split(",", ";", " ", "%", "\u0000")[0].trim()
        val parsedInt = numStr.toIntOrNull()
        if (parsedInt != null && parsedInt in 0..100) return parsedInt
        val firstByte = bytes[0].toInt() and 0xFF
        if (firstByte in 0..100 && (firstByte < 48 || firstByte > 57 || bytes.size == 1)) {
            return firstByte
        }
        return null
    }

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
                    if (device != null) {
                        aclConnectedAddresses.add(device.address)
                        Log.i(TAG, "ACL connected: ${device.name ?: device.address}")
                        scope.launch {
                            registerDevice(device)
                            refreshConnectedDevices()
                        }
                        onDeviceConnected?.invoke(device)
                    } else {
                        scope.launch { refreshConnectedDevices() }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (device != null) {
                        aclConnectedAddresses.remove(device.address)
                        deviceBatteryCache.remove(device.address)
                        Log.i(TAG, "ACL disconnected: ${device.name ?: device.address}")
                        scope.launch { refreshConnectedDevices() }
                        onDeviceDisconnected?.invoke(device)
                    } else {
                        scope.launch { refreshConnectedDevices() }
                    }
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    if (device != null) {
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            aclConnectedAddresses.add(device.address)
                            Log.i(TAG, "Profile connected: ${device.name ?: device.address}")
                            scope.launch {
                                registerDevice(device)
                                refreshConnectedDevices()
                            }
                            onDeviceConnected?.invoke(device)
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            aclConnectedAddresses.remove(device.address)
                            Log.i(TAG, "Profile disconnected: ${device.name ?: device.address}")
                            scope.launch { refreshConnectedDevices() }
                            onDeviceDisconnected?.invoke(device)
                        } else {
                            scope.launch { refreshConnectedDevices() }
                        }
                    } else {
                        scope.launch { refreshConnectedDevices() }
                    }
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    scope.launch { refreshConnectedDevices() }
                }
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED",
                "com.android.bluetooth.btservice.action.BATTERY_LEVEL_CHANGED" -> {
                    val lvl = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    val caseLvl = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL_CASE", -1).let {
                        if (it in 0..100) it else intent.getIntExtra("case_battery", -1)
                    }.let {
                        if (it in 0..100) it else intent.getIntExtra("case_level", -1)
                    }
                    if (device != null) {
                        val current = deviceBatteryCache[device.address] ?: BatteryInfo()
                        val updated = current.copy(
                            mainLevel = if (lvl in 0..100) lvl else current.mainLevel,
                            caseLevel = if (caseLvl in 0..100) caseLvl else current.caseLevel
                        )
                        deviceBatteryCache[device.address] = updated
                        if (caseLvl in 0..100) {
                            val prefs = context.getSharedPreferences("device_battery_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putInt("case_${device.address}", caseLvl).apply()
                        }
                    }
                    scope.launch { refreshConnectedDevices() }
                }
                "com.google.android.gms.nearby.fastpair.ACTION_STATUS_CHANGED",
                "com.samsung.accessory.intent.action.BATTERY_UPDATE" -> {
                    val caseLvl = intent.getIntExtra("battery_case", -1).let {
                        if (it in 0..100) it else intent.getIntExtra("case_battery", -1)
                    }.let {
                        if (it in 0..100) it else intent.getIntExtra("com.samsung.accessory.extra.CASE_BATTERY", -1)
                    }
                    val leftLvl = intent.getIntExtra("battery_left", -1)
                    val rightLvl = intent.getIntExtra("battery_right", -1)
                    if (device != null) {
                        val current = deviceBatteryCache[device.address] ?: BatteryInfo()
                        val updated = current.copy(
                            caseLevel = if (caseLvl in 0..100) caseLvl else current.caseLevel,
                            leftLevel = if (leftLvl in 0..100) leftLvl else current.leftLevel,
                            rightLevel = if (rightLvl in 0..100) rightLvl else current.rightLevel
                        )
                        deviceBatteryCache[device.address] = updated
                        if (caseLvl in 0..100) {
                            val prefs = context.getSharedPreferences("device_battery_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putInt("case_${device.address}", caseLvl).apply()
                        }
                    }
                    scope.launch { refreshConnectedDevices() }
                }
                "android.bluetooth.device.action.METADATA_CHANGED" -> {
                    if (device != null) {
                        val key = intent.getIntExtra("android.bluetooth.device.extra.KEY", -1)
                        val value = intent.getByteArrayExtra("android.bluetooth.device.extra.VALUE")
                        if (key != -1 && value != null) {
                            val parsed = parseBatteryBytes(value)
                            if (parsed != null && parsed in 0..100) {
                                val current = deviceBatteryCache[device.address] ?: BatteryInfo()
                                val updated = when (key) {
                                    10 -> current.copy(leftLevel = parsed)
                                    11 -> current.copy(rightLevel = parsed)
                                    12, 19 -> current.copy(caseLevel = parsed)
                                    18 -> current.copy(mainLevel = parsed)
                                    else -> current
                                }
                                deviceBatteryCache[device.address] = updated
                                if (key == 12 || key == 19) {
                                    val prefs = context.getSharedPreferences("device_battery_prefs", Context.MODE_PRIVATE)
                                    prefs.edit().putInt("case_${device.address}", parsed).apply()
                                }
                            }
                        }
                    }
                    scope.launch { refreshConnectedDevices() }
                }
                BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT -> {
                    val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD)
                    if (cmd == "+IPHONEACCEV" && device != null) {
                        @Suppress("DEPRECATION")
                        val args = intent.getSerializableExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS) as? Array<*>
                        if (args != null && args.isNotEmpty()) {
                            runCatching {
                                var i = 1
                                var current = deviceBatteryCache[device.address] ?: BatteryInfo()
                                while (i < args.size) {
                                    val k = (args[i] as? Number)?.toInt() ?: (args[i] as? String)?.toIntOrNull()
                                    val v = if (i + 1 < args.size) (args[i + 1] as? Number)?.toInt() ?: (args[i + 1] as? String)?.toIntOrNull() else null
                                    if (k != null && v != null) {
                                        val pct = if (v in 0..9) (v + 1) * 10 else v.coerceIn(0, 100)
                                        when (k) {
                                            1 -> current = current.copy(mainLevel = pct)
                                            2, 3 -> {
                                                current = current.copy(caseLevel = pct)
                                                val prefs = context.getSharedPreferences("device_battery_prefs", Context.MODE_PRIVATE)
                                                prefs.edit().putInt("case_${device.address}", pct).apply()
                                            }
                                        }
                                    }
                                    i += 2
                                }
                                deviceBatteryCache[device.address] = current
                            }
                        }
                    }
                    scope.launch { refreshConnectedDevices() }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _btEnabled.value = state == BluetoothAdapter.STATE_ON
                    if (state == BluetoothAdapter.STATE_OFF) {
                        aclConnectedAddresses.clear()
                        deviceBatteryCache.clear()
                        a2dpProfile = null
                        headsetProfile = null
                        scope.launch { refreshConnectedDevices() }
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        runCatching {
                            adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.A2DP)
                            adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.HEADSET)
                        }
                        scope.launch { refreshConnectedDevices() }
                    }
                }
            }
        }
    }

    private val audioDeviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
            scope.launch { refreshConnectedDevices() }
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
            scope.launch { refreshConnectedDevices() }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(android.media.AudioManager.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction("com.android.bluetooth.btservice.action.BATTERY_LEVEL_CHANGED")
            addAction("android.bluetooth.device.action.METADATA_CHANGED")
            addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
            addAction("com.google.android.gms.nearby.fastpair.ACTION_STATUS_CHANGED")
            addAction("com.samsung.accessory.intent.action.BATTERY_UPDATE")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )

        // Register persistent profile proxy listeners for instantaneous (0ms) profile queries
        runCatching {
            adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.A2DP)
            adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.HEADSET)
        }

        // Register hardware AudioDeviceCallback for instantaneous, zero-latency detection
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        runCatching { audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null) }

        _btEnabled.value = adapter?.isEnabled ?: false
        scope.launch { refreshConnectedDevices() }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        runCatching {
            a2dpProfile?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
            headsetProfile?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
            a2dpProfile = null
            headsetProfile = null
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        runCatching { audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback) }
        scope.cancel()
    }

    fun refresh() {
        scope.launch { refreshConnectedDevices() }
    }

    @SuppressLint("MissingPermission")
    suspend fun refreshConnectedDevices() {
        val connected = mutableListOf<ConnectedDevice>()

        // 1. Check if Wired Headset is plugged in
        if (com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.isWiredConnected(context)) {
            val dbDevice = db.deviceDao().getDevice(com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.WIRED_ADDRESS)
            connected.add(
                ConnectedDevice(
                    address = com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.WIRED_ADDRESS,
                    name = dbDevice?.name ?: com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.WIRED_NAME,
                    isFavorite = dbDevice?.isFavorite ?: false,
                    type = DeviceType.WIRED_HEADPHONES
                )
            )
        }

        val ad = adapter
        if (ad != null && ad.isEnabled) {
            runCatching {
                // 2. Synchronous AudioManager check for active Bluetooth audio outputs (zero latency, never hangs)
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                val activeAudioOutputDevices = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
                val btAudioOutputs = activeAudioOutputDevices.filter {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (android.os.Build.VERSION.SDK_INT >= 31 && (
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER
                    ))
                }

                val audioDeviceNames = btAudioOutputs.map { it.productName.toString().trim() }.filter { it.isNotBlank() }
                val audioDeviceAddresses = btAudioOutputs.mapNotNull { it.address.takeIf { addr -> addr.isNotBlank() } }

                // 3. Fast profile connection state queries (synchronous IPC)
                val isA2dpActive = ad.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED
                val isHeadsetActive = ad.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED

                // 4. Synchronous profile query via cached profile proxies (0ms latency!)
                val profileDevices = mutableListOf<BluetoothDevice>()
                if (isA2dpActive) {
                    a2dpProfile?.let { runCatching { profileDevices.addAll(it.connectedDevices) } }
                }
                if (isHeadsetActive) {
                    headsetProfile?.let { runCatching { profileDevices.addAll(it.connectedDevices) } }
                }
                // Fallback only if proxies are not yet bound and a profile is active
                if (profileDevices.isEmpty() && (isA2dpActive || isHeadsetActive)) {
                    val fallback = withTimeoutOrNull(200L) {
                        coroutineScope {
                            val a2dpDef = if (isA2dpActive) async { getProfileDevices(ad, BluetoothProfile.A2DP) } else null
                            val headsetDef = if (isHeadsetActive) async { getProfileDevices(ad, BluetoothProfile.HEADSET) } else null
                            ((a2dpDef?.await() ?: emptyList()) + (headsetDef?.await() ?: emptyList()))
                        }
                    } ?: emptyList()
                    profileDevices.addAll(fallback)
                }

                val profileConnectedAddresses = profileDevices.map { it.address }.toSet()
                val bonded = ad.bondedDevices ?: emptySet()

                val isAudioActive = isA2dpActive || isHeadsetActive
                val hasBtAudioOutputs = btAudioOutputs.isNotEmpty()

                bonded.forEach { device ->
                    val devAddr = device.address
                    val devName = device.name ?: ""

                    val isConnectedByProfile = profileConnectedAddresses.contains(devAddr)
                    val isConnectedByAudioAddress = audioDeviceAddresses.contains(devAddr)
                    val isConnectedByAudioName = devName.isNotBlank() && audioDeviceNames.any { it.equals(devName, ignoreCase = true) }
                    val isConnectedByAcl = aclConnectedAddresses.contains(devAddr)
                    val isReflectConnected = runCatching {
                        val m = device.javaClass.getDeclaredMethod("isConnected")
                        m.isAccessible = true
                        m.invoke(device) as? Boolean ?: false
                    }.getOrDefault(false)

                    // Ground-truth check:
                    // If AudioManager has no BT audio outputs AND no profiles are active on adapter,
                    // this device CANNOT be physically connected for audio.
                    if (!hasBtAudioOutputs && !isAudioActive && !isConnectedByProfile) {
                        aclConnectedAddresses.remove(devAddr)
                        return@forEach
                    }

                    // A device is genuinely connected if:
                    // 1) Specifically listed in profile connected devices, OR
                    // 2) Specifically listed in active AudioManager outputs (by MAC or name), OR
                    // 3) Reflection reports connected AND an audio profile is globally active AND ACL or name matches
                    val isConn = isConnectedByProfile || isConnectedByAudioAddress || isConnectedByAudioName ||
                            (isReflectConnected && isAudioActive && (isConnectedByAcl || isConnectedByAudioName))

                    if (!isConn) {
                        aclConnectedAddresses.remove(devAddr)
                        return@forEach
                    }

                    if (isConn) {
                        com.smartbluetoothsleeptracker.receiver.BluetoothReceiver.setActiveConnectTime(
                            context, devAddr, System.currentTimeMillis()
                        )
                        val dbDevice = db.deviceDao().getDevice(devAddr)
                        val inferred = inferDeviceType(device)
                        // If DB previously auto-saved EARBUDS by default, but new detection identifies NECKBAND, auto-correct it
                        val devType = if (dbDevice == null) {
                            inferred
                        } else if (dbDevice.deviceType == DeviceType.EARBUDS && inferred == DeviceType.NECKBAND) {
                            scope.launch { db.deviceDao().setDeviceType(devAddr, DeviceType.NECKBAND) }
                            DeviceType.NECKBAND
                        } else if (dbDevice.deviceType == DeviceType.OTHER && inferred != DeviceType.OTHER) {
                            scope.launch { db.deviceDao().setDeviceType(devAddr, inferred) }
                            inferred
                        } else {
                            dbDevice.deviceType
                        }
                        val batteryInfo = readBatteryInfo(device)
                        connected.add(
                            ConnectedDevice(
                                address = devAddr,
                                name = if (devName.isNotBlank()) devName else devAddr,
                                isFavorite = dbDevice?.isFavorite ?: false,
                                type = devType,
                                device = device,
                                battery = batteryInfo
                            )
                        )
                    }
                }
            }
        }

        _connectedDevices.value = connected.distinctBy { it.address }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getProfileDevices(adapter: BluetoothAdapter, profile: Int): List<BluetoothDevice> = suspendCancellableCoroutine { cont ->
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                val devs = try { proxy.connectedDevices } catch (e: Exception) { emptyList() }
                runCatching { adapter.closeProfileProxy(p, proxy) }
                if (cont.isActive) cont.resume(devs)
            }
            override fun onServiceDisconnected(p: Int) {
                if (cont.isActive) cont.resume(emptyList())
            }
        }
        val bound = runCatching { adapter.getProfileProxy(context, listener, profile) }.getOrDefault(false)
        if (!bound && cont.isActive) {
            cont.resume(emptyList())
        }
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
    fun inferDeviceType(device: BluetoothDevice): DeviceType {
        val name = (device.name ?: "").lowercase().trim()

        if (name.contains("speaker") || name.contains("soundbar") || name.contains("theatre") ||
            name.contains("theater") || name.contains("tv") || name.contains("echo") || name.contains("nest")) {
            return DeviceType.HOME_THEATRE
        }

        // Neckbands (boAt Rockerz, OnePlus Bullets, Realme Buds Wireless, Boult Curve, Noise Tune, etc.)
        val isNeckband = name.contains("neck") || name.contains("band") ||
                name.contains("rockerz") || name.contains("bullets") || name.contains("tangent") ||
                name.contains("curve") || name.contains("tune") || name.contains("wi-c") ||
                name.contains("wi-1000") || name.contains("wi-xb") || name.contains("collar") ||
                name.contains("flex") || name.contains("evolve") || name.contains("glide") ||
                name.contains("arc") || (name.contains("wireless") && !name.contains("true wireless") && !name.contains("tws"))

        if (isNeckband) {
            return DeviceType.NECKBAND
        }

        // TWS Earbuds (AirPods, Galaxy Buds, Pixel Buds, Airdopes, Buds Air, etc.)
        val isTws = name.contains("tws") || name.contains("true wireless") ||
                name.contains("airpod") || name.contains("earbud") || name.contains("buds") ||
                name.contains("airdopes") || name.contains("earphone") || name.contains("freebuds") ||
                name.contains("galaxy buds") || name.contains("pixel buds") || name.contains("dots") ||
                name.contains("encod")

        if (isTws) {
            return DeviceType.EARBUDS
        }

        val major = device.bluetoothClass?.majorDeviceClass
        return when (major) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                if (name.contains("headphone") || name.contains("wh-") || name.contains("over-ear") || name.contains("crusher")) {
                    DeviceType.NECKBAND
                } else {
                    DeviceType.EARBUDS
                }
            }
            BluetoothClass.Device.Major.COMPUTER -> DeviceType.PC
            BluetoothClass.Device.Major.WEARABLE -> DeviceType.SMARTWATCH
            else -> DeviceType.OTHER
        }
    }

    @SuppressLint("MissingPermission")
    fun readBatteryInfo(device: BluetoothDevice): BatteryInfo {
        var main = -1
        var left = -1
        var right = -1
        var case = -1

        // 1. Direct/Reflective getBatteryLevel() (API 27+)
        runCatching {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val lvl = method.invoke(device) as? Int
            if (lvl != null && lvl in 0..100) {
                main = lvl
            }
        }

        // 2. Broadcast-cached battery levels
        val cached = deviceBatteryCache[device.address]
        if (cached != null) {
            if (main == -1 && cached.mainLevel in 0..100) main = cached.mainLevel
            if (left == -1 && cached.leftLevel in 0..100) left = cached.leftLevel
            if (right == -1 && cached.rightLevel in 0..100) right = cached.rightLevel
            if (case == -1 && cached.caseLevel in 0..100) case = cached.caseLevel
        }

        // 3. Google Fast Pair & Untethered Earbud Metadata (API 29+)
        // METADATA_UNTETHERED_LEFT_BATTERY = 10, RIGHT = 11, CASE = 12, MAIN = 18, CASE_FALLBACK = 19
        runCatching {
            val getMeta = try {
                device.javaClass.getDeclaredMethod("getMetadata", Int::class.javaPrimitiveType).apply { isAccessible = true }
            } catch (_: Exception) {
                device.javaClass.getMethod("getMetadata", Int::class.javaPrimitiveType)
            }
            val leftBytes = getMeta.invoke(device, 10) as? ByteArray
            val rightBytes = getMeta.invoke(device, 11) as? ByteArray
            val caseBytes = (getMeta.invoke(device, 12) as? ByteArray) ?: (getMeta.invoke(device, 19) as? ByteArray)
            val mainBytes = getMeta.invoke(device, 18) as? ByteArray

            parseBatteryBytes(leftBytes)?.let { left = it }
            parseBatteryBytes(rightBytes)?.let { right = it }
            parseBatteryBytes(caseBytes)?.let { case = it }
            if (main == -1) {
                parseBatteryBytes(mainBytes)?.let { main = it }
            }
        }

        val devType = inferDeviceType(device)
        val isEarbuds = devType == DeviceType.EARBUDS

        // 4. Persistent cache check (SharedPreferences)
        val prefs = context.getSharedPreferences("device_battery_prefs", Context.MODE_PRIVATE)
        // One-time cleanup of legacy synthetic case entries
        if (!prefs.getBoolean("cleared_fake_case_v2", false)) {
            prefs.edit().apply {
                prefs.all.keys.filter { it.startsWith("case_") }.forEach { remove(it) }
                putBoolean("cleared_fake_case_v2", true)
            }.apply()
        }

        if (left == -1) {
            val p = prefs.getInt("left_${device.address}", -1)
            if (p in 0..100) left = p
        }
        if (right == -1) {
            val p = prefs.getInt("right_${device.address}", -1)
            if (p in 0..100) right = p
        }
        if (case == -1) {
            val p = prefs.getInt("case_${device.address}", -1)
            if (p in 0..100) case = p
        }

        // 5. Harmonize Left and Right for TWS Earbuds
        if (isEarbuds) {
            if (main in 0..100) {
                if (left == -1) left = main
                if (right == -1) right = main
            } else if (left in 0..100 && right in 0..100) {
                main = (left + right) / 2
            } else if (left in 0..100) {
                main = left
                if (right == -1) right = left
            } else if (right in 0..100) {
                main = right
                if (left == -1) left = right
            }

            // Case battery: NEVER synthesize case percentage from earbuds!
            // If the case does not broadcast its own BLE/FastPair telemetry or is closed,
            // case remains -1 so UI accurately displays "--".
        }

        // 6. Persist confirmed levels to SharedPreferences
        prefs.edit().apply {
            if (main in 0..100) putInt("main_${device.address}", main)
            if (left in 0..100) putInt("left_${device.address}", left)
            if (right in 0..100) putInt("right_${device.address}", right)
            if (case in 0..100) putInt("case_${device.address}", case)
        }.apply()

        val info = BatteryInfo(
            mainLevel = main,
            leftLevel = left,
            rightLevel = right,
            caseLevel = case
        )
        deviceBatteryCache[device.address] = info
        return info
    }
}
