package com.smartbluetoothsleeptracker.viewmodel

import com.smartbluetoothsleeptracker.MainDispatcherRule
import com.smartbluetoothsleeptracker.data.db.UsageEntity
import com.smartbluetoothsleeptracker.data.model.DailyUsagePoint
import com.smartbluetoothsleeptracker.data.model.DeviceUsageSummary
import com.smartbluetoothsleeptracker.data.model.HomeUiState
import com.smartbluetoothsleeptracker.data.repository.BluetoothAutomationController
import com.smartbluetoothsleeptracker.data.repository.UsageDataSource
import com.smartbluetoothsleeptracker.worker.SleepTimerScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `dashboard state combines repository flows`() = runTest {
        val dataSource = FakeUsageDataSource()
        val controller = FakeBluetoothAutomationController()
        val scheduler = FakeSleepTimerScheduler()
        val homeViewModel = HomeViewModel(controller, scheduler)
        val viewModel = DashboardViewModel(dataSource, homeViewModel)
        backgroundScope.launch { viewModel.uiState.collect { } }
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(60_000L, state.todayTotal)
        assertEquals(420_000L, state.weekTotal)
        assertEquals(1_200_000L, state.monthTotal)
        assertEquals(2, state.dailyUsagePoints.size)
        assertEquals("Headphones", state.deviceBreakdown.first().deviceName)
        assertEquals(1, state.deviceBreakdown.first().estimatedBatteryImpact)
        assertEquals(2, state.recentHistory.size)
    }

    private class FakeUsageDataSource : UsageDataSource {
        override fun todayTotal(today: LocalDate): Flow<Long> = MutableStateFlow(60_000L)

        override fun weekTotal(today: LocalDate): Flow<Long> = MutableStateFlow(420_000L)

        override fun monthTotal(today: LocalDate): Flow<Long> = MutableStateFlow(1_200_000L)

        override fun dailyUsagePoints(today: LocalDate): Flow<List<DailyUsagePoint>> =
            MutableStateFlow(
                listOf(
                    DailyUsagePoint(today.minusDays(1).toString(), 120_000L),
                    DailyUsagePoint(today.toString(), 300_000L)
                )
            )

        override fun deviceUsageSummary(today: LocalDate): Flow<List<DeviceUsageSummary>> =
            MutableStateFlow(
                listOf(
                    DeviceUsageSummary("Headphones", 360_000L),
                    DeviceUsageSummary("Speaker", 60_000L)
                )
            )

        override fun recentSessions(limit: Int): Flow<List<UsageEntity>> =
            MutableStateFlow(
                listOf(
                    UsageEntity(id = 1, deviceName = "Headphones", startTime = 1_700_000_000_000, endTime = 1_700_000_360_000, duration = 360_000L, date = LocalDate.now().toString()),
                    UsageEntity(id = 2, deviceName = "Speaker", startTime = 1_700_001_000_000, endTime = 1_700_001_060_000, duration = 60_000L, date = LocalDate.now().toString())
                )
            )
            
        override fun volumeSnapshotsSince(since: Long): Flow<List<com.smartbluetoothsleeptracker.data.db.VolumeSnapshotEntity>> = MutableStateFlow(emptyList())

        override suspend fun deleteDeviceUsage(deviceName: String) {}
    }

    private class FakeSleepTimerScheduler : SleepTimerScheduler {
        override fun enqueueSleepTimer(context: android.content.Context, minutes: Long) {}
        override fun cancelSleepTimer(context: android.content.Context) {}
    }

    private class FakeBluetoothAutomationController : BluetoothAutomationController {
        override val homeState = MutableStateFlow(HomeUiState())
        override fun startSleepTimer(minutes: Long) {}
        override fun updateSelectedMinutes(minutes: Long) {}
        override fun clearSleepTimer() {}
        override fun reconcileConnectedDevices() {}
        override fun evaluateBatterySaver() {}
        override fun evaluateSchedule(now: java.time.LocalDateTime) {}
        override fun activateSmartSleepMode(reason: String) = true
        override fun requestBluetoothDisabled(reason: String) = true
        override fun requestBluetoothEnabled(reason: String) = true
        override fun updateBluetoothState() {}
        override fun restoreVolume() {}
    }
}
