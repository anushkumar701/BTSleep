package com.smartbluetoothsleeptracker.core.bluetooth.case

import android.bluetooth.BluetoothDevice

data class CaseBattery(
    val percent: Int,
    val charging: Boolean?,
    val seenAtMillis: Long
)

sealed interface CaseSupport {
    object Supported : CaseSupport
    object Unsupported : CaseSupport
}

interface CaseBatteryProvider {
    fun supports(device: BluetoothDevice, name: String): Boolean
    suspend fun read(device: BluetoothDevice): CaseBattery?   // null = not known right now
}
