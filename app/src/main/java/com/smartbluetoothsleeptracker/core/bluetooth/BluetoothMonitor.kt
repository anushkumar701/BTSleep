package com.smartbluetoothsleeptracker.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.smartbluetoothsleeptracker.core.bluetooth.case.CaseBattery
import com.smartbluetoothsleeptracker.core.bluetooth.case.CaseBatteryProvider
import com.smartbluetoothsleeptracker.core.bluetooth.case.CaseSupport
import com.smartbluetoothsleeptracker.data.db.AppDatabase
import com.smartbluetoothsleeptracker.data.db.DeviceEntity
import com.smartbluetoothsleeptracker.data.db.DeviceType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

import com.smartbluetoothsleeptracker.core.notification.AppNotifications
import com.smartbluetoothsleeptracker.data.prefs.AppPrefs

data class BatteryInfo(
    val mainLevel: Int = -1,    // 0..100, -1 = unknown
    val leftLevel: Int = -1,    // 0..100, -1 = unknown
    val rightLevel: Int = -1,   // 0..100, -1 = unknown
    val caseLevel: Int = -1,    // 0..100, -1 = unknown
    val caseBattery: CaseBattery? = null,
    val caseSupport: CaseSupport = CaseSupport.Unsupported
) {
    val isKnown: Boolean
        get() = (mainLevel in 0..100) || (leftLevel in 0..100) || (rightLevel in 0..100) || (caseLevel in 0..100)

    fun formatSummary(deviceType: DeviceType): String {
        val hasLeft = leftLevel in 0..100
        val hasRight = rightLevel in 0..100
        val hasCase = caseLevel in 0..100
        val parts = mutableListOf<String>()

        if (hasLeft && hasRight) {
            parts.add("Left: $leftLevel%")
            parts.add("Right: $rightLevel%")
        } else if (hasLeft) {
            parts.add("Left: $leftLevel%")
        } else if (hasRight) {
            parts.add("Right: $rightLevel%")
        } else if (mainLevel in 0..100) {
            parts.add("Battery: $mainLevel%")
        }

        if (hasCase) {
            val charging = if (caseBattery?.charging == true) " ⚡" else ""
            parts.add("Case: $caseLevel%$charging")
        }

        return if (parts.isNotEmpty()) parts.joinToString("  •  ") else "Connected"
    }
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
    private val db: AppDatabase,
    private val prefs: AppPrefs? = null
) {
    companion object {
        private const val TAG = "BtMonitor"

        val isConnectedMethod: java.lang.reflect.Method? by lazy {
            runCatching {
                BluetoothDevice::class.java.getDeclaredMethod("isConnected").apply { isAccessible = true }
            }.getOrNull()
        }

        val getBatteryLevelMethod: java.lang.reflect.Method? by lazy {
            runCatching {
                BluetoothDevice::class.java.getMethod("getBatteryLevel").apply { isAccessible = true }
            }.getOrNull()
        }

        val getMetadataMethod: java.lang.reflect.Method? by lazy {
            runCatching {
                BluetoothDevice::class.java.getMethod("getMetadata", Int::class.javaPrimitiveType).apply { isAccessible = true }
            }.getOrNull()
        }
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
    private val notifiedConnectionAddresses = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val lastNotifiedSummary = ConcurrentHashMap<String, String>()
    @Volatile
    private var cachedConnectBatteryNotifEnabled: Boolean = true
    private val refreshMutex = kotlinx.coroutines.sync.Mutex()
    private var debouncedRefreshJob: Job? = null

    fun triggerDebouncedRefresh(delayMs: Long = 80L) {
        debouncedRefreshJob?.cancel()
        debouncedRefreshJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            refreshConnectedDevices()
        }
    }

    val caseProviders = mutableListOf<CaseBatteryProvider>()
    private val caseBatteryCache = ConcurrentHashMap<String, CaseBattery>()

    fun registerCaseProvider(provider: CaseBatteryProvider) {
        if (!caseProviders.contains(provider)) {
            caseProviders.add(provider)
        }
    }

    fun getCaseSupport(device: BluetoothDevice, name: String = ""): CaseSupport {
        val devName = name.ifBlank {
            try { device.name ?: "" } catch (_: SecurityException) { "" }
        }
        val cachedCase = deviceBatteryCache[device.address]?.caseLevel ?: -1
        return if (cachedCase in 0..100 || caseBatteryCache.containsKey(device.address) || caseProviders.any { it.supports(device, devName) }) {
            CaseSupport.Supported
        } else {
            CaseSupport.Unsupported
        }
    }

    fun updateCaseBattery(address: String, caseBattery: CaseBattery) {
        caseBatteryCache[address] = caseBattery
        val current = deviceBatteryCache[address] ?: BatteryInfo()
        deviceBatteryCache[address] = current.copy(
            caseLevel = caseBattery.percent,
            caseBattery = caseBattery
        )
        triggerDebouncedRefresh(40L)
    }

    suspend fun queryCaseBattery(device: BluetoothDevice, name: String = ""): CaseBattery? {
        val devName = name.ifBlank {
            try { device.name ?: "" } catch (_: SecurityException) { "" }
        }
        val provider = caseProviders.firstOrNull { it.supports(device, devName) } ?: return null
        val result = runCatching { provider.read(device) }.getOrNull()
        if (result != null) {
            updateCaseBattery(device.address, result)
        }
        return result
    }

    private var a2dpProfile: BluetoothProfile? = null
    private var headsetProfile: BluetoothProfile? = null

    private val profileServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = proxy
                BluetoothProfile.HEADSET -> headsetProfile = proxy
            }
            triggerDebouncedRefresh(60L)
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = null
                BluetoothProfile.HEADSET -> headsetProfile = null
            }
            triggerDebouncedRefresh(60L)
        }
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
                        }
                        triggerDebouncedRefresh(40L)
                        onDeviceConnected?.invoke(device)
                    } else {
                        triggerDebouncedRefresh(60L)
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (device != null) {
                        aclConnectedAddresses.remove(device.address)
                        deviceBatteryCache.remove(device.address)
                        dismissBatteryNotification(device.address)
                        Log.i(TAG, "ACL disconnected: ${device.name ?: device.address}")
                        triggerDebouncedRefresh(20L)
                        onDeviceDisconnected?.invoke(device)
                    } else {
                        triggerDebouncedRefresh(60L)
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
                            }
                            triggerDebouncedRefresh(40L)
                            onDeviceConnected?.invoke(device)
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            aclConnectedAddresses.remove(device.address)
                            dismissBatteryNotification(device.address)
                            Log.i(TAG, "Profile disconnected: ${device.name ?: device.address}")
                            triggerDebouncedRefresh(20L)
                            onDeviceDisconnected?.invoke(device)
                        } else {
                            triggerDebouncedRefresh(60L)
                        }
                    } else {
                        triggerDebouncedRefresh(60L)
                    }
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    triggerDebouncedRefresh(60L)
                }
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED",
                "com.android.bluetooth.btservice.action.BATTERY_LEVEL_CHANGED" -> {
                    val lvl = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    if (device != null && lvl in 0..100) {
                        val current = deviceBatteryCache[device.address] ?: BatteryInfo()
                        val updated = current.copy(mainLevel = lvl)
                        deviceBatteryCache[device.address] = updated
                    }
                    triggerDebouncedRefresh(40L)
                }
                "android.bluetooth.device.action.METADATA_CHANGED",
                "com.google.android.gms.nearby.fastpair.ACTION_STATUS_CHANGED",
                "com.samsung.accessory.intent.action.BATTERY_UPDATE" -> {
                    if (device != null) {
                        Log.i(TAG, "Device metadata/battery update broadcast received for ${device.address}")
                    }
                    triggerDebouncedRefresh(30L)
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
                                    if (k == 1 && v != null && v in 0..9) {
                                        val pct = (v + 1) * 10
                                        current = current.copy(mainLevel = pct)
                                    }
                                    i += 2
                                }
                                deviceBatteryCache[device.address] = current
                            }
                        }
                    } else if (cmd == "+XEVENT" && device != null) {
                        @Suppress("DEPRECATION")
                        val args = intent.getSerializableExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS) as? Array<*>
                        if (args != null) {
                            runCatching {
                                var current = deviceBatteryCache[device.address] ?: BatteryInfo()
                                for (idx in args.indices) {
                                    if (args[idx]?.toString()?.equals("BATTERY", ignoreCase = true) == true && idx + 2 < args.size) {
                                        val lvl = (args[idx + 1] as? Number)?.toInt() ?: (args[idx + 1] as? String)?.toIntOrNull()
                                        val total = (args[idx + 2] as? Number)?.toInt() ?: (args[idx + 2] as? String)?.toIntOrNull()
                                        if (lvl != null && total != null && total > 0) {
                                            val pct = ((lvl.toFloat() / total) * 100).toInt().coerceIn(0, 100)
                                            current = current.copy(mainLevel = pct)
                                            deviceBatteryCache[device.address] = current
                                        }
                                    }
                                }
                            }
                        }
                    }
                    triggerDebouncedRefresh(80L)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _btEnabled.value = state == BluetoothAdapter.STATE_ON
                    if (state == BluetoothAdapter.STATE_OFF) {
                        aclConnectedAddresses.clear()
                        deviceBatteryCache.clear()
                        dismissAllBatteryNotifications()
                        a2dpProfile = null
                        headsetProfile = null
                        triggerDebouncedRefresh(20L)
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        runCatching {
                            adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.A2DP)
                            adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.HEADSET)
                        }
                        triggerDebouncedRefresh(80L)
                    }
                }
            }
        }
    }

    private val audioDeviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
            triggerDebouncedRefresh(60L)
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
            triggerDebouncedRefresh(60L)
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

        prefs?.let { p ->
            scope.launch {
                p.settings.map { it.connectBatteryNotificationEnabled }.distinctUntilChanged().collect {
                    cachedConnectBatteryNotifEnabled = it
                }
            }
        }

        _btEnabled.value = adapter?.isEnabled ?: false
        scope.launch { refreshConnectedDevices() }
    }

    fun stop() {
        dismissAllBatteryNotifications()
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

    fun refresh(clearCache: Boolean = false) {
        if (clearCache) {
            deviceBatteryCache.clear()
            caseBatteryCache.clear()
        }
        scope.launch { refreshConnectedDevices() }
    }

    @SuppressLint("MissingPermission")
    suspend fun refreshConnectedDevices() {
        if (!refreshMutex.tryLock()) return
        try {
            val connected = mutableListOf<ConnectedDevice>()
            val allDbDevices = db.deviceDao().getAllNow().associateBy { it.address }

            // 1. Check if Wired Headset is plugged in
            if (com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.isWiredConnected(context)) {
                val dbDevice = allDbDevices[com.smartbluetoothsleeptracker.receiver.WiredHeadsetReceiver.WIRED_ADDRESS]
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
                            isConnectedMethod?.invoke(device) as? Boolean ?: false
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
                            val dbDevice = allDbDevices[devAddr]
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
                            notifyDeviceConnectedIfEligible(device, devName, batteryInfo, devType)
                        }
                    }
                }
            }

            val finalConnected = connected.distinctBy { it.address }
            val currentAddrs = finalConnected.map { it.address }.toSet()

            // Automatically dismiss battery notification for any device that has disconnected
            val disconnectedAddrs = notifiedConnectionAddresses.filter { !currentAddrs.contains(it) }
            disconnectedAddrs.forEach { addr ->
                dismissBatteryNotification(addr)
            }

            notifiedConnectionAddresses.retainAll(currentAddrs)
            lastNotifiedSummary.keys.retainAll(currentAddrs)

            _connectedDevices.value = finalConnected
        } finally {
            refreshMutex.unlock()
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyDeviceConnectedIfEligible(
        device: BluetoothDevice,
        devName: String,
        info: BatteryInfo,
        devType: DeviceType
    ) {
        if (!cachedConnectBatteryNotifEnabled) return

        // Audio devices only (Earbuds, Neckbands, Headphones)
        if (devType != DeviceType.EARBUDS && devType != DeviceType.NECKBAND && devType != DeviceType.WIRED_HEADPHONES) {
            return
        }

        val addr = device.address
        val summary = info.formatSummary(devType)
        val isNew = notifiedConnectionAddresses.add(addr)
        val prev = lastNotifiedSummary[addr]

        // Trigger notification if newly connected, or if battery summary has updated
        if (isNew || (prev != null && prev != summary)) {
            lastNotifiedSummary[addr] = summary
            val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
            val notif = AppNotifications.batteryStatusNotification(
                ctx = context,
                deviceName = devName.ifBlank { "Bluetooth Audio" },
                batterySummary = summary
            ).build()
            runCatching {
                val notifId = AppNotifications.NOTIF_BATTERY_STATUS + (addr.hashCode() and 0x7FFF)
                nm.notify(notifId, notif)
                Log.i(TAG, "Posted battery status notification for $devName: $summary (id=$notifId)")
            }
        }
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

    private fun parseMetadataInt(bytes: ByteArray?): Int? {
        if (bytes == null || bytes.isEmpty()) return null
        val str = String(bytes, Charsets.UTF_8).trim()
        val num = str.toIntOrNull()
        if (num != null) return if (num in 0..100) num else null
        val raw = bytes[0].toInt() and 0xFF
        return if (raw in 0..100) raw else null
    }

    private fun parseMetadataBool(bytes: ByteArray?): Boolean? {
        if (bytes == null || bytes.isEmpty()) return null
        val str = String(bytes, Charsets.UTF_8).trim()
        if (str.equals("true", ignoreCase = true) || str == "1") return true
        if (str.equals("false", ignoreCase = true) || str == "0") return false
        return bytes[0].toInt() == 1
    }

    @SuppressLint("MissingPermission")
    fun readBatteryInfo(device: BluetoothDevice): BatteryInfo {
        var main = -1
        var left = -1
        var right = -1
        var case = -1
        var isLeftCharging: Boolean? = null
        var isRightCharging: Boolean? = null
        var isCaseCharging: Boolean? = null

        // 1. Direct/Reflective getBatteryLevel() (API 27+ cached method)
        runCatching {
            val lvl = getBatteryLevelMethod?.invoke(device) as? Int
            if (lvl != null && lvl in 0..100) {
                main = lvl
            }
        }

        // 1b. Direct probe on BluetoothDevice.getMetadata() (AOSP Fast Pair keys)
        // Invoked via reflection; succeeds if system/ROM grants BLUETOOTH_PRIVILEGED or permits access
        runCatching {
            val metaMethod = getMetadataMethod
            if (metaMethod != null) {
                // Key 10: METADATA_UNTETHERED_LEFT_BATTERY
                val leftBytes = metaMethod.invoke(device, 10) as? ByteArray
                val parsedLeft = parseMetadataInt(leftBytes)
                if (parsedLeft != null && parsedLeft in 0..100) left = parsedLeft

                // Key 11: METADATA_UNTETHERED_RIGHT_BATTERY
                val rightBytes = metaMethod.invoke(device, 11) as? ByteArray
                val parsedRight = parseMetadataInt(rightBytes)
                if (parsedRight != null && parsedRight in 0..100) right = parsedRight

                // Key 12: METADATA_UNTETHERED_CASE_BATTERY
                val caseBytes = metaMethod.invoke(device, 12) as? ByteArray
                val parsedCase = parseMetadataInt(caseBytes)
                if (parsedCase != null && parsedCase in 0..100) case = parsedCase

                // Key 13: METADATA_UNTETHERED_LEFT_CHARGING
                val leftChargingBytes = metaMethod.invoke(device, 13) as? ByteArray
                isLeftCharging = parseMetadataBool(leftChargingBytes)

                // Key 14: METADATA_UNTETHERED_RIGHT_CHARGING
                val rightChargingBytes = metaMethod.invoke(device, 14) as? ByteArray
                isRightCharging = parseMetadataBool(rightChargingBytes)

                // Key 15: METADATA_UNTETHERED_CASE_CHARGING
                val caseChargingBytes = metaMethod.invoke(device, 15) as? ByteArray
                isCaseCharging = parseMetadataBool(caseChargingBytes)

                // Key 18: METADATA_MAIN_BATTERY
                if (main == -1) {
                    val mainBytes = metaMethod.invoke(device, 18) as? ByteArray
                    val parsedMain = parseMetadataInt(mainBytes)
                    if (parsedMain != null && parsedMain in 0..100) main = parsedMain
                }
            }
        }

        // Working bud determination:
        // If an earbud is charging in the case, it is NOT in the user's ear -> mark inactive (-1)
        if (isLeftCharging == true) {
            left = -1
        }
        if (isRightCharging == true) {
            right = -1
        }

        // 2. Broadcast-cached levels for main & case only
        // NEVER resurrect stale discrete buds (left/right) from cache!
        val cached = deviceBatteryCache[device.address]
        if (cached != null) {
            if (main == -1 && cached.mainLevel in 0..100) main = cached.mainLevel
            if (case == -1 && cached.caseLevel in 0..100) case = cached.caseLevel
        }

        val devType = inferDeviceType(device)
        val isEarbuds = devType == DeviceType.EARBUDS
        val devName = try { device.name ?: "" } catch (_: SecurityException) { "" }

        // Check if device name explicitly indicates single Left or Right bud
        val isExplicitLeft = devName.endsWith("(L)", ignoreCase = true) ||
                             devName.endsWith(" L", ignoreCase = true) ||
                             devName.contains(" Left", ignoreCase = true)
        val isExplicitRight = devName.endsWith("(R)", ignoreCase = true) ||
                              devName.endsWith(" R", ignoreCase = true) ||
                              devName.contains(" Right", ignoreCase = true)

        if (isExplicitLeft && left == -1 && main in 0..100) {
            left = main
            right = -1
        } else if (isExplicitRight && right == -1 && main in 0..100) {
            right = main
            left = -1
        }

        // Persistent cache check (SharedPreferences) as initial fallback for main & case only
        val prefs = context.getSharedPreferences("device_battery_prefs", Context.MODE_PRIVATE)
        if (main == -1 && left == -1 && right == -1) {
            val pMain = prefs.getInt("main_${device.address}", -1)
            if (pMain in 0..100) main = pMain
        }
        if (case == -1) {
            val p = prefs.getInt("case_${device.address}", -1)
            if (p in 0..100) case = p
        }

        // TWS Earbuds level management:
        // Only working buds are shown:
        // - If both left & right are working: main = average
        // - If only left is working: main = left, right remains -1
        // - If only right is working: main = right, left remains -1
        // - If neither discrete is working: left = -1, right = -1, main = unified
        if (isEarbuds) {
            if (left in 0..100 && right in 0..100) {
                main = (left + right) / 2
            } else if (left in 0..100) {
                main = left
            } else if (right in 0..100) {
                main = right
            }
        }

        // Persist confirmed levels to SharedPreferences; remove keys for inactive buds so they don't resurrect
        val oldMain = prefs.getInt("main_${device.address}", -1)
        val oldLeft = prefs.getInt("left_${device.address}", -1)
        val oldRight = prefs.getInt("right_${device.address}", -1)
        val oldCase = prefs.getInt("case_${device.address}", -1)
        if (main != oldMain || left != oldLeft || right != oldRight || case != oldCase) {
            val editor = prefs.edit()
            if (main in 0..100) editor.putInt("main_${device.address}", main) else editor.remove("main_${device.address}")
            if (left in 0..100) editor.putInt("left_${device.address}", left) else editor.remove("left_${device.address}")
            if (right in 0..100) editor.putInt("right_${device.address}", right) else editor.remove("right_${device.address}")
            if (case in 0..100) editor.putInt("case_${device.address}", case) else editor.remove("case_${device.address}")
            editor.apply()
        }

        val support = getCaseSupport(device, devName)
        val caseBat = if (case in 0..100) {
            val newCaseBat = CaseBattery(
                percent = case,
                charging = isCaseCharging ?: caseBatteryCache[device.address]?.charging,
                seenAtMillis = System.currentTimeMillis()
            )
            caseBatteryCache[device.address] = newCaseBat
            newCaseBat
        } else {
            caseBatteryCache[device.address]
        }

        if (case == -1 && caseBat != null && caseBat.percent in 0..100) {
            case = caseBat.percent
        }

        val info = BatteryInfo(
            mainLevel = main,
            leftLevel = left,
            rightLevel = right,
            caseLevel = case,
            caseBattery = caseBat,
            caseSupport = if (case in 0..100) CaseSupport.Supported else support
        )
        deviceBatteryCache[device.address] = info
        return info
    }

    fun dismissBatteryNotification(address: String) {
        runCatching {
            val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
            val notifId = AppNotifications.NOTIF_BATTERY_STATUS + (address.hashCode() and 0x7FFF)
            nm.cancel(notifId)
            notifiedConnectionAddresses.remove(address)
            lastNotifiedSummary.remove(address)
            Log.i(TAG, "Dismissed battery status notification for $address (id=$notifId)")
        }
    }

    fun dismissAllBatteryNotifications() {
        runCatching {
            val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
            notifiedConnectionAddresses.forEach { addr ->
                val notifId = AppNotifications.NOTIF_BATTERY_STATUS + (addr.hashCode() and 0x7FFF)
                nm.cancel(notifId)
            }
            notifiedConnectionAddresses.clear()
            lastNotifiedSummary.clear()
            Log.i(TAG, "Dismissed all battery status notifications")
        }
    }
}
