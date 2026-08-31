package com.attendance.app.core.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecordEntity(
    @PrimaryKey
    val id: String,
    val classId: String,
    val studentId: String,
    val timestamp: Long,
    val isPresent: Boolean,
    val method: String, // BLE, WIFI, MANUAL
    val syncStatus: String // PENDING, SYNCED
)
