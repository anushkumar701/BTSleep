package com.smartbluetoothsleeptracker.data.repository

import com.smartbluetoothsleeptracker.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.every

@OptIn(ExperimentalCoroutinesApi::class)
class TimerManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setup() {
        mockkStatic(android.os.SystemClock::class)
        every { android.os.SystemClock.elapsedRealtime() } returns 1000L
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `startTimer marks timer as running`() = runTest {
        val timerStore = FakeTimerStateStore()
        val timerManager = TimerManager(timerStore)

        timerManager.startTimer(15)

        assertTrue(timerManager.isTimerRunning())
        assertTrue(timerStore.timerEndTime.value != null)
    }

    private class FakeTimerStateStore : TimerStateStore {
        private val timerEndTimeFlow = MutableStateFlow<Long?>(null)
        private val timerPausedFlow = MutableStateFlow<Long?>(null)

        override val timerEndTime: StateFlow<Long?> = timerEndTimeFlow
        override val timerPausedRemainingMillis: StateFlow<Long?> = timerPausedFlow

        override fun setTimerEndTime(timestamp: Long?) {
            timerEndTimeFlow.value = timestamp
        }

        override fun setTimerPausedRemainingMillis(millis: Long?) {
            timerPausedFlow.value = millis
        }
        
        override fun setTimerWallClockEndTime(timestamp: Long?) {}
    }
}
