package com.smartbluetoothsleeptracker

import android.app.Application
import com.smartbluetoothsleeptracker.core.bluetooth.BluetoothDisconnector
import com.smartbluetoothsleeptracker.core.bluetooth.BluetoothMonitor
import com.smartbluetoothsleeptracker.core.notification.AppNotifications
import com.smartbluetoothsleeptracker.core.playback.PlaybackController
import com.smartbluetoothsleeptracker.core.screen.ScreenController
import com.smartbluetoothsleeptracker.data.db.AppDatabase
import com.smartbluetoothsleeptracker.data.prefs.AppPrefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ActiveTimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val remainingMs: Long = 0L,
    val endTimeMillis: Long = 0L,
    val plannedMinutes: Int = 0,
    val extendedMinutes: Int = 0,
    val targetAddresses: List<String> = emptyList()
)

class SleepBTApp : Application() {

    lateinit var db: AppDatabase
    lateinit var prefs: AppPrefs
    lateinit var disconnector: BluetoothDisconnector
    lateinit var btMonitor: BluetoothMonitor
    lateinit var playbackController: PlaybackController
    lateinit var screenController: ScreenController
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    private val _timerState = MutableStateFlow(ActiveTimerState())
    val timerState: StateFlow<ActiveTimerState> = _timerState.asStateFlow()

    fun updateTimerState(state: ActiveTimerState) {
        _timerState.value = state
    }

    fun updateTimerState(transform: (ActiveTimerState) -> ActiveTimerState) {
        _timerState.value = transform(_timerState.value)
    }

    override fun onCreate() {
        super.onCreate()

        db = AppDatabase.get(this)
        prefs = AppPrefs(this)
        disconnector = BluetoothDisconnector(this, db)
        btMonitor = BluetoothMonitor(this, db).apply {
            registerCaseProvider(com.smartbluetoothsleeptracker.core.bluetooth.case.AppleCaseBatteryProvider(this@SleepBTApp))
            registerCaseProvider(com.smartbluetoothsleeptracker.core.bluetooth.case.GalaxyBudsCaseBatteryProvider(this@SleepBTApp))
        }
        playbackController = PlaybackController(this)
        screenController = ScreenController(this)

        AppNotifications.createChannels(this)
        com.smartbluetoothsleeptracker.core.haptics.HapticManager.init(this)

        // Wire up cooldown enforcement: when a device reconnects, check if it should be blocked
        btMonitor.onDeviceConnected = { device ->
            if (disconnector.shouldBlockDevice(device.address)) {
                disconnector.enforceDisconnect(device)
            }
        }

        btMonitor.start()
    }
}
