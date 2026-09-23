package com.attendance.app.core.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE classId = :classId")
    suspend fun studentsForClass(classId: String): List<StudentEntity>

    @Query("SELECT * FROM students")
    fun observeAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE classId = :classId")
    fun getStudentsForClass(classId: String): Flow<List<StudentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Query("DELETE FROM students WHERE classId = :classId")
    suspend fun deleteStudentsForClass(classId: String)

    @Transaction
    suspend fun replaceStudentsForClass(classId: String, students: List<StudentEntity>) {
        deleteStudentsForClass(classId)
        insertStudents(students)
    }
}
