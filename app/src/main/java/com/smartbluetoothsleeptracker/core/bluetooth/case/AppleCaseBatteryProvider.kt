package com.smartbluetoothsleeptracker.core.bluetooth.case

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AppleCaseBatteryProvider(
    private val context: Context
) : CaseBatteryProvider {

    companion object {
        const val APPLE_MANUFACTURER_ID = 0x004C
        const val MIN_RSSI_THRESHOLD = -70

        /**
         * Parses Apple's "proximity pairing" manufacturer advertisement payload.
         * Data layout (after 2-byte company ID 0x004C):
         * - Byte 0: Type = 0x07 (Proximity Pairing)
         * - Byte 1: Length
         * - Byte 2: Subtype = 0x01
         * - Bytes 3-4: Model ID
         * - Byte 5: Status
         * - Byte 6: Left / Right battery nibbles
         * - Byte 7: High nibble = charging flags (bit 2 is case charging),
         *           Low nibble = case battery (0..10 -> 0..100%, 0xF = unknown)
         */
        fun parseAppleProximityPayload(data: ByteArray?): CaseBattery? {
            if (data == null || data.size < 8) return null
            if (data[0] != 0x07.toByte()) return null
            if (data[2] != 0x01.toByte()) return null

            val byte7 = data[7].toInt() and 0xFF
            val caseNibble = byte7 and 0x0F
            // Nibble values 0..10 represent 0%..100%. 0xF means unknown.
            if (caseNibble !in 0..10) return null

            val percent = caseNibble * 10
            // Bit 2 of the high nibble indicates case charging status
            val isCharging = ((byte7 shr 4) and 0x04) != 0

            return CaseBattery(
                percent = percent,
                charging = isCharging,
                seenAtMillis = System.currentTimeMillis()
            )
        }
    }

    override fun supports(device: BluetoothDevice, name: String): Boolean {
        val devName = name.ifBlank {
            try { device.name ?: "" } catch (_: SecurityException) { "" }
        }
        return devName.contains("AirPods", ignoreCase = true) ||
                devName.contains("Beats", ignoreCase = true)
    }

    @SuppressLint("MissingPermission")
    override suspend fun read(device: BluetoothDevice): CaseBattery? = withContext(Dispatchers.IO) {
        // BLE proximity scanning is restricted to Android 12+ (API 31+) with BLUETOOTH_SCAN
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@withContext null

        val hasScanPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasScanPermission) return@withContext null

        val btManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = btManager?.adapter ?: return@withContext null
        if (!adapter.isEnabled) return@withContext null
        val scanner = adapter.bluetoothLeScanner ?: return@withContext null

        val bestCandidate = java.util.concurrent.atomic.AtomicReference<CaseBattery?>(null)
        val highestRssi = java.util.concurrent.atomic.AtomicInteger(Int.MIN_VALUE)

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result == null) return
                val rssi = result.rssi
                if (rssi < MIN_RSSI_THRESHOLD) return

                val scanRecord = result.scanRecord ?: return
                val rawData = scanRecord.getManufacturerSpecificData(APPLE_MANUFACTURER_ID)
                val parsed = parseAppleProximityPayload(rawData)

                if (parsed != null && rssi > highestRssi.get()) {
                    highestRssi.set(rssi)
                    bestCandidate.set(parsed)
                }
            }
        }

        val filter = ScanFilter.Builder()
            .setManufacturerData(APPLE_MANUFACTURER_ID, byteArrayOf())
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            scanner.startScan(listOf(filter), settings, callback)
            kotlinx.coroutines.delay(8000L)
        } catch (_: Exception) {
        } finally {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {}
        }
        return@withContext bestCandidate.get()
    }
}
