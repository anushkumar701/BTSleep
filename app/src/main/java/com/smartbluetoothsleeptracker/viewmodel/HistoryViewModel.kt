package com.smartbluetoothsleeptracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.data.db.AppDatabase
import com.smartbluetoothsleeptracker.data.db.SessionEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class HistoryTab { TODAY, WEEK, MONTH }

data class DeviceStat(
    val deviceName: String,
    val totalDuration: Long,   // millis
    val sessionCount: Int,
    val lastUsed: Long         // epoch millis
)

data class HistoryUiState(
    val sessions: List<SessionEntity> = emptyList(),
    val deviceStats: List<DeviceStat> = emptyList(),
    val todayTotal: Long = 0L,
    val weekTotal: Long = 0L,
    val monthTotal: Long = 0L,
    val totalSessions: Int = 0,
    val selectedTab: HistoryTab = HistoryTab.WEEK
)

class HistoryViewModel(private val db: AppDatabase) : ViewModel() {

    private val _tab = MutableStateFlow(HistoryTab.WEEK)

    val state: StateFlow<HistoryUiState> = combine(
        db.sessionDao().getAllSessions(),
        _tab
    ) { allSessions, tab ->
        val today    = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val weekStr  = today.minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val monthStr = today.minusDays(29).format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Filter by selected tab
        val filtered = when (tab) {
            HistoryTab.TODAY -> allSessions.filter { it.date == todayStr }
            HistoryTab.WEEK  -> allSessions.filter { it.date >= weekStr }
            HistoryTab.MONTH -> allSessions.filter { it.date >= monthStr }
        }

        // Device stats from filtered
        val deviceStats = filtered
            .groupBy { it.deviceName }
            .map { (name, sessions) ->
                DeviceStat(
                    deviceName = name,
                    totalDuration = sessions.sumOf { it.duration },
                    sessionCount = sessions.size,
                    lastUsed = sessions.maxOf { it.endTime }
                )
            }
            .sortedByDescending { it.totalDuration }

        HistoryUiState(
            sessions      = filtered,
            deviceStats   = deviceStats,
            todayTotal    = allSessions.filter { it.date == todayStr }.sumOf { it.duration },
            weekTotal     = allSessions.filter { it.date >= weekStr }.sumOf { it.duration },
            monthTotal    = allSessions.filter { it.date >= monthStr }.sumOf { it.duration },
            totalSessions = allSessions.size,
            selectedTab   = tab
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState())

    fun setTab(tab: HistoryTab) { _tab.value = tab }

    fun deleteSession(id: Int)            = viewModelScope.launch { db.sessionDao().deleteById(id) }
    fun deleteDeviceHistory(name: String) = viewModelScope.launch { db.sessionDao().deleteByDeviceName(name) }
    fun resetDeviceTiming(name: String)   = viewModelScope.launch { db.sessionDao().deleteByDeviceName(name) }
    fun clearAll()                        = viewModelScope.launch { db.sessionDao().clearAll() }
}
