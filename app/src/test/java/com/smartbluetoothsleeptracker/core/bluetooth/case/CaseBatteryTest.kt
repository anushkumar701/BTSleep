package com.smartbluetoothsleeptracker.core.bluetooth.case

import com.smartbluetoothsleeptracker.core.bluetooth.BatteryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseBatteryTest {

    @Test
    fun testAppleProximityPayload_validNonCharging() {
        val payload = byteArrayOf(
            0x07.toByte(), // Type: Proximity Pairing
            0x19.toByte(), // Length
            0x01.toByte(), // Subtype
            0x0E.toByte(), 0x02.toByte(), // Model
            0x00.toByte(), // Status
            0x55.toByte(), // Buds battery (Left/Right)
            0x08.toByte(), // High nibble 0 (not charging), Low nibble 8 (80%)
            0x00.toByte(), 0x00.toByte()
        )
        val result = AppleCaseBatteryProvider.parseAppleProximityPayload(payload)
        assertNotNull(result)
        assertEquals(80, result!!.percent)
        assertEquals(false, result.charging)
        assertTrue(result.seenAtMillis > 0L)
    }

    @Test
    fun testAppleProximityPayload_validCharging() {
        val payload = byteArrayOf(
            0x07.toByte(),
            0x19.toByte(),
            0x01.toByte(),
            0x0E.toByte(), 0x02.toByte(),
            0x00.toByte(),
            0x77.toByte(),
            // High nibble has bit 2 set (0x40 = 0b0100_0000), low nibble = 9 (90%)
            0x49.toByte()
        )
        val result = AppleCaseBatteryProvider.parseAppleProximityPayload(payload)
        assertNotNull(result)
        assertEquals(90, result!!.percent)
        assertEquals(true, result.charging)
    }

    @Test
    fun testAppleProximityPayload_unknownNibble0xF() {
        val payload = byteArrayOf(
            0x07.toByte(),
            0x19.toByte(),
            0x01.toByte(),
            0x0E.toByte(), 0x02.toByte(),
            0x00.toByte(),
            0x77.toByte(),
            0x0F.toByte() // 0xF = unknown
        )
        val result = AppleCaseBatteryProvider.parseAppleProximityPayload(payload)
        assertNull(result)
    }

    @Test
    fun testAppleProximityPayload_invalidLength() {
        val payload = byteArrayOf(0x07.toByte(), 0x19.toByte(), 0x01.toByte())
        val result = AppleCaseBatteryProvider.parseAppleProximityPayload(payload)
        assertNull(result)
    }

    @Test
    fun testAppleProximityPayload_wrongTypeOrSubtype() {
        val wrongType = byteArrayOf(
            0x08.toByte(), 0x19.toByte(), 0x01.toByte(), 0x00, 0x00, 0x00, 0x00, 0x05
        )
        assertNull(AppleCaseBatteryProvider.parseAppleProximityPayload(wrongType))

        val wrongSubtype = byteArrayOf(
            0x07.toByte(), 0x19.toByte(), 0x02.toByte(), 0x00, 0x00, 0x00, 0x00, 0x05
        )
        assertNull(AppleCaseBatteryProvider.parseAppleProximityPayload(wrongSubtype))
    }

    @Test
    fun testGalaxyBudsStatusPayload_extendedStatus() {
        // Frame: SOM=0xFD, lenL=0x15, lenH=0x00, msgId=0x61, followed by 16 payload bytes
        val frame = ByteArray(20)
        frame[0] = 0xFD.toByte()
        frame[1] = 0x15.toByte()
        frame[2] = 0x00.toByte()
        frame[3] = 0x61.toByte() // Extended status
        // Payload offset is index 4. Payload offset 15 is index 19.
        frame[19] = 85.toByte() // 85% case battery

        val result = GalaxyBudsCaseBatteryProvider.parseGalaxyBudsStatusPayload(frame, frame.size)
        assertNotNull(result)
        assertEquals(85, result!!.percent)
        assertEquals(false, result.charging)
    }

    @Test
    fun testGalaxyBudsStatusPayload_chargingFlag() {
        val frame = ByteArray(20)
        frame[0] = 0xFD.toByte()
        frame[1] = 0x15.toByte()
        frame[2] = 0x00.toByte()
        frame[3] = 0x61.toByte()
        frame[19] = 175.toByte() // 175 -> 75% charging

        val result = GalaxyBudsCaseBatteryProvider.parseGalaxyBudsStatusPayload(frame, frame.size)
        assertNotNull(result)
        assertEquals(75, result!!.percent)
        assertEquals(true, result.charging)
    }

    @Test
    fun testGalaxyBudsStatusPayload_invalidSOM() {
        val invalid = byteArrayOf(0x00, 0x15, 0x00, 0x61, 0x50, 0x50, 0x50)
        assertNull(GalaxyBudsCaseBatteryProvider.parseGalaxyBudsStatusPayload(invalid, invalid.size))
    }

    @Test
    fun testBluetoothMonitor_noCaseWhenNoProvider() {
        val info = BatteryInfo(mainLevel = 80)
        assertEquals(80, info.mainLevel)
        assertEquals(-1, info.caseLevel)
        assertNull(info.caseBattery)
        assertEquals(CaseSupport.Unsupported, info.caseSupport)
    }

    @Test
    fun testIPhoneAccevKey2DoesNotSetCase() {
        // In +IPHONEACCEV: key 1 is battery level (0-9 -> 10-100%), key 2 is dock flag (0 or 1).
        // Emulating handler logic:
        val args = arrayOf(1, 7, 2, 1) // key 1 = 7 (80%), key 2 = 1 (docked)
        var info = BatteryInfo()

        var i = 0
        while (i < args.size) {
            val k = args[i]
            val v = if (i + 1 < args.size) args[i + 1] else null
            if (k == 1 && v != null && v in 0..9) {
                val pct = (v + 1) * 10
                info = info.copy(mainLevel = pct)
            }
            // Key 2 dock flag is deliberately ignored for caseLevel
            i += 2
        }

        assertEquals(80, info.mainLevel)
        assertEquals(-1, info.caseLevel)
        assertNull(info.caseBattery)
    }

    @Test
    fun testBatteryInfo_formatSummary_bothBuds() {
        val info = BatteryInfo(mainLevel = 85, leftLevel = 80, rightLevel = 90)
        assertEquals("Left: 80%  •  Right: 90%", info.formatSummary(com.smartbluetoothsleeptracker.data.db.DeviceType.EARBUDS))
    }

    @Test
    fun testBatteryInfo_formatSummary_singleLeftBud() {
        val info = BatteryInfo(mainLevel = 80, leftLevel = 80, rightLevel = -1)
        assertEquals("Left: 80%", info.formatSummary(com.smartbluetoothsleeptracker.data.db.DeviceType.EARBUDS))
    }

    @Test
    fun testBatteryInfo_formatSummary_singleRightBud() {
        val info = BatteryInfo(mainLevel = 90, leftLevel = -1, rightLevel = 90)
        assertEquals("Right: 90%", info.formatSummary(com.smartbluetoothsleeptracker.data.db.DeviceType.EARBUDS))
    }

    @Test
    fun testBatteryInfo_formatSummary_withCase() {
        val info = BatteryInfo(
            mainLevel = 80,
            leftLevel = 80,
            rightLevel = -1,
            caseLevel = 65,
            caseBattery = CaseBattery(percent = 65, charging = false, seenAtMillis = System.currentTimeMillis())
        )
        assertEquals("Left: 80%  •  Case: 65%", info.formatSummary(com.smartbluetoothsleeptracker.data.db.DeviceType.EARBUDS))
    }

    @Test
    fun testBatteryInfo_formatSummary_withChargingCase() {
        val info = BatteryInfo(
            mainLevel = 80,
            leftLevel = 80,
            rightLevel = 85,
            caseLevel = 100,
            caseBattery = CaseBattery(percent = 100, charging = true, seenAtMillis = System.currentTimeMillis())
        )
        assertEquals("Left: 80%  •  Right: 85%  •  Case: 100% ⚡", info.formatSummary(com.smartbluetoothsleeptracker.data.db.DeviceType.EARBUDS))
    }

    @Test
    fun testBatteryInfo_formatSummary_singleDevice() {
        val info = BatteryInfo(mainLevel = 70)
        assertEquals("Battery: 70%", info.formatSummary(com.smartbluetoothsleeptracker.data.db.DeviceType.NECKBAND))
    }

    @Test
    fun testBatteryInfo_formatSummary_unknown() {
        val info = BatteryInfo()
        assertEquals("Connected", info.formatSummary(com.smartbluetoothsleeptracker.data.db.DeviceType.EARBUDS))
    }
}

