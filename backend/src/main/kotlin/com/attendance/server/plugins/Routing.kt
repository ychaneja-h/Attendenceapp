package com.attendance.server.plugins

import com.attendance.server.routes.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*

import com.attendance.server.services.AuthService
import com.attendance.server.services.ClassService
import com.attendance.server.services.AttendanceService

fun Application.configureRouting() {
    val attendanceService = AttendanceService()

    routing {
        get("/health") {
            call.respondText("OK")
        }
        get("/download-app") {
            val file = java.io.File("C:\\Users\\ychan\\.gemini\\antigravity\\brain\\8d8b1530-2f7a-4e07-a4a0-c8a690a97c25\\app-debug.apk")
            call.response.header(io.ktor.http.HttpHeaders.ContentDisposition, "attachment; filename=\"app-debug.apk\"")
            call.respondFile(file)
        }
        authRoutes()
        classRoutes()
        attendanceRoutes(attendanceService)
        userRoutes()
        requestRoutes()
        staticFiles("/uploads", java.io.File("backend/uploads"))
    }
}
