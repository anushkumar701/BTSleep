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
    val period: UsagePeriod = UsagePeriod.TODAY,
    val sessions: List<SessionEntity> = emptyList(),
    val deviceStats: List<DeviceUsageStat> = emptyList(),
    val chartItems: List<ChartBarItem> = emptyList(),
    val chartTitle: String = "Today's Hourly Usage",
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

    private val _period = MutableStateFlow(UsagePeriod.TODAY)
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

        app.db.sessionDao().deleteEmptySessions()
        app.db.sessionDao().pruneOldSessions(500)

        // Fetch raw sessions and daily usage in date range
        val rawSessions = app.db.sessionDao().sessionsInRangeNow(fromStr, toStr)
        val dailyUsages = app.db.dailyUsageDao().usageInRangeNow(fromStr, toStr)

        val sessions = rawSessions
            .filter { (it.actualDurationMin ?: 0) > 0 || (it.endTime == null && it.plannedDurationMin > 0) }
            .distinctBy { it.id }
            .sortedByDescending { it.startTime }
            .toMutableList()

        // Check active ongoing Bluetooth connections or running timer to include live duration
        val connectedDevices = app.btMonitor.connectedDevices.value
        val activeAddrs = connectedDevices.map { it.address }
        val now = System.currentTimeMillis()

        // Check if a timer session is actively running
        val activeSession = sessions.find { it.endTime == null }
        val activeTimerAddr = activeSession?.deviceAddress

        val liveMinutesMap = mutableMapOf<String, Int>()
        activeAddrs.forEach { addr ->
            if (activeSession != null && addr == activeTimerAddr) {
                // Live duration of the running timer
                val elapsedMs = (now - activeSession.startTime).coerceAtLeast(0L)
                val liveMin = com.smartbluetoothsleeptracker.service.TimerCalculations.calculateDurationMinutes(elapsedMs)
                if (liveMin > 0) {
                    liveMinutesMap[addr] = liveMin
                }
            } else if (activeSession == null || addr != activeTimerAddr) {
                // Background connection time since last recorded event
                val startTime = BluetoothReceiver.getActiveConnectTime(app, addr)
                if (startTime > 0L && now > startTime) {
                    val durationMs = now - startTime
                    val liveMin = com.smartbluetoothsleeptracker.service.TimerCalculations.calculateDurationMinutes(durationMs)
                    if (liveMin > 0) {
                        liveMinutesMap[addr] = liveMin
                    }
                }
            }
        }
        val totalLiveMinutes = liveMinutesMap.values.sum()

        // ── Unified Per-Device Stats (Sessions + Daily Usage + Live Minutes) ──
        val deviceAddrs = (sessions.map { it.deviceAddress } + dailyUsages.map { it.deviceAddress } + activeAddrs).distinct()
        val stats = deviceAddrs.mapNotNull { addr ->
            val dev = app.db.deviceDao().getDevice(addr)
                ?: connectedDevices.find { it.address == addr }?.let { conn ->
                    val entity = com.smartbluetoothsleeptracker.data.db.DeviceEntity(
                        address = conn.address,
                        name = conn.name,
                        deviceType = conn.type,
                        isFavorite = conn.isFavorite
                    )
                    app.db.deviceDao().upsert(entity)
                    entity
                }
                ?: return@mapNotNull null
            val devSessions = sessions.filter { it.deviceAddress == addr && it.endTime != null && (it.actualDurationMin ?: 0) > 0 }
            val sessionMins = devSessions.sumOf { it.actualDurationMin ?: 0 }
            val dailyMins = dailyUsages.filter { it.deviceAddress == addr }.sumOf { it.totalMinutes }
            val baseMins = maxOf(sessionMins, dailyMins)
            val liveMins = liveMinutesMap[addr] ?: 0
            val totalMins = baseMins + liveMins

            val completedCount = devSessions.size
            val dailyCount = dailyUsages.filter { it.deviceAddress == addr }.sumOf { it.sessionCount }
            val hasActiveLive = liveMins > 0
            val count = maxOf(completedCount, dailyCount) + (if (hasActiveLive) 1 else 0)

            if (totalMins > 0 || count > 0 || addr in activeAddrs) {
                DeviceUsageStat(dev, totalMins, count)
            } else {
                null
            }
        }.sortedByDescending { it.totalMinutes }

        // ── Unified Chart Bar Items (12-Hour Format & Accurate Accounting) ──
        val chartBars = when (period) {
            UsagePeriod.TODAY -> {
                val currentHour = LocalDateTime.now().hour
                val currentBucket = (currentHour / 4) * 4
                (0..20 step 4).map { h ->
                    val label = when (h) {
                        0 -> "12 AM"
                        12 -> "12 PM"
                        in 1..11 -> "$h AM"
                        else -> "${h - 12} PM"
                    }
                    var hourMins = sessions.filter {
                        if (it.endTime == null) return@filter false
                        val sTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.startTime), ZoneId.systemDefault())
                        sTime.toLocalDate() == today && sTime.hour in h until (h + 4)
                    }.sumOf { it.actualDurationMin ?: 0 }

                    val isCurrent = (h == currentBucket)
                    if (isCurrent && totalLiveMinutes > 0) {
                        hourMins += totalLiveMinutes
                    }
                    ChartBarItem(label, hourMins, isCurrent)
                }
            }
            UsagePeriod.WEEK -> {
                (0..6).map { offset ->
                    val d = from.plusDays(offset.toLong())
                    val dStr = d.format(fmt)
                    val label = if (d == today) "Today" else d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    
                    var dayMins = deviceAddrs.sumOf { addr ->
                        val devSess = sessions.filter { it.deviceAddress == addr && it.date == dStr && it.endTime != null }
                        val sMins = devSess.sumOf { it.actualDurationMin ?: 0 }
                        val dMins = dailyUsages.filter { it.deviceAddress == addr && it.date == dStr }.sumOf { it.totalMinutes }
                        maxOf(sMins, dMins)
                    }
                    val isToday = (d == today)
                    if (isToday && totalLiveMinutes > 0) {
                        dayMins += totalLiveMinutes
                    }
                    ChartBarItem(label, dayMins, isToday)
                }
            }
            UsagePeriod.MONTH -> {
                (0..3).map { w ->
                    val wStart = from.plusDays((w * 7).toLong())
                    val wEnd = if (w == 3) today else wStart.plusDays(6)
                    val label = "W${w + 1}"
                    
                    var weekMins = deviceAddrs.sumOf { addr ->
                        val devSess = sessions.filter { s ->
                            s.deviceAddress == addr && s.endTime != null && try {
                                val d = LocalDate.parse(s.date, fmt)
                                !d.isBefore(wStart) && !d.isAfter(wEnd)
                            } catch (_: Exception) { false }
                        }
                        val sMins = devSess.sumOf { it.actualDurationMin ?: 0 }
                        val dMins = dailyUsages.filter { u ->
                            u.deviceAddress == addr && try {
                                val d = LocalDate.parse(u.date, fmt)
                                !d.isBefore(wStart) && !d.isAfter(wEnd)
                            } catch (_: Exception) { false }
                        }.sumOf { it.totalMinutes }
                        maxOf(sMins, dMins)
                    }
                    val isCurrent = !today.isBefore(wStart) && !today.isAfter(wEnd)
                    if (isCurrent && totalLiveMinutes > 0) {
                        weekMins += totalLiveMinutes
                    }
                    ChartBarItem(label, weekMins, isCurrent)
                }
            }
        }

        val totalMins = stats.sumOf { it.totalMinutes }
        val totalSess = stats.sumOf { it.sessionCount }

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
            var dev = app.db.deviceDao().getDevice(address)
            if (dev == null) {
                val conn = app.btMonitor.connectedDevices.value.find { it.address == address }
                if (conn != null) {
                    dev = com.smartbluetoothsleeptracker.data.db.DeviceEntity(
                        address = conn.address,
                        name = conn.name,
                        deviceType = conn.type,
                        isFavorite = false
                    )
                    app.db.deviceDao().upsert(dev)
                } else {
                    return@launch
                }
            }
            app.db.deviceDao().setFavorite(address, !dev.isFavorite)
            loadData(_period.value)
            app.btMonitor.refresh()
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
                app.db.dailyUsageDao().deleteForDevice(addr)
                app.db.sessionDao().deleteForDevice(addr)
                app.db.deviceDao().deleteByAddress(addr)
            }
            null -> {}
        }
        _state.update { it.copy(undoState = UndoActionState()) }
        loadData(_period.value)
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            val session = app.db.sessionDao().getById(id)
            app.db.sessionDao().deleteById(id)
            if (session != null) {
                syncDailyUsageForDeviceAndDate(session.deviceAddress, session.date)
            }
            loadData(_period.value)
        }
    }

    private suspend fun syncDailyUsageForDeviceAndDate(address: String, date: String) {
        val remainingSessions = app.db.sessionDao().sessionsForDateNow(date)
            .filter { it.deviceAddress == address }
        val totalMin = remainingSessions.sumOf { it.actualDurationMin ?: it.plannedDurationMin }
        val count = remainingSessions.size

        if (totalMin > 0 || count > 0) {
            app.db.dailyUsageDao().upsert(
                DailyUsageEntity(
                    date = date,
                    deviceAddress = address,
                    totalMinutes = totalMin,
                    sessionCount = count
                )
            )
        } else {
            app.db.dailyUsageDao().deleteForDeviceAndDate(address, date)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val settings = app.prefs.settings.first()
            // Guard: don't clear if a timer session is actively running
            if (settings.activeSessionId > 0L) return@launch
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

    @android.annotation.SuppressLint("MissingPermission")
    fun testDisconnect(address: String) {
        viewModelScope.launch {
            val btManager = app.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = btManager?.adapter
            val device = try {
                adapter?.getRemoteDevice(address)
            } catch (_: Exception) {
                null
            }
            if (device == null || adapter == null || !adapter.isEnabled) {
                android.widget.Toast.makeText(app, "Bluetooth is off or device not found", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 1. Verify device is ACTUALLY connected before running disconnect test
            val isInitiallyConnected = app.disconnector.isDeviceActuallyConnected(device)
            if (!isInitiallyConnected) {
                android.widget.Toast.makeText(
                    app,
                    "Device is not connected. Please connect ${device.name ?: address} first to test disconnection.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            android.widget.Toast.makeText(app, "Testing disconnect for ${device.name ?: address}...", android.widget.Toast.LENGTH_SHORT).show()
            val result = app.disconnector.disconnectSingleDevice(device, allowSystemDialog = false)
            var verifiedDisconnected = result.success && result.methodUsed != null
            if (!verifiedDisconnected && result.methodsTried.any { it.second }) {
                // Polling confirmation if stack disconnect lag occurred
                for (i in 1..5) {
                    kotlinx.coroutines.delay(500L)
                    val stillConn = app.disconnector.isDeviceActuallyConnected(device)
                    if (!stillConn) {
                        verifiedDisconnected = true
                        break
                    }
                }
            }

            if (verifiedDisconnected && result.methodUsed != null) {
                app.db.deviceDao().setWorkingMethod(device.address, result.methodUsed)
                android.widget.Toast.makeText(app, "✓ Device disconnected successfully!", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(app, "Disconnect test failed. Ensure Bluetooth is active and try again.", android.widget.Toast.LENGTH_LONG).show()
            }
            loadData(_period.value)
        }
    }
}
