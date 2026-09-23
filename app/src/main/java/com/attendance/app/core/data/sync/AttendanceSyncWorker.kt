package com.attendance.app.core.data.sync
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.attendance.app.core.data.attendance.AttendanceRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AttendanceSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams), KoinComponent {
    private val repository: AttendanceRepository by inject()
    override suspend fun doWork(): Result = if (repository.synchronize()) Result.success() else Result.retry()
}
