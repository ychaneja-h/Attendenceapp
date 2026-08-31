package com.attendance.app.core.network

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.attendance.app.auth.data.TokenManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReportDownloader(private val context: Context) : KoinComponent {
    private val tokenManager: TokenManager by inject()

    fun downloadClassReport(classId: String, month: String) {
        val url = "${Constants.BASE_URL}/reports/class/$classId/csv?month=$month"
        startDownload(url, "ClassReport_${classId}_$month.csv")
    }

    fun downloadStudentReport(classId: String, studentId: String, month: String) {
        val url = "${Constants.BASE_URL}/reports/class/$classId/student/$studentId/csv?month=$month"
        startDownload(url, "StudentReport_${studentId}_$month.csv")
    }

    private fun startDownload(url: String, fileName: String) {
        val token = tokenManager.getToken()
        if (token == null) {
            Toast.makeText(context, "Not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading Report")
                .setDescription("Downloading attendance report")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .addRequestHeader("Authorization", "Bearer $token")

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
