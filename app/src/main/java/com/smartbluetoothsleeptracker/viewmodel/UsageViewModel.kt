package com.smartbluetoothsleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.BTCurfewApp
import com.smartbluetoothsleeptracker.data.db.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class UsagePeriod { TODAY, WEEK, MONTH }

data class DeviceUsageStat(
    val device: DeviceEntity,
    val totalMinutes: Int,
    val sessionCount: Int
)

data class UsageUiState(
    val period: UsagePeriod = UsagePeriod.WEEK,
    val sessions: List<SessionEntity> = emptyList(),
    val deviceStats: List<DeviceUsageStat> = emptyList(),
    val dailyUsage: List<DailyUsageEntity> = emptyList(),
    val totalMinutes: Int = 0,
    val totalSessions: Int = 0,
    val totalDevices: Int = 0
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BTCurfewApp
    private val _state = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    private val _period = MutableStateFlow(UsagePeriod.WEEK)

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

        val sessions = app.db.sessionDao().sessionsInRangeNow(fromStr, toStr)
        val usage = app.db.dailyUsageDao().usageInRangeNow(fromStr, toStr)

        // Aggregate by device
        val deviceAddrs = (sessions.map { it.deviceAddress } + usage.map { it.deviceAddress }).distinct()
        val stats = deviceAddrs.mapNotNull { addr ->
            val dev = app.db.deviceDao().getDevice(addr) ?: return@mapNotNull null
            val mins = usage.filter { it.deviceAddress == addr }.sumOf { it.totalMinutes }
            val count = sessions.count { it.deviceAddress == addr }
            DeviceUsageStat(dev, mins, count)
        }.sortedByDescending { it.totalMinutes }

        // 7-day chart data (always trailing 7 days)
        val chartFrom = today.minusDays(6).format(fmt)
        val chartUsage = app.db.dailyUsageDao().usageInRangeNow(chartFrom, toStr)

        _state.value = UsageUiState(
            period = period,
            sessions = sessions,
            deviceStats = stats,
            dailyUsage = chartUsage,
            totalMinutes = usage.sumOf { it.totalMinutes },
            totalSessions = sessions.size,
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

    fun resetUsageForDevice(address: String) {
        viewModelScope.launch {
            app.db.dailyUsageDao().deleteForDevice(address)
            app.db.sessionDao().deleteForDevice(address)
            loadData(_period.value)
        }
    }

    fun removeDevice(address: String) {
        viewModelScope.launch {
            app.db.deviceDao().deleteByAddress(address)
            loadData(_period.value)
        }
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
        viewModelScope.launch { loadData(_period.value) }
    }
}
