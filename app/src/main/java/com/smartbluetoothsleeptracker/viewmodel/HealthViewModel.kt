package com.smartbluetoothsleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.SleepBTApp
import com.smartbluetoothsleeptracker.data.db.DailyUsageEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class HealthRisk { LOW, MODERATE, HIGH }

data class HealthUiState(
    val todayMinutes: Int = 0,
    val weekAvgMinutes: Int = 0,
    val risk: HealthRisk = HealthRisk.LOW,
    val weeklyData: List<Pair<String, Int>> = emptyList() // dayLabel to minutes
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

            val usage = app.db.dailyUsageDao().earHealthUsageInRange(
                weekAgo.format(fmt), today.format(fmt)
            )

            // Build 7-day chart
            val weeklyData = (0..6).map { offset ->
                val d = weekAgo.plusDays(offset.toLong())
                val dateStr = d.format(fmt)
                val dayMins = usage.filter { it.date == dateStr }.sumOf { it.totalMinutes }
                val label = if (d == today) "Today"
                else d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                label to dayMins
            }

            val todayMins = weeklyData.last().second
            val weekTotal = weeklyData.sumOf { it.second }
            val weekAvg = if (weeklyData.isNotEmpty()) weekTotal / weeklyData.size else 0

            val risk = when {
                weekAvg >= 120 -> HealthRisk.HIGH
                weekAvg >= 60 -> HealthRisk.MODERATE
                else -> HealthRisk.LOW
            }

            _state.value = HealthUiState(
                todayMinutes = todayMins,
                weekAvgMinutes = weekAvg,
                risk = risk,
                weeklyData = weeklyData
            )
        }
    }
}
