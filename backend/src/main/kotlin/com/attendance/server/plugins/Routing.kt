package com.attendance.server.plugins

import com.attendance.server.routes.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*

import com.attendance.server.services.AttendanceService

fun Application.configureRouting() {
    val attendanceService = AttendanceService()

    routing {
        get("/health") {
            call.response.headers.append("X-App-Version", "1.4.1")
            call.respondText("OK")
        }
        authRoutes()
        adminRoutes()
        classRoutes()
        attendanceRoutes(attendanceService)
        planAAttendanceRoutes()
        userRoutes()
        hodRoutes()
        requestRoutes()
        staticFiles("/uploads", java.io.File("uploads"))
    }
}
