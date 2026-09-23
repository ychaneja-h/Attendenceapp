package com.attendance.app.face

import android.content.Context
import com.attendance.app.core.network.Constants
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.json.JSONArray
import org.json.JSONObject

/** Cache only the server's active template; request history never activates local face data. */
suspend fun syncApprovedFace(context: Context, httpClient: HttpClient): String {
    val prefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
    val pendingAtStart = prefs.getString("pending_face_embedding", null)
    val requestsResponse = httpClient.get("${Constants.BASE_URL}/requests/me")
    check(requestsResponse.status.isSuccess()) { "Unable to check face approval." }
    val requests = JSONArray(requestsResponse.bodyAsText())
    var serverPending = false
    var pendingWasRejected = false
    for (index in 0 until requests.length()) {
        val request = requests.getJSONObject(index)
        if (request.optString("requestType") != "FACE_REGISTRATION") continue
        if (request.optString("status") == "PENDING") serverPending = true
        if (request.optString("status") == "REJECTED" && pendingAtStart != null &&
            sameFaceTemplate(request.optString("requestedData"), pendingAtStart)) pendingWasRejected = true
    }
    if (serverPending) prefs.edit().putBoolean("face_approval_pending", true).apply()

    val faceResponse = httpClient.get("${Constants.BASE_URL}/users/me/face")
    check(faceResponse.status.isSuccess()) { "Unable to refresh the approved face." }
    val approved = JSONObject(faceResponse.bodyAsText()).optString("faceEmbedding", "")
    if (approved.isNotBlank()) {
        val values = JSONArray(approved)
        require(values.length() > 0 && (0 until values.length()).all { values.getDouble(it).isFinite() }) {
            "The approved face data is invalid."
        }
    }
    // A registration submitted during these network calls must retain its pending gate.
    if (prefs.getString("pending_face_embedding", null) != pendingAtStart) return "Face Status: Pending Approval"
    val pendingApproved = pendingAtStart != null && sameFaceTemplate(approved, pendingAtStart)
    val localPendingUnresolved = pendingAtStart != null && !pendingApproved && !pendingWasRejected
    val pending = serverPending || localPendingUnresolved
    prefs.edit().apply {
        if (approved.isNotBlank()) putString("registered_face_embedding", approved)
        else remove("registered_face_embedding")
        putBoolean("face_approval_pending", pending)
        if (pendingApproved || pendingWasRejected) remove("pending_face_embedding")
    }.apply()
    return when {
        serverPending -> "Face Status: Pending Teacher Approval"
        localPendingUnresolved -> "Face Status: Saved request awaiting upload or approval"
        approved.isNotBlank() -> "Face Status: Approved"
        else -> "Face Status: No Face Registered"
    }
}

private fun sameFaceTemplate(first: String, second: String): Boolean = runCatching {
    val a = JSONArray(first)
    val b = JSONArray(second)
    a.length() > 0 && a.length() == b.length() && (0 until a.length()).all { a.getDouble(it) == b.getDouble(it) }
}.getOrDefault(false)
