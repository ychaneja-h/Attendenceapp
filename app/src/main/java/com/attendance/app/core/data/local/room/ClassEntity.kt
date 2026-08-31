package com.attendance.app.core.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val section: String,
    val semester: Int,
    val department: String,
    val academicYear: String,
    val classCode: String,
    val teacherName: String,
    val studentCount: Int,
    val isActive: Boolean
)
