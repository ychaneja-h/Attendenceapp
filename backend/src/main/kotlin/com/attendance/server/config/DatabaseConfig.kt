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
                var finalUrl = if (dbUrl.contains("supabase") && !dbUrl.contains("sslmode")) {
            if (dbUrl.contains("?")) "$dbUrl&sslmode=require" else "$dbUrl?sslmode=require"
        } else {
            dbUrl
        }
        
        if (!finalUrl.contains("prepareThreshold=0")) {
            finalUrl = if (finalUrl.contains("?")) "$finalUrl&prepareThreshold=0" else "$finalUrl?prepareThreshold=0"
        }

        val config = HikariConfig().apply {
            jdbcUrl = finalUrl
            driverClassName = "org.postgresql.Driver"
            username = System.getenv("DB_USER") ?: "postgres"
            password = requireNotNull(System.getenv("DB_PASSWORD")) { "DB_PASSWORD must be configured" }
            maximumPoolSize = 3
            minimumIdle = 1
            isAutoCommit = false
            addDataSourceProperty("prepareThreshold", "0")
            addDataSourceProperty("preparedStatementCacheQueries", "0")
            addDataSourceProperty("preparedStatementCacheSizeMiB", "0")
            // transactionIsolation removed for Supabase pooler compatibility
            connectionTimeout = 30000
            // A cold JVM can exceed the pooler's short TLS handshake deadline while
            // loading crypto providers. Retry initialization within this bounded window
            // instead of exiting after Hikari's default single cold connection attempt.
            initializationFailTimeout = 60_000L
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
                ApprovalRequests,
                PlanAEvents,
                PlanATimeAnchors
            )
            // Exposed 0.52 does not migrate varchar lengths for existing columns.
            // Long sessions produce receipt IDs containing a UUID, attempt nonce and sequence.
            val eventIdLength = exec("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'plan_a_events' AND column_name = 'id'
            """.trimIndent()) { rows -> if (rows.next()) rows.getInt(1) else 128 } ?: 128
            if (eventIdLength < 128) {
                exec("ALTER TABLE plan_a_events ALTER COLUMN id TYPE VARCHAR(128)")
            }
            val hardeningSql = requireNotNull(DatabaseConfig::class.java.getResource("/db/harden-public.sql")) {
                "Database security migration is missing"
            }.readText()
            exec(hardeningSql)
        }
    }
}
