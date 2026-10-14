package com.smartbluetoothsleeptracker.core.bluetooth

import com.smartbluetoothsleeptracker.data.db.DisconnectAttemptEntity

/**
 * Pure strategy and ordering logic for Bluetooth disconnect methods and failure demotion.
 */
object MethodOrderResolver {

    const val DEMOTION_FAILURE_THRESHOLD = 3

    /**
     * Orders disconnect methods:
     * 1. Viable cached working method first (if available and not demoted)
     * 2. Other viable methods (fewer than [DEMOTION_FAILURE_THRESHOLD] consecutive failures)
     * 3. Fallback methods (demoted due to 3+ consecutive failures)
     */
    fun <T> resolveOrder(
        allMethods: List<T>,
        getMethodId: (T) -> String,
        recentAttempts: List<DisconnectAttemptEntity>,
        cachedMethodId: String? = null
    ): List<T> {
        val failCounts = allMethods.associate { method ->
            val methodId = getMethodId(method)
            val attempts = recentAttempts.filter { it.methodId == methodId }
            val consecutiveFailures = attempts.takeWhile { !it.succeeded }.size
            methodId to consecutiveFailures
        }

        val available = allMethods.filter { (failCounts[getMethodId(it)] ?: 0) < DEMOTION_FAILURE_THRESHOLD }
        val fallback = allMethods.filter { (failCounts[getMethodId(it)] ?: 0) >= DEMOTION_FAILURE_THRESHOLD }

        val ordered = if (cachedMethodId != null) {
            val cached = available.find { getMethodId(it) == cachedMethodId }
            if (cached != null) {
                listOf(cached) + available.filter { getMethodId(it) != cachedMethodId } + fallback
            } else {
                available + fallback
            }
        } else {
            available + fallback
        }

        return ordered.ifEmpty { allMethods }
    }
}
