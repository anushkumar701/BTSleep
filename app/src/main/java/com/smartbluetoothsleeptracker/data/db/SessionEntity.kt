package com.smartbluetoothsleeptracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceName: String,
    val startTime: Long,   // epoch millis
    val endTime: Long,     // epoch millis
    val duration: Long,    // millis
    val date: String       // "yyyy-MM-dd"
)
