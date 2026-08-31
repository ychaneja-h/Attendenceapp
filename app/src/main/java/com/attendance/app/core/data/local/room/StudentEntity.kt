package com.attendance.app.core.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey
    val id: String,
    val classId: String,
    val studentName: String,
    val collegeId: String,
    val status: String,
    val joinedAt: Long,
    val bleMacAddress: String? = null
)
