package com.smartbluetoothsleeptracker.viewmodel

import android.content.Context
import android.content.ContextWrapper
import com.smartbluetoothsleeptracker.MainDispatcherRule
import com.smartbluetoothsleeptracker.data.model.HomeUiState
import com.smartbluetoothsleeptracker.data.repository.BluetoothAutomationController
import com.smartbluetoothsleeptracker.worker.SleepTimerScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `startSleepTimer updates controller scheduler and ui state`() = runTest {
        val controller = FakeBluetoothAutomationController()
        val scheduler = FakeSleepTimerScheduler()
        val viewModel = HomeViewModel(controller, scheduler)

        viewModel.selectMinutes(30)
        viewModel.startSleepTimer(ContextWrapper(null))
        advanceUntilIdle()

        assertEquals(30L, controller.startedMinutes)
        assertEquals(30L, scheduler.enqueuedMinutes)
        assertTrue(viewModel.uiState.value.timerRunning)
        assertEquals(30L, viewModel.uiState.value.selectedMinutes)
    }

    @Test
    fun `cancelSleepTimer clears timer and scheduler`() = runTest {
        val controller = FakeBluetoothAutomationController()
        val scheduler = FakeSleepTimerScheduler()
        val viewModel = HomeViewModel(controller, scheduler)

        viewModel.cancelSleepTimer(ContextWrapper(null))
        advanceUntilIdle()

        assertTrue(controller.cleared)
        assertTrue(scheduler.cancelled)
        assertFalse(viewModel.uiState.value.timerRunning)
    }

    private class FakeSleepTimerScheduler : SleepTimerScheduler {
        var enqueuedMinutes: Long? = null
        var cancelled = false

        override fun enqueueSleepTimer(context: Context, minutes: Long) {
            enqueuedMinutes = minutes
        }

        override fun cancelSleepTimer(context: Context) {
            cancelled = true
        }
    }

    private class FakeBluetoothAutomationController : BluetoothAutomationController {
        private val state = MutableStateFlow(HomeUiState())
        override val homeState: StateFlow<HomeUiState> = state
        var startedMinutes: Long? = null
        var cleared = false

        override fun startSleepTimer(minutes: Long) {
            startedMinutes = minutes
            state.value = state.value.copy(
                timerRunning = true,
                timerEndsAt = System.currentTimeMillis() + minutes * 60_000L
            )
        }

        override fun updateSelectedMinutes(minutes: Long) {
            state.value = state.value.copy(selectedMinutes = minutes)
        }

        override fun clearSleepTimer() {
            cleared = true
            state.value = state.value.copy(timerRunning = false, timerEndsAt = null)
        }

        override fun reconcileConnectedDevices() = Unit

        override fun evaluateBatterySaver() = Unit

        override fun evaluateSchedule(now: LocalDateTime) = Unit

        override fun activateSmartSleepMode(reason: String) = true

        override fun requestBluetoothDisabled(reason: String): Boolean = true
        override fun requestBluetoothEnabled(reason: String): Boolean = true
        override fun updateBluetoothState() {}
        override fun restoreVolume() {}
    }
}
