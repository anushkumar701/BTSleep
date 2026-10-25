package com.smartbluetoothsleeptracker.core.bluetooth

/**
 * Parser for system and companion app notifications that report Bluetooth earbuds
 * and case battery levels (Google Fast Pair, HeyMelody/realme Link, Pixel Buds, Samsung, etc.).
 */
object FastPairNotificationParser {

    data class BatteryParseResult(
        val deviceName: String?,
        val main: Int = -1,
        val left: Int = -1,
        val right: Int = -1,
        val case: Int = -1,
        val isCaseCharging: Boolean? = null,
        val isLeftCharging: Boolean? = null,
        val isRightCharging: Boolean? = null
    ) {
        val hasAnyBattery: Boolean
            get() = (main in 0..100) || (left in 0..100) || (right in 0..100) || (case in 0..100)
    }

    // Matches e.g. "Left 100%", "Left: 80%", "Left - 90%", "L: 100%", "L 100%"
    private val LEFT_REGEX = Regex("""(?i)(?:\bleft\b|\bL\b)[\s:=-]*(\d{1,3})%?""")

    // Matches e.g. "Right 100%", "Right: 80%", "Right - 90%", "R: 100%", "R 100%"
    private val RIGHT_REGEX = Regex("""(?i)(?:\bright\b|\bR\b)[\s:=-]*(\d{1,3})%?""")

    // Matches e.g. "Case 80%", "Case: 80%", "Case - 80%", "Box: 80%", "Case 80% (Charging)"
    private val CASE_REGEX = Regex("""(?i)(?:\bcase\b|\bbox\b)[\s:=-]*(\d{1,3})%?""")
    private val CASE_SHORT_REGEX = Regex("""\bC\s*[:=-]\s*(\d{1,3})%?""")

    // Fallback single device percentage: e.g. "Battery: 80%", "80% battery"
    private val SINGLE_BATTERY_REGEX = Regex("""(?i)(?:\bbattery\b|\blevel\b)[\s:=-]*(\d{1,3})%?""")

    private val CHARGE_KEYWORDS = listOf("charging", "charge", "⚡", "lightning")

    fun parse(
        title: CharSequence?,
        text: CharSequence?,
        bigText: CharSequence? = null,
        tickerText: CharSequence? = null
    ): BatteryParseResult? {
        val titleStr = title?.toString()?.trim().orEmpty()
        val textStr = text?.toString()?.trim().orEmpty()
        val bigTextStr = bigText?.toString()?.trim().orEmpty()
        val tickerStr = tickerText?.toString()?.trim().orEmpty()

        val payload = "$textStr $bigTextStr $tickerStr".trim()
        if (payload.isBlank() && titleStr.isBlank()) return null

        val searchString = "$payload $titleStr"

        var left = extractPercentage(LEFT_REGEX, searchString)
        var right = extractPercentage(RIGHT_REGEX, searchString)
        var case = extractPercentage(CASE_REGEX, searchString)
        if (case == -1) {
            case = extractPercentage(CASE_SHORT_REGEX, searchString)
        }

        var main = -1
        if (left == -1 && right == -1 && case == -1) {
            main = extractPercentage(SINGLE_BATTERY_REGEX, searchString)
        }

        if (left == -1 && right == -1 && case == -1 && main == -1) {
            return null
        }

        val isCaseCharging = if (case != -1 && isSectionCharging(searchString, "case")) true else null
        val isLeftCharging = if (left != -1 && isSectionCharging(searchString, "left")) true else null
        val isRightCharging = if (right != -1 && isSectionCharging(searchString, "right")) true else null

        val cleanName = extractCleanDeviceName(titleStr, tickerStr)

        return BatteryParseResult(
            deviceName = cleanName,
            main = main,
            left = left,
            right = right,
            case = case,
            isCaseCharging = isCaseCharging,
            isLeftCharging = isLeftCharging,
            isRightCharging = isRightCharging
        )
    }

    private fun extractPercentage(regex: Regex, input: String): Int {
        val match = regex.find(input) ?: return -1
        return match.groupValues.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100) ?: -1
    }

    private fun isSectionCharging(text: String, section: String): Boolean {
        val lower = text.lowercase()
        val idx = lower.indexOf(section)
        if (idx == -1) return false
        val window = lower.substring(idx, (idx + 35).coerceAtMost(lower.length))
        return CHARGE_KEYWORDS.any { window.contains(it) }
    }

    private fun extractCleanDeviceName(title: String, ticker: String): String? {
        if (title.isNotBlank()) {
            return title
        }
        if (ticker.isNotBlank()) {
            val batteryIdx = ticker.indexOf(" Battery:", ignoreCase = true)
            if (batteryIdx > 0) {
                return ticker.substring(0, batteryIdx).trim()
            }
        }
        return null
    }

    fun matchesDevice(notificationTitle: String?, deviceName: String): Boolean {
        if (notificationTitle.isNullOrBlank() || deviceName.isBlank()) return false
        val notif = notificationTitle.lowercase().trim()
        val dev = deviceName.lowercase().trim()

        if (notif.contains(dev) || dev.contains(notif)) return true

        val cleanNotif = notif.replace(Regex("""['’]s\b"""), "").replace(Regex("""[^a-z0-9]"""), " ")
        val cleanDev = dev.replace(Regex("""['’]s\b"""), "").replace(Regex("""[^a-z0-9]"""), " ")

        val devWords = cleanDev.split(Regex("""\s+""")).filter { it.length >= 3 }
        if (devWords.isNotEmpty()) {
            val matchCount = devWords.count { cleanNotif.contains(it) }
            if (matchCount >= (devWords.size + 1) / 2) {
                return true
            }
        }

        return false
    }
}
