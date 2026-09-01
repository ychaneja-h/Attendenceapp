package com.attendance.app.core.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
        @Query("SELECT * FROM attendance_records WHERE classId = :classId")
    fun getRecordsForClass(classId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND timestamp >= :since ORDER BY timestamp DESC")
    fun getRecordsForClassSince(classId: String, since: Long): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncRecords(): List<AttendanceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AttendanceRecordEntity)

    @Query("UPDATE attendance_records SET syncStatus = 'SYNCED' WHERE id IN (:recordIds)")
    suspend fun markAsSynced(recordIds: List<String>)
}



