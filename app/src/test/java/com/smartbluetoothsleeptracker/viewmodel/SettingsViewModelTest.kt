package com.smartbluetoothsleeptracker.viewmodel

import com.smartbluetoothsleeptracker.MainDispatcherRule
import com.smartbluetoothsleeptracker.data.model.HomeUiState
import com.smartbluetoothsleeptracker.data.model.SettingsState
import com.smartbluetoothsleeptracker.data.model.ThemeMode
import com.smartbluetoothsleeptracker.data.repository.BluetoothAutomationController
import com.smartbluetoothsleeptracker.data.repository.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `settings updates propagate through ui state`() = runTest {
        val store = FakeSettingsStore()
        val controller = FakeBluetoothAutomationController()
        val viewModel = SettingsViewModel(store, controller)
        backgroundScope.launch { viewModel.uiState.collect { } }

        viewModel.updateBatterySaver(true)
        viewModel.updateAutoTimer(true)
        viewModel.updateScheduleEnabled(true)
        viewModel.updateScheduleOnMinutes(480)
        viewModel.updateScheduleOffMinutes(540)
        viewModel.updateForegroundService(false)
        viewModel.updateNotifications(false)
        viewModel.updateThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.batterySaverEnabled)
        assertTrue(state.autoTimerEnabled)
        assertTrue(state.scheduleEnabled)
        assertEquals(480, state.scheduleOnMinutes)
        assertEquals(540, state.scheduleOffMinutes)
        assertEquals(false, state.foregroundServiceEnabled)
        assertEquals(false, state.notificationsEnabled)
        assertEquals(ThemeMode.DARK, state.themeMode)
    }

    @Test
    fun `manual bluetooth actions delegate to controller`() = runTest {
        val controller = FakeBluetoothAutomationController()
        val viewModel = SettingsViewModel(FakeSettingsStore(), controller)
        backgroundScope.launch { viewModel.uiState.collect { } }

        viewModel.requestBluetoothOn()
        viewModel.requestBluetoothOff()

        assertEquals("Manual action from Settings.", controller.lastEnableReason)
        assertEquals("Manual action from Settings.", controller.lastDisableReason)
    }

    private class FakeSettingsStore : SettingsStore {
        private val state = MutableStateFlow(SettingsState())
        override val settingsState: StateFlow<SettingsState> = state

        override fun updateBatterySaver(enabled: Boolean) {
            state.value = state.value.copy(batterySaverEnabled = enabled)
        }

        override fun updateAutoTimer(enabled: Boolean) {
            state.value = state.value.copy(autoTimerEnabled = enabled)
        }

        override fun updateIdleMinutes(minutes: Int) {
            state.value = state.value.copy(idleMinutes = minutes)
        }

        override fun updateScheduleEnabled(enabled: Boolean) {
            state.value = state.value.copy(scheduleEnabled = enabled)
        }

        override fun updateScheduleOnMinutes(minutes: Int) {
            state.value = state.value.copy(scheduleOnMinutes = minutes)
        }

        override fun updateScheduleOffMinutes(minutes: Int) {
            state.value = state.value.copy(scheduleOffMinutes = minutes)
        }

        override fun updateForegroundService(enabled: Boolean) {
            state.value = state.value.copy(foregroundServiceEnabled = enabled)
        }

        override fun updateNotifications(enabled: Boolean) {
            state.value = state.value.copy(notificationsEnabled = enabled)
        }

        override fun updateThemeMode(themeMode: ThemeMode) {
            state.value = state.value.copy(themeMode = themeMode)
        }
        
        override fun setOnboardingCompleted(completed: Boolean) {}
    }

    private class FakeBluetoothAutomationController : BluetoothAutomationController {
        override val homeState: StateFlow<HomeUiState> = MutableStateFlow(HomeUiState())
        var lastEnableReason: String? = null
        var lastDisableReason: String? = null

        override fun startSleepTimer(minutes: Long) = Unit

        override fun updateSelectedMinutes(minutes: Long) = Unit

        override fun clearSleepTimer() = Unit

        override fun reconcileConnectedDevices() = Unit

        override fun evaluateBatterySaver() = Unit

        override fun evaluateSchedule(now: LocalDateTime) = Unit

        override fun activateSmartSleepMode(reason: String): Boolean {
            lastDisableReason = reason
            return true
        }

        override fun requestBluetoothDisabled(reason: String): Boolean {
            lastDisableReason = reason
            return true
        }

        override fun requestBluetoothEnabled(reason: String): Boolean {
            lastEnableReason = reason
            return true
        }

        override fun updateBluetoothState() {}
        override fun restoreVolume() {}
    }
}
