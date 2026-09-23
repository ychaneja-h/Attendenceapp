package com.attendance.app.core.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ClassEntity::class,
        StudentEntity::class,
        AttendanceRecordEntity::class,
        PlanAEventEntity::class,
        PlanAStateEntity::class,
        PlanASnapshotEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun planAAttendanceDao(): PlanAAttendanceDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE classes ADD COLUMN memberStatus TEXT NOT NULL DEFAULT 'PENDING'")
            }
        }
        /** Preserve all v1 records. New sessions never reuse the ambiguous legacy day records. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS students_v2 (id TEXT NOT NULL, classId TEXT NOT NULL, studentName TEXT NOT NULL, collegeId TEXT NOT NULL, status TEXT NOT NULL, joinedAt INTEGER NOT NULL, bleMacAddress TEXT, PRIMARY KEY(classId, id))")
                db.execSQL("INSERT INTO students_v2 SELECT id, classId, studentName, collegeId, status, joinedAt, bleMacAddress FROM students")
                db.execSQL("DROP TABLE students")
                db.execSQL("ALTER TABLE students_v2 RENAME TO students")
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_a_events (id TEXT NOT NULL PRIMARY KEY, actorId TEXT NOT NULL, classId TEXT NOT NULL, sessionId TEXT, attemptId TEXT, kind TEXT NOT NULL, wallTimeMs INTEGER NOT NULL, payload TEXT NOT NULL, syncStatus TEXT NOT NULL, rejectionReason TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_a_state (`key` TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_a_snapshots (ownerId TEXT NOT NULL, sessionId TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(ownerId, sessionId))")
            }
        }
    }
}
