package com.attendance.server

import com.attendance.server.config.DatabaseConfig
import com.attendance.server.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList

fun main() {
    embeddedServer(
        Netty,
        environment = applicationEngineEnvironment {
            log = org.slf4j.LoggerFactory.getLogger("ktor.application")
            connector {
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080
                host = "0.0.0.0"
            }
            module(Application::module)
        }
    ).start(wait = true)
}

fun Application.module() {
    DatabaseConfig.init()
    com.attendance.server.services.AuthService.seedDefaultUsers()
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureCORS()
    configureRateLimit()
    configureRouting()
    configureMidnightJob()
}

fun Application.configureMidnightJob() {
    kotlinx.coroutines.GlobalScope.launch {
        while (true) {
            val now = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
            val nextMidnight = now.plusDays(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            val delayMs = nextMidnight.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
            
            kotlinx.coroutines.delay(delayMs)
            
            try {
                org.jetbrains.exposed.sql.transactions.transaction {
                    val todayStart = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                    val pastSessions = com.attendance.server.models.AttendanceSessions.selectAll().where { com.attendance.server.models.AttendanceSessions.startedAt less todayStart }
                    val pastSessionIds = pastSessions.map { it[com.attendance.server.models.AttendanceSessions.id] }
                    if (pastSessionIds.isNotEmpty()) {
                        com.attendance.server.models.AttendanceRecords.update({ (com.attendance.server.models.AttendanceRecords.sessionId inList pastSessionIds) and (com.attendance.server.models.AttendanceRecords.finalStatus eq "UNDER_REVIEW") }) {
                            it[finalStatus] = "ABSENT"
                            it[reviewReason] = "Auto-resolved at midnight"
                        }
                        com.attendance.server.models.AttendanceSessions.update({ com.attendance.server.models.AttendanceSessions.id inList pastSessionIds }) {
                            it[sessionStatus] = "COMPLETED"
                        }
                    }
                }
            } catch(e: Exception) { e.printStackTrace() }
        }
    }
}




