package com.attendance.app.core.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.attendance.app.core.data.local.room.AttendanceDao
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AttendanceSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val attendanceDao: AttendanceDao by inject()
    private val httpClient: HttpClient by inject()

    override suspend fun doWork(): Result {
        return try {
            val pendingRecords = attendanceDao.getPendingSyncRecords()
            
            if (pendingRecords.isEmpty()) {
                return Result.success()
            }

            val syncRequests = pendingRecords.map {
                SyncRecordRequest(
                    id = it.id,
                    classId = it.classId,
                    studentId = it.studentId,
                    timestamp = it.timestamp,
                    isPresent = it.isPresent,
                    method = it.method
                )
            }

            val tokenManager: com.attendance.app.auth.data.TokenManager by inject()
            val token = tokenManager.getToken()

            val response = httpClient.post("${com.attendance.app.core.network.Constants.BASE_URL}/attendance/sync") {
                contentType(ContentType.Application.Json)
                if (token != null) {
                    header(io.ktor.http.HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(syncRequests)
            }

            if (response.status.isSuccess()) {
                // Mark as SYNCED
                val syncedIds = pendingRecords.map { it.id }
                attendanceDao.markAsSynced(syncedIds)
                Result.success()
            } else {
                Log.e("AttendanceSyncWorker", "Server rejected sync: ${response.status}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("AttendanceSyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}

@Serializable
data class SyncRecordRequest(
    val id: String,
    val classId: String,
    val studentId: String,
    val timestamp: Long,
    val isPresent: Boolean,
    val method: String
)

@Serializable
data class SyncRecordResponse(
    val id: String,
    val status: String,
    val successfulChecks: Int,
    val requiredChecks: Int,
    val nextCheckIntervalMinutes: Int
)
