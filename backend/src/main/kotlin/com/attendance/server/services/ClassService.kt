package com.attendance.server.services

import com.attendance.server.models.*
import com.attendance.shared.dto.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

object ClassService {

    fun createClass(teacherId: UUID, request: CreateClassRequest): ClassResponse {
        return transaction {
            AccessControl.requireRole(teacherId, AccessControl.staffRoles)
            // Ensure teacher exists
            val teacher = Users.select { Users.id eq teacherId }.firstOrNull()
                ?: throw IllegalArgumentException("Teacher not found")

            // Generate unique 6-character class code
            val code = generateClassCode()

            // Dummy department and academic year if not provided
            val depId = request.departmentId?.let { UUID.fromString(it) } ?: getOrCreateDummyDepartment()
            val yearId = request.academicYearId?.let { UUID.fromString(it) } ?: getOrCreateDummyAcademicYear()

            // Generate BLE keys (simplified for now)
            val bleId = code.take(8).padEnd(8, '0') // 4-byte hex string
            val bleKey = ByteArray(32) { (0..255).random().toByte() }

            val classId = Classes.insert {
                it[this.teacherId] = teacherId
                it[name] = request.name
                it[section] = request.section
                it[semester] = request.semester
                it[departmentId] = depId
                it[academicYearId] = yearId
                it[classCode] = code
                it[bleClassId] = bleId
                it[bleClassKey] = bleKey
                it[createdAt] = Instant.now()
            }[Classes.id]

            ClassResponse(
                id = classId.toString(),
                name = request.name,
                section = request.section,
                semester = request.semester,
                department = "Dummy Dept",
                academicYear = "2024",
                classCode = code,
                teacherName = teacher[Users.name],
                studentCount = 0,
                isActive = true
            )
        }
    }

    fun joinClass(studentId: UUID, request: JoinClassRequest): JoinRequestResponse {
        return transaction {
            AccessControl.requireRole(studentId, setOf("STUDENT"))
            val student = Users.select { Users.id eq studentId }.firstOrNull()
                ?: throw IllegalArgumentException("Student not found")

            val classRow = Classes.select { (Classes.classCode eq request.classCode) and (Classes.isActive eq true) }.firstOrNull()
                ?: throw IllegalArgumentException("Invalid class code")

            val classId = classRow[Classes.id]

            // Check if already requested or joined
            val existingMember = ClassMembers.select { (ClassMembers.classId eq classId) and (ClassMembers.studentId eq studentId) }.firstOrNull()
            if (existingMember != null) {
                throw IllegalArgumentException("Already joined this class")
            }

            val existingReq = ClassJoinRequests.select { (ClassJoinRequests.classId eq classId) and (ClassJoinRequests.studentId eq studentId) }.firstOrNull()
            if (existingReq != null) {
                throw IllegalArgumentException("Already requested to join")
            }

            val reqId = ClassJoinRequests.insert {
                it[this.classId] = classId
                it[this.studentId] = studentId
                it[status] = "PENDING"
                it[requestedAt] = Instant.now()
            }[ClassJoinRequests.id]

            JoinRequestResponse(
                id = reqId.toString(),
                studentId = studentId.toString(),
                studentName = student[Users.name],
                collegeId = student[Users.collegeId],
                status = "PENDING",
                requestedAt = Instant.now().toEpochMilli()
            )
        }
    }

    fun getTeacherClasses(teacherId: UUID): List<ClassResponse> {
        return transaction {
            AccessControl.requireRole(teacherId, AccessControl.staffRoles)
            val query = Classes.innerJoin(Users).select { (Classes.teacherId eq teacherId) and (Classes.isActive eq true) }
            query.map { row ->
                val studentCount = ClassMembers.select { ClassMembers.classId eq row[Classes.id] }.count().toInt()
                ClassResponse(
                    id = row[Classes.id].toString(),
                    name = row[Classes.name],
                    section = row[Classes.section],
                    semester = row[Classes.semester],
                    department = row[Classes.departmentId].toString(),
                    academicYear = row[Classes.academicYearId].toString(),
                    classCode = row[Classes.classCode],
                    teacherName = row[Users.name],
                    studentCount = studentCount,
                    isActive = row[Classes.isActive]
                )
            }
        }
    }

    fun getStudentClasses(studentId: UUID): List<ClassResponse> {
        return transaction {
            AccessControl.requireRole(studentId, setOf("STUDENT"))
            val activeQuery = ClassMembers
                .innerJoin(Classes, onColumn = { ClassMembers.classId }, otherColumn = { Classes.id })
                .innerJoin(Users, onColumn = { Classes.teacherId }, otherColumn = { Users.id })
                .select { (ClassMembers.studentId eq studentId) and (Classes.isActive eq true) and (ClassMembers.status eq "ACTIVE") }
            
            val activeClasses = activeQuery.map { row ->
                ClassResponse(
                    id = row[Classes.id].toString(),
                    name = row[Classes.name],
                    section = row[Classes.section],
                    semester = row[Classes.semester],
                    department = row[Classes.departmentId].toString(),
                    academicYear = row[Classes.academicYearId].toString(),
                    classCode = row[Classes.classCode],
                    teacherName = row[Users.name],
                    studentCount = ClassMembers.select { ClassMembers.classId eq row[Classes.id] }.count().toInt(),
                    isActive = row[Classes.isActive],
                    memberStatus = "ACTIVE"
                )
            }

            val pendingQuery = ClassJoinRequests
                .innerJoin(Classes, onColumn = { ClassJoinRequests.classId }, otherColumn = { Classes.id })
                .innerJoin(Users, onColumn = { Classes.teacherId }, otherColumn = { Users.id })
                .select { (ClassJoinRequests.studentId eq studentId) and (Classes.isActive eq true) and (ClassJoinRequests.status eq "PENDING") }

            val pendingClasses = pendingQuery.map { row ->
                ClassResponse(
                    id = row[Classes.id].toString(),
                    name = row[Classes.name],
                    section = row[Classes.section],
                    semester = row[Classes.semester],
                    department = row[Classes.departmentId].toString(),
                    academicYear = row[Classes.academicYearId].toString(),
                    classCode = row[Classes.classCode],
                    teacherName = row[Users.name],
                    studentCount = ClassMembers.select { ClassMembers.classId eq row[Classes.id] }.count().toInt(),
                    isActive = row[Classes.isActive],
                    memberStatus = "PENDING_APPROVAL"
                )
            }

            activeClasses + pendingClasses
        }
    }

    fun removeStudent(classId: UUID, studentId: UUID, actorId: UUID) {
        transaction {
            AccessControl.requireClassOwner(actorId, classId)
            ClassMembers.update({ (ClassMembers.classId eq classId) and (ClassMembers.studentId eq studentId) }) {
                it[status] = "REMOVED"
            }
            ClassJoinRequests.update({ (ClassJoinRequests.classId eq classId) and (ClassJoinRequests.studentId eq studentId) }) {
                it[status] = "REJECTED"
            }
        }
    }

    fun deleteClass(classId: UUID, actorId: UUID) {
        transaction {
            AccessControl.requireClassOwner(actorId, classId)
            Classes.update({ Classes.id eq classId }) {
                it[isActive] = false
            }
        }
    }

    fun getAllRequests(classId: UUID, actorId: UUID): List<JoinRequestResponse> {
        return transaction {
            AccessControl.requireClassOwner(actorId, classId)
            val query = ClassJoinRequests.innerJoin(Users, onColumn = { ClassJoinRequests.studentId }, otherColumn = { Users.id })
                .select { ClassJoinRequests.classId eq classId }
            query.map { row ->
                JoinRequestResponse(
                    id = row[ClassJoinRequests.id].toString(),
                    studentId = row[ClassJoinRequests.studentId].toString(),
                    studentName = row[Users.name],
                    collegeId = row[Users.collegeId] ?: "",
                    status = row[ClassJoinRequests.status],
                    requestedAt = row[ClassJoinRequests.requestedAt].toEpochMilli()
                )
            }
        }
    }

    fun getPendingRequests(classId: UUID, actorId: UUID): List<JoinRequestResponse> {
        return transaction {
            AccessControl.requireClassOwner(actorId, classId)
            val query = ClassJoinRequests.innerJoin(Users, onColumn = { ClassJoinRequests.studentId }, otherColumn = { Users.id })
                .select { (ClassJoinRequests.classId eq classId) and (ClassJoinRequests.status eq "PENDING") }
            query.map { row ->
                JoinRequestResponse(
                    id = row[ClassJoinRequests.id].toString(),
                    studentId = row[ClassJoinRequests.studentId].toString(),
                    studentName = row[Users.name],
                    collegeId = row[Users.collegeId] ?: "",
                    status = row[ClassJoinRequests.status],
                    requestedAt = row[ClassJoinRequests.requestedAt].toEpochMilli()
                )
            }
        }
    }

    fun rejectRequest(requestId: UUID, actorId: UUID) {
        transaction {
            val request = ClassJoinRequests.select { ClassJoinRequests.id eq requestId }.singleOrNull()
                ?: throw IllegalArgumentException("Request not found")
            AccessControl.requireClassOwner(actorId, request[ClassJoinRequests.classId])
            require(request[ClassJoinRequests.status] == "PENDING") { "Request is no longer pending" }
            ClassJoinRequests.update({ ClassJoinRequests.id eq requestId }) {
                it[status] = "REJECTED"
            }
        }
    }

    fun approveRequest(requestId: UUID, actorId: UUID) {
        transaction {
            val request = ClassJoinRequests.select { ClassJoinRequests.id eq requestId }.firstOrNull()
                ?: throw IllegalArgumentException("Request not found")
            AccessControl.requireClassOwner(actorId, request[ClassJoinRequests.classId])
            require(request[ClassJoinRequests.status] == "PENDING") { "Request is no longer pending" }
            ClassJoinRequests.update({ ClassJoinRequests.id eq requestId }) {
                it[status] = "APPROVED"
            }
            
            val existing = ClassMembers.select { (ClassMembers.classId eq request[ClassJoinRequests.classId]) and
                (ClassMembers.studentId eq request[ClassJoinRequests.studentId]) }.singleOrNull()
            if (existing != null) ClassMembers.update({ ClassMembers.id eq existing[ClassMembers.id] }) {
                it[status] = "ACTIVE"
                it[approvedAt] = Instant.now()
            } else ClassMembers.insert {
                it[classId] = request[ClassJoinRequests.classId]
                it[studentId] = request[ClassJoinRequests.studentId]
                it[status] = "ACTIVE"
                it[joinedAt] = Instant.now()
                it[approvedAt] = Instant.now()
            }
        }
    }

    private fun generateClassCode(): String {
        val allowedChars = ('A'..'Z') + ('0'..'9')
        var code: String
        do {
            code = (1..6).map { allowedChars.random() }.joinToString("")
            val exists = Classes.select { Classes.classCode eq code }.count() > 0
        } while (exists)
        return code
    }

    private fun getOrCreateDummyDepartment(): UUID {
        val dep = Departments.selectAll().firstOrNull()
        if (dep != null) return dep[Departments.id]
        return Departments.insert {
            it[name] = "Computer Science"
            it[code] = "CSE"
            it[createdAt] = Instant.now()
        }[Departments.id]
    }

    private fun getOrCreateDummyAcademicYear(): UUID {
        val year = AcademicYears.selectAll().firstOrNull()
        if (year != null) return year[AcademicYears.id]
        return AcademicYears.insert {
            it[label] = "2024-2025"
            it[startDate] = Instant.now()
            it[endDate] = Instant.now().plusSeconds(31536000)
            it[isCurrent] = true
        }[AcademicYears.id]
    }
}
