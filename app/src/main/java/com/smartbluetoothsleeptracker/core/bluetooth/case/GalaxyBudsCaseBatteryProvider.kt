package com.smartbluetoothsleeptracker.core.bluetooth.case

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class GalaxyBudsCaseBatteryProvider(
    private val context: Context
) : CaseBatteryProvider {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("2e73a4ad-332d-41fc-90e2-16bef06523f2")
        const val SOM_DD = 0xDD.toByte()
        const val SOM_FD = 0xFD.toByte()

        /**
         * Parses status packets from Samsung Galaxy Buds SPP communication.
         * Accommodates both 0xDD (legacy) and 0xFD (standard framed) Start-of-Message headers.
         * Message IDs 0x60 (Status) and 0x61 (Extended Status Update).
         */
        fun parseGalaxyBudsStatusPayload(data: ByteArray, length: Int = data.size): CaseBattery? {
            if (length < 6) return null

            var somIndex = -1
            for (i in 0 until length - 4) {
                if (data[i] == SOM_DD || data[i] == SOM_FD) {
                    somIndex = i
                    break
                }
            }
            if (somIndex == -1) return null

            var msgId = -1
            var payloadOffset = -1
            if (data[somIndex] == SOM_DD) {
                msgId = data[somIndex + 1].toInt() and 0xFF
                payloadOffset = somIndex + 4
            } else if (data[somIndex] == SOM_FD) {
                msgId = data[somIndex + 3].toInt() and 0xFF
                payloadOffset = somIndex + 4
            }

            if (msgId !in listOf(0x60, 0x61, 0x62)) {
                if (somIndex + 1 < length && (data[somIndex + 1].toInt() and 0xFF) in listOf(0x60, 0x61, 0x62)) {
                    payloadOffset = somIndex + 2
                } else {
                    return null
                }
            }

            if (payloadOffset >= length) return null

            var casePercent = -1
            var isCharging: Boolean? = null
            val available = length - payloadOffset

            // Galaxy Buds+ / Live / Pro / 2 / 2 Pro extended status (0x61)
            // Case battery is reported at payload offset 15
            if (available > 15) {
                val raw = data[payloadOffset + 15].toInt() and 0xFF
                if (raw in 0..100) {
                    casePercent = raw
                    isCharging = false
                } else if (raw in 101..200) {
                    casePercent = raw - 100
                    isCharging = true
                }
            }

            // Original Galaxy Buds (2019) at payload offset 6
            if (casePercent == -1 && available > 6) {
                val raw = data[payloadOffset + 6].toInt() and 0xFF
                if (raw in 0..100) {
                    casePercent = raw
                    isCharging = false
                } else if (raw in 101..200) {
                    casePercent = raw - 100
                    isCharging = true
                }
            }

            // Simple status fallback (0x60) at payload offset 2
            if (casePercent == -1 && available >= 3) {
                val raw = data[payloadOffset + 2].toInt() and 0xFF
                if (raw in 0..100) {
                    casePercent = raw
                    isCharging = false
                }
            }

            if (casePercent in 0..100) {
                return CaseBattery(
                    percent = casePercent,
                    charging = isCharging,
                    seenAtMillis = System.currentTimeMillis()
                )
            }
            return null
        }
    }

    override fun supports(device: BluetoothDevice, name: String): Boolean {
        val devName = name.ifBlank {
            try { device.name ?: "" } catch (_: SecurityException) { "" }
        }
        return devName.contains("Galaxy Buds", ignoreCase = true) ||
                devName.contains("Buds Live", ignoreCase = true) ||
                devName.contains("Buds Pro", ignoreCase = true) ||
                devName.contains("Buds2", ignoreCase = true) ||
                devName.contains("Buds3", ignoreCase = true) ||
                devName.contains("Gear IconX", ignoreCase = true)
    }

    @SuppressLint("MissingPermission")
    override suspend fun read(device: BluetoothDevice): CaseBattery? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasConnect) return@withContext null
        }

        withTimeoutOrNull(3000L) {
            var socket: android.bluetooth.BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val inputStream = socket.inputStream
                val buffer = ByteArray(256)
                val bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    parseGalaxyBudsStatusPayload(buffer, bytesRead)
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { socket?.close() }
            }
        }
    }
}
