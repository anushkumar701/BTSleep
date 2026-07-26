package com.smartbluetoothsleeptracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartbluetoothsleeptracker.data.db.AppDatabase
import com.smartbluetoothsleeptracker.data.db.SessionEntity
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Status labels per spec: Safe / Moderate / High */
enum class UsageStatus { SAFE, MODERATE, HIGH }

data class HealthUiState(
    val todayMinutes: Int = 0,
    val status: UsageStatus = UsageStatus.SAFE
)

class HealthViewModel(private val db: AppDatabase) : ViewModel() {

    val state: StateFlow<HealthUiState> = db.sessionDao().getAllSessions()
        .map { sessions -> computeHealth(sessions) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HealthUiState())

    private fun computeHealth(sessions: List<SessionEntity>): HealthUiState {
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val todayMinutes = sessions
            .filter { it.date == todayStr }
            .sumOf { it.duration / 60_000L }
            .toInt()

        val status = when {
            todayMinutes <= 60 -> UsageStatus.SAFE
            todayMinutes <= 120 -> UsageStatus.MODERATE
            else -> UsageStatus.HIGH
        }

        return HealthUiState(
            todayMinutes = todayMinutes,
            status = status
        )
    }
}
