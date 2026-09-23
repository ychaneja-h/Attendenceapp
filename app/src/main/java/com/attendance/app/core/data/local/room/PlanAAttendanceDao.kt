package com.attendance.app.core.data.local.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "plan_a_events")
data class PlanAEventEntity(
    @PrimaryKey val id: String,
    val actorId: String,
    val classId: String,
    val sessionId: String?,
    val attemptId: String?,
    val kind: String,
    val wallTimeMs: Long,
    val payload: String,
    val syncStatus: String = "PENDING",
    val rejectionReason: String? = null
)

@Entity(tableName = "plan_a_state")
data class PlanAStateEntity(@PrimaryKey val key: String, val payload: String)

@Entity(tableName = "plan_a_snapshots", primaryKeys = ["ownerId", "sessionId"])
data class PlanASnapshotEntity(val ownerId: String, val sessionId: String, val payload: String)

@Dao
interface PlanAAttendanceDao {
    @Query("SELECT * FROM plan_a_events ORDER BY wallTimeMs, id")
    fun observeEvents(): Flow<List<PlanAEventEntity>>

    @Query("SELECT * FROM plan_a_state")
    fun observeState(): Flow<List<PlanAStateEntity>>

    @Query("SELECT * FROM plan_a_snapshots")
    fun observeSnapshots(): Flow<List<PlanASnapshotEntity>>

    @Query("SELECT * FROM plan_a_snapshots WHERE ownerId = :ownerId AND sessionId IN (:ids)")
    suspend fun snapshotsForSessions(ownerId: String, ids: List<String>): List<PlanASnapshotEntity>

    @Query("SELECT * FROM plan_a_events WHERE actorId = :actorId ORDER BY wallTimeMs, id")
    suspend fun eventsForActor(actorId: String): List<PlanAEventEntity>

    @Query("SELECT * FROM plan_a_events WHERE actorId = :actorId AND syncStatus = 'PENDING' ORDER BY wallTimeMs, id LIMIT 500")
    suspend fun pendingEvents(actorId: String): List<PlanAEventEntity>

    @Query("SELECT * FROM plan_a_state WHERE `key` = :key")
    suspend fun state(key: String): PlanAStateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: PlanAEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(state: PlanAStateEntity)

    @Query("DELETE FROM plan_a_state WHERE `key` = :key")
    suspend fun deleteState(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSnapshots(snapshots: List<PlanASnapshotEntity>)

    @Query("DELETE FROM plan_a_snapshots WHERE ownerId = :ownerId AND sessionId IN (:ids)")
    suspend fun deleteSnapshots(ownerId: String, ids: List<String>)

    @Query("UPDATE plan_a_events SET syncStatus = 'SYNCED', rejectionReason = NULL WHERE id = :id")
    suspend fun acknowledge(id: String)

    @Query("UPDATE plan_a_events SET syncStatus = 'REJECTED', rejectionReason = :reason WHERE id = :id")
    suspend fun reject(id: String, reason: String)
}
