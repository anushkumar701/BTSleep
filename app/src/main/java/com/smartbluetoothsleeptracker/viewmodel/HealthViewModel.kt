package com.smartbluetoothsleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.SleepBTApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class HealthRisk { LOW, MODERATE, HIGH }

data class HealthUiState(
    val todayMinutes: Int = 0,
    val weekAvgMinutes: Int = 0,
    val risk: HealthRisk = HealthRisk.LOW,
    val weeklyData: List<Pair<String, Int>> = emptyList(), // dayLabel to minutes
    val streakDays: Int = 0
)

class HealthViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SleepBTApp
    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            val weekAgo = today.minusDays(6)

            val fromDateStr = today.minusDays(90).format(fmt)
            val toDateStr = today.format(fmt)

            // 1. Fetch daily_usage for ear devices over 90 days for streak calculation
            val usageList = app.db.dailyUsageDao().earHealthUsageInRange(fromDateStr, toDateStr)

            // 2. Fetch sessions in date range as fallback/unification
            val sessionsList = app.db.sessionDao().sessionsInRangeNow(fromDateStr, toDateStr)

            // Excluded device types for ear health
            val excludedTypes = setOf("PC", "SMARTWATCH", "HOME_THEATRE")
            val devicesMap = app.db.deviceDao().getAllNow().associateBy { it.address }

            val validSessions = sessionsList.filter { s ->
                val dev = devicesMap[s.deviceAddress]
                dev == null || dev.deviceType.name !in excludedTypes
            }

            // Build 7-day chart (trailing 7 days)
            val weeklyData = (0..6).map { offset ->
                val d = weekAgo.plusDays(offset.toLong())
                val dateStr = d.format(fmt)
                
                // Sum from daily_usage
                val usageMins = usageList.filter { it.date == dateStr }.sumOf { it.totalMinutes }
                // Sum from sessions (if daily_usage was missing or lower)
                val sessionMins = validSessions.filter { it.date == dateStr }.sumOf { it.actualDurationMin ?: 0 }

                val dayMins = maxOf(usageMins, sessionMins)
                val label = if (d == today) "Today"
                else d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                label to dayMins
            }

            val todayMins = weeklyData.last().second
            val weekTotal = weeklyData.sumOf { it.second }
            val weekAvg = if (weeklyData.isNotEmpty()) weekTotal / 7 else 0 // Rolling 7-day average

            // Risk thresholds: LOW < 60m, MODERATE 60m-119m, HIGH >= 120m
            val risk = when {
                weekAvg >= 120 -> HealthRisk.HIGH
                weekAvg >= 60 -> HealthRisk.MODERATE
                else -> HealthRisk.LOW
            }

            // Calculate consecutive low-risk streak (days with <= 60 mins usage)
            // Start from YESTERDAY — today is still in progress and shouldn't be counted prematurely
            var streakCount = 0
            var checkDate = today.minusDays(1)
            var streakActive = true

            while (streakActive) {
                val dStr = checkDate.format(fmt)
                val uMins = usageList.filter { it.date == dStr }.sumOf { it.totalMinutes }
                val sMins = validSessions.filter { it.date == dStr }.sumOf { it.actualDurationMin ?: 0 }
                val dMins = maxOf(uMins, sMins)

                if (dMins > 60) {
                    streakActive = false
                } else {
                    if (dMins > 0) {
                        streakCount++
                    } else if (streakCount > 0) {
                        // 0-usage day does not break streak (e.g. day off)
                    }
                    checkDate = checkDate.minusDays(1)
                    if (today.toEpochDay() - checkDate.toEpochDay() > 90) break
                }
            }

            _state.value = HealthUiState(
                todayMinutes = todayMins,
                weekAvgMinutes = weekAvg,
                risk = risk,
                weeklyData = weeklyData,
                streakDays = streakCount
            )
        }
    }
}
