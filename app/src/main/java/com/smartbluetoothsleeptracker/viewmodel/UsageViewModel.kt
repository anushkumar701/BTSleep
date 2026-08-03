package com.smartbluetoothsleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.SleepBTApp
import com.smartbluetoothsleeptracker.data.db.*
import com.smartbluetoothsleeptracker.receiver.BluetoothReceiver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class UsagePeriod { TODAY, WEEK, MONTH }

data class DeviceUsageStat(
    val device: DeviceEntity,
    val totalMinutes: Int,
    val sessionCount: Int
)

data class ChartBarItem(
    val label: String,
    val minutes: Int,
    val isHighlighted: Boolean = false
)

data class UndoActionState(
    val message: String? = null,
    val pendingDeviceAddress: String? = null,
    val actionType: PendingActionType? = null
)

enum class PendingActionType { REMOVE_DEVICE, RESET_USAGE }

data class UsageUiState(
    val period: UsagePeriod = UsagePeriod.WEEK,
    val sessions: List<SessionEntity> = emptyList(),
    val deviceStats: List<DeviceUsageStat> = emptyList(),
    val chartItems: List<ChartBarItem> = emptyList(),
    val chartTitle: String = "Last 7 Days",
    val totalMinutes: Int = 0,
    val totalSessions: Int = 0,
    val totalDevices: Int = 0,
    val isRefreshing: Boolean = false,
    val undoState: UndoActionState = UndoActionState()
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SleepBTApp
    private val _state = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    private val _period = MutableStateFlow(UsagePeriod.WEEK)
    private var pendingJob: Job? = null

    init {
        viewModelScope.launch {
            _period.collectLatest { period ->
                loadData(period)
            }
        }
    }

    fun setPeriod(p: UsagePeriod) { _period.value = p }

    private suspend fun loadData(period: UsagePeriod) {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val (from, to) = when (period) {
            UsagePeriod.TODAY -> today to today
            UsagePeriod.WEEK -> today.minusDays(6) to today
            UsagePeriod.MONTH -> today.minusDays(29) to today
        }
        val fromStr = from.format(fmt)
        val toStr = to.format(fmt)

        val sessions = app.db.sessionDao().sessionsInRangeNow(fromStr, toStr).toMutableList()
        val usage = app.db.dailyUsageDao().usageInRangeNow(fromStr, toStr).toMutableList()

        // Check active ongoing connections to include live duration
        val connectedDevices = app.btMonitor.connectedDevices.value
        val activeAddrs = connectedDevices.map { it.address }
        val now = System.currentTimeMillis()

        var extraLiveMinutes = 0
        activeAddrs.forEach { addr ->
            val startTime = BluetoothReceiver.getActiveConnectTime(app, addr)
            if (startTime > 0L && now > startTime) {
                val liveMin = ((now - startTime) / 60_000L).toInt()
                if (liveMin > 0) {
                    extraLiveMinutes += liveMin
                }
            }
        }

        // Aggregate by device
        val deviceAddrs = (sessions.map { it.deviceAddress } + usage.map { it.deviceAddress } + activeAddrs).distinct()
        val stats = deviceAddrs.mapNotNull { addr ->
            val dev = app.db.deviceDao().getDevice(addr) ?: return@mapNotNull null
            var mins = usage.filter { it.deviceAddress == addr }.sumOf { it.totalMinutes }
            var count = sessions.count { it.deviceAddress == addr }

            val activeStart = BluetoothReceiver.getActiveConnectTime(app, addr)
            if (activeStart > 0L && now > activeStart) {
                val liveMins = ((now - activeStart) / 60_000L).toInt()
                mins += liveMins
            }

            DeviceUsageStat(dev, mins, count)
        }.sortedByDescending { it.totalMinutes }

        // Aggregate chart bars
        val chartBars = when (period) {
            UsagePeriod.TODAY -> {
                val currentHour = LocalDateTime.now().hour
                val currentBucket = (currentHour / 4) * 4
                val todayUsageMins = usage.filter { it.date == fromStr }.sumOf { it.totalMinutes }
                (0..20 step 4).map { h ->
                    val label = String.format("%02d:00", h)
                    var hourMins = sessions.filter {
                        val sTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.startTime), ZoneId.systemDefault())
                        sTime.toLocalDate() == today && sTime.hour in h until (h + 4)
                    }.sumOf { it.actualDurationMin ?: it.plannedDurationMin }

                    val isCurrent = (h == currentBucket)
                    if (isCurrent && hourMins == 0) {
                        hourMins = maxOf(todayUsageMins, extraLiveMinutes)
                    }
                    ChartBarItem(label, hourMins, isCurrent)
                }
            }
            UsagePeriod.WEEK -> {
                (0..6).map { offset ->
                    val d = from.plusDays(offset.toLong())
                    val dStr = d.format(fmt)
                    val label = if (d == today) "Today" else d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    var mins = usage.filter { it.date == dStr }.sumOf { it.totalMinutes }
                    if (d == today && mins == 0 && extraLiveMinutes > 0) {
                        mins = extraLiveMinutes
                    }
                    ChartBarItem(label, mins, d == today)
                }
            }
            UsagePeriod.MONTH -> {
                (0..3).map { w ->
                    val wStart = from.plusDays((w * 7).toLong())
                    val wEnd = if (w == 3) today else wStart.plusDays(6)
                    val label = "W${w + 1}"
                    var mins = usage.filter {
                        try {
                            val d = LocalDate.parse(it.date, fmt)
                            !d.isBefore(wStart) && !d.isAfter(wEnd)
                        } catch (e: Exception) {
                            false
                        }
                    }.sumOf { it.totalMinutes }
                    if (w == 3 && mins == 0 && extraLiveMinutes > 0) {
                        mins = extraLiveMinutes
                    }
                    val isCurrent = !today.isBefore(wStart) && !today.isAfter(wEnd)
                    ChartBarItem(label, mins, isCurrent)
                }
            }
        }

        val totalMins = stats.sumOf { it.totalMinutes }
        val totalSess = sessions.size

        _state.value = UsageUiState(
            period = period,
            sessions = sessions,
            deviceStats = stats,
            chartItems = chartBars,
            chartTitle = when (period) {
                UsagePeriod.TODAY -> "Today's Hourly Usage"
                UsagePeriod.WEEK -> "Last 7 Days"
                UsagePeriod.MONTH -> "Last 30 Days (Weekly)"
            },
            totalMinutes = totalMins,
            totalSessions = totalSess,
            totalDevices = stats.size
        )
    }

    fun toggleFavorite(address: String) {
        viewModelScope.launch {
            val dev = app.db.deviceDao().getDevice(address) ?: return@launch
            app.db.deviceDao().setFavorite(address, !dev.isFavorite)
            loadData(_period.value)
        }
    }

    fun setDeviceType(address: String, type: DeviceType) {
        viewModelScope.launch {
            app.db.deviceDao().setDeviceType(address, type)
            loadData(_period.value)
        }
    }

    // ── UNDO SNACKBAR FOR DESTRUCTIVE ACTIONS ─────────────────────────────

    fun scheduleResetUsage(address: String) {
        pendingJob?.cancel()
        _state.update {
            it.copy(
                undoState = UndoActionState(
                    message = "Usage reset for device",
                    pendingDeviceAddress = address,
                    actionType = PendingActionType.RESET_USAGE
                )
            )
        }
        pendingJob = viewModelScope.launch {
            delay(5000L) // 5 second undo window
            commitPendingAction()
        }
    }

    fun scheduleRemoveDevice(address: String) {
        pendingJob?.cancel()
        _state.update {
            it.copy(
                undoState = UndoActionState(
                    message = "Device removed",
                    pendingDeviceAddress = address,
                    actionType = PendingActionType.REMOVE_DEVICE
                )
            )
        }
        pendingJob = viewModelScope.launch {
            delay(5000L) // 5 second undo window
            commitPendingAction()
        }
    }

    fun undoPendingAction() {
        pendingJob?.cancel()
        pendingJob = null
        _state.update { it.copy(undoState = UndoActionState()) }
    }

    private suspend fun commitPendingAction() {
        val undo = _state.value.undoState
        val addr = undo.pendingDeviceAddress ?: return
        when (undo.actionType) {
            PendingActionType.RESET_USAGE -> {
                app.db.dailyUsageDao().deleteForDevice(addr)
                app.db.sessionDao().deleteForDevice(addr)
            }
            PendingActionType.REMOVE_DEVICE -> {
                app.db.deviceDao().deleteByAddress(addr)
            }
            null -> {}
        }
        _state.update { it.copy(undoState = UndoActionState()) }
        loadData(_period.value)
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            app.db.sessionDao().deleteById(id)
            loadData(_period.value)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            app.db.sessionDao().deleteAll()
            app.db.dailyUsageDao().deleteAll()
            loadData(_period.value)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            loadData(_period.value)
            delay(300)
            _state.update { it.copy(isRefreshing = false) }
        }
    }
}
