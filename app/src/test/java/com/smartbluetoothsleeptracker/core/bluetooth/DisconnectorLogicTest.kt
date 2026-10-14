package com.smartbluetoothsleeptracker.core.bluetooth

import com.smartbluetoothsleeptracker.data.db.DisconnectAttemptEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Bluetooth disconnection logic, result aggregation,
 * and reflective method ordering & failure demotion via MethodOrderResolver.
 */
class DisconnectorLogicTest {

    private val availableMethodIds = listOf("a2dp_reflection", "hfp_reflection", "acl_reflection", "system_disable_dialog")

    @Test
    fun testDisconnectResult_aggregation() {
        val resultSuccess = DisconnectResult(
            success = true,
            methodUsed = "a2dp_reflection",
            methodsTried = listOf("a2dp_reflection" to true)
        )

        assertTrue(resultSuccess.success)
        assertEquals("a2dp_reflection", resultSuccess.methodUsed)
        assertEquals(1, resultSuccess.methodsTried.size)
        assertTrue(resultSuccess.methodsTried.first().second)

        val resultFailure = DisconnectResult(
            success = false,
            methodUsed = null,
            methodsTried = listOf("a2dp_reflection" to false, "hfp_reflection" to false)
        )

        assertFalse(resultFailure.success)
        assertEquals(null, resultFailure.methodUsed)
        assertEquals(2, resultFailure.methodsTried.size)
        assertFalse(resultFailure.methodsTried.first().second)
    }

    @Test
    fun testMethodOrderResolver_cachedWorkingMethodPrioritized() {
        val order = MethodOrderResolver.resolveOrder(
            allMethods = availableMethodIds,
            getMethodId = { it },
            recentAttempts = emptyList(),
            cachedMethodId = "hfp_reflection"
        )

        // Cached working method must be placed first
        assertEquals("hfp_reflection", order.first())
        assertEquals(4, order.size)
    }

    @Test
    fun testMethodOrderResolver_threeConsecutiveFailuresDemotesMethod() {
        val attempts = listOf(
            DisconnectAttemptEntity(deviceAddress = "AA:BB:CC:DD:EE:FF", androidVersion = 34, manufacturer = "Google", methodId = "a2dp_reflection", succeeded = false, lastTestedAt = 3000L),
            DisconnectAttemptEntity(deviceAddress = "AA:BB:CC:DD:EE:FF", androidVersion = 34, manufacturer = "Google", methodId = "a2dp_reflection", succeeded = false, lastTestedAt = 2000L),
            DisconnectAttemptEntity(deviceAddress = "AA:BB:CC:DD:EE:FF", androidVersion = 34, manufacturer = "Google", methodId = "a2dp_reflection", succeeded = false, lastTestedAt = 1000L)
        )

        val order = MethodOrderResolver.resolveOrder(
            allMethods = availableMethodIds,
            getMethodId = { it },
            recentAttempts = attempts,
            cachedMethodId = null
        )

        // a2dp_reflection must be demoted to the end of the list after 3 consecutive failures
        assertEquals(listOf("hfp_reflection", "acl_reflection", "system_disable_dialog", "a2dp_reflection"), order)
    }

    @Test
    fun testMethodOrderResolver_twoFailuresDoesNotDemote() {
        val attempts = listOf(
            DisconnectAttemptEntity(deviceAddress = "AA:BB:CC:DD:EE:FF", androidVersion = 34, manufacturer = "Google", methodId = "a2dp_reflection", succeeded = false, lastTestedAt = 2000L),
            DisconnectAttemptEntity(deviceAddress = "AA:BB:CC:DD:EE:FF", androidVersion = 34, manufacturer = "Google", methodId = "a2dp_reflection", succeeded = false, lastTestedAt = 1000L)
        )

        val order = MethodOrderResolver.resolveOrder(
            allMethods = availableMethodIds,
            getMethodId = { it },
            recentAttempts = attempts,
            cachedMethodId = null
        )

        // 2 failures < threshold (3) -> order unchanged
        assertEquals("a2dp_reflection", order.first())
    }

    @Test
    fun testMethodOrderResolver_cachedMethodDemotedIfFailing() {
        val attempts = listOf(
            DisconnectAttemptEntity(deviceAddress = "AA:BB:CC:DD:EE:FF", androidVersion = 34, manufacturer = "Google", methodId = "hfp_reflection", succeeded = false, lastTestedAt = 3000L),
            DisconnectAttemptEntity(deviceAddress = "AA:BB:CC:DD:EE:FF", androidVersion = 34, manufacturer = "Google", methodId = "hfp_reflection", succeeded = false, lastTestedAt = 2000L),
            DisconnectAttemptEntity(deviceAddress = "AA:BB:CC:DD:EE:FF", androidVersion = 34, manufacturer = "Google", methodId = "hfp_reflection", succeeded = false, lastTestedAt = 1000L)
        )

        val order = MethodOrderResolver.resolveOrder(
            allMethods = availableMethodIds,
            getMethodId = { it },
            recentAttempts = attempts,
            cachedMethodId = "hfp_reflection"
        )

        // Demoted despite being previously cached
        assertTrue(order.last() == "hfp_reflection")
    }
}
