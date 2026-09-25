package com.attendance.app.auth.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/** Prefer the server's safe, actionable message; do not show raw proxy HTML or machine codes. */
internal fun authErrorMessage(body: String, status: Int, fallback: String): String {
    if (status >= 500) return "The service is temporarily unavailable. Please try again shortly."
    if (status == 429) return "Too many attempts. Please wait a moment and try again."
    val error = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return fallback
    val code = runCatching { error["error"]?.jsonPrimitive?.contentOrNull }.getOrNull()
    if (code == "DEVICE_MISMATCH" || code == "PENDING_APPROVAL") return code
    return runCatching { error["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        ?.trim()?.takeIf { it.isNotEmpty() && it.length <= 500 } ?: fallback
}

internal fun authRequestFailure(error: Exception): Exception = Exception(
    if (error is IOException) "Could not reach the server. Check your internet connection and try again."
    else "The server response could not be processed. Please try again."
)
