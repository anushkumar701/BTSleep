package com.smartbluetoothsleeptracker.data.db

import androidx.room.*

// ── Enums ──────────────────────────────────────────────────────────────

enum class DeviceType {
    EARBUDS, NECKBAND, HOME_THEATRE, PC, SMARTWATCH, WIRED_HEADPHONES, OTHER
}

// ── Entities ───────────────────────────────────────────────────────────

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val address: String,
    val name: String,
    @ColumnInfo(name = "device_type") val deviceType: DeviceType = DeviceType.OTHER,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "working_disconnect_method") val workingDisconnectMethod: String? = null,
    @ColumnInfo(name = "last_connected_at") val lastConnectedAt: Long? = null
)

@Entity(
    tableName = "disconnect_attempts",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["address"],
        childColumns = ["device_address"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("device_address")]
)
data class DisconnectAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "device_address") val deviceAddress: String,
    @ColumnInfo(name = "android_version") val androidVersion: Int,
    val manufacturer: String,
    @ColumnInfo(name = "method_id") val methodId: String,
    val succeeded: Boolean,
    @ColumnInfo(name = "last_tested_at") val lastTestedAt: Long,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null
)

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["address"],
        childColumns = ["device_address"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("device_address")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "device_address") val deviceAddress: String,
    @ColumnInfo(name = "device_name") val deviceName: String = "",
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "planned_duration_min") val plannedDurationMin: Int,
    @ColumnInfo(name = "actual_duration_min") val actualDurationMin: Int? = null,
    @ColumnInfo(name = "disconnect_confirmed") val disconnectConfirmed: Boolean = false,
    @ColumnInfo(name = "extended_minutes") val extendedMinutes: Int = 0,
    val date: String = "" // yyyy-MM-dd for easy queries
)

@Entity(
    tableName = "daily_usage",
    primaryKeys = ["date", "device_address"],
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["address"],
        childColumns = ["device_address"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("device_address")]
)
data class DailyUsageEntity(
    val date: String, // yyyy-MM-dd
    @ColumnInfo(name = "device_address") val deviceAddress: String,
    @ColumnInfo(name = "total_minutes") val totalMinutes: Int = 0,
    @ColumnInfo(name = "session_count") val sessionCount: Int = 0
)

// ── Type Converters ────────────────────────────────────────────────────

class Converters {
    @TypeConverter fun fromDeviceType(t: DeviceType): String = t.name
    @TypeConverter fun toDeviceType(s: String): DeviceType = runCatching { DeviceType.valueOf(s) }.getOrDefault(DeviceType.OTHER)
}
