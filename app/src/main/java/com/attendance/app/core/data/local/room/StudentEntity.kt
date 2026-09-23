package com.attendance.app.core.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students", primaryKeys = ["classId", "id"])
data class StudentEntity(
    val id: String,
    val classId: String,
    val studentName: String,
    val collegeId: String,
    val status: String,
    val joinedAt: Long,
    val bleMacAddress: String? = null
)
