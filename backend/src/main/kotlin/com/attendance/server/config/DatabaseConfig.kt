package com.attendance.server.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.attendance.server.models.*

object DatabaseConfig {
    fun init() {
        val dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/attendance"
        // Ensure SSL is enabled for Supabase
        val finalUrl = if (dbUrl.contains("supabase") && !dbUrl.contains("sslmode")) {
            if (dbUrl.contains("?")) "$dbUrl&sslmode=require" else "$dbUrl?sslmode=require"
        } else {
            dbUrl
        }

        val config = HikariConfig().apply {
            jdbcUrl = finalUrl
            driverClassName = "org.postgresql.Driver"
            username = System.getenv("DB_USER") ?: "postgres"
            password = System.getenv("DB_PASSWORD") ?: "123456"
            maximumPoolSize = 3
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
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
