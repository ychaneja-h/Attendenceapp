import re

with open('app/src/main/java/com/attendance/app/auth/data/AuthRepository.kt', 'r') as f:
    content = f.read()

# Replace registerTeacher
teacher_pattern = r'suspend fun registerTeacher\(.*?\): Result<AuthTokenResponse> \{.*?Result\.success\(body\)\s*\} else'
teacher_repl = '''suspend fun registerTeacher(request: com.attendance.shared.dto.TeacherRegisterRequest): Result<Unit> {
        return try {
            val response = httpClient.post("\/teacher/register") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else'''
content = re.sub(teacher_pattern, teacher_repl, content, flags=re.DOTALL)

# Replace registerHod
hod_pattern = r'suspend fun registerHod\(.*?\): Result<AuthTokenResponse> \{.*?Result\.success\(body\)\s*\} else'
hod_repl = '''suspend fun registerHod(request: com.attendance.shared.dto.HodRegisterRequest): Result<Unit> {
        return try {
            val response = httpClient.post("\/hod/register") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else'''
content = re.sub(hod_pattern, hod_repl, content, flags=re.DOTALL)

with open('app/src/main/java/com/attendance/app/auth/data/AuthRepository.kt', 'w') as f:
    f.write(content)
