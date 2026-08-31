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
            call.respondText("OK")
        }
        authRoutes()
        adminRoutes()
        classRoutes()
        attendanceRoutes(attendanceService)
        userRoutes()
        hodRoutes()
        requestRoutes()
        staticFiles("/uploads", java.io.File("uploads"))
    }
}
