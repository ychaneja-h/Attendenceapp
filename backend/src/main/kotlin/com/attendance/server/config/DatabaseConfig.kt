package com.attendance.server.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.attendance.server.models.*

object DatabaseConfig {
    fun init() {
        val config = HikariConfig().apply {
            jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/attendance"
            driverClassName = "org.postgresql.Driver"
            username = System.getenv("DB_USER") ?: "postgres"
            password = System.getenv("DB_PASSWORD") ?: "123456"
            maximumPoolSize = 5
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }

        Database.connect(HikariDataSource(config))

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users,
                Devices,
                Departments,
                AcademicYears,
                Classes,
                ClassMembers,
                ClassJoinRequests,
                FaceTemplates,
                FaceReenrollmentRequests,
                AttendanceSessions,
                AttendanceChecks,
                AttendanceRecords,
                AttendanceCheckResults,
                ReviewActions,
                SecurityEvents,
                AuditLogs,
                Notifications,
                SyncQueue,
                ApprovalRequests
            )
        }
    }
}
