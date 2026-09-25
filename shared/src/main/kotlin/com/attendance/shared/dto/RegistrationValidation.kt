package com.attendance.shared.dto

data class RegistrationIssue(val code: String, val message: String)

/** The form and API use the same limits; passwords are never trimmed or truncated. */
object RegistrationValidation {
    const val PASSWORD_HINT = "Use at least 8 characters."

    fun password(password: String): RegistrationIssue? = when {
        password.length < 8 -> RegistrationIssue("PASSWORD_TOO_SHORT", "Use a password with at least 8 characters.")
        password.toByteArray(Charsets.UTF_8).size > 72 -> RegistrationIssue("PASSWORD_TOO_LONG", "This password is too long. Shorten it to at most 72 UTF-8 bytes.")
        else -> null
    }

    fun common(collegeId: String, name: String, password: String): RegistrationIssue? =
        text(collegeId, 50, "COLLEGE_ID", "College ID") ?: text(name, 200, "NAME", "Full name") ?: this.password(password)

    fun student(request: StudentRegisterRequest): RegistrationIssue? =
        common(request.collegeId, request.name, request.password) ?:
        text(request.department, 100, "DEPARTMENT", "Department") ?:
        text(request.section, 20, "SECTION", "Section") ?:
        if (request.semester < 1) RegistrationIssue("INVALID_SEMESTER", "Enter a valid semester number (1 or higher).") else
            text(request.academicYear, 20, "ACADEMIC_YEAR", "Academic year") ?:
            text(request.deviceFingerprint, 255, "DEVICE_ID", "Device ID")

    fun teacher(request: TeacherRegisterRequest): RegistrationIssue? =
        common(request.collegeId, request.name, request.password) ?: text(request.departmentCode, 50, "DEPARTMENT_CODE", "Department invite code")

    fun hod(request: HodRegisterRequest): RegistrationIssue? =
        common(request.collegeId, request.name, request.password) ?:
        text(request.department, 100, "DEPARTMENT", "Department") ?:
        text(request.departmentCode, 50, "DEPARTMENT_CODE", "Department invite code")

    private fun text(value: String, maxLength: Int, code: String, label: String): RegistrationIssue? = when {
        value.isBlank() -> RegistrationIssue("MISSING_$code", "$label is required.")
        value.length > maxLength -> RegistrationIssue("INVALID_$code", "$label must be $maxLength characters or fewer.")
        else -> null
    }
}

fun StudentRegisterRequest.normalizedForRegistration() = copy(
    collegeId = collegeId.trim(), name = name.trim(), department = department.trim(),
    section = section.trim(), academicYear = academicYear.trim()
)

fun TeacherRegisterRequest.normalizedForRegistration() = copy(
    collegeId = collegeId.trim(), name = name.trim(), departmentCode = departmentCode.trim()
)

fun HodRegisterRequest.normalizedForRegistration() = copy(
    collegeId = collegeId.trim(), name = name.trim(), department = department.trim(), departmentCode = departmentCode.trim()
)
