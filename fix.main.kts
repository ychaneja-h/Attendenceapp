import java.io.File

fun main() {
    val file = File("app/src/main/java/com/attendance/app/auth/data/AuthRepository.kt")
    var content = file.readText()
    
    // Replace all instances of: Result.failure(Exception(response.bodyAsText().takeIf { it.isNotBlank() } ?: "Registration failed: \"))
    // with proper extraction
    
    val regex = Regex("""Result\.failure\(Exception\(response\.bodyAsText\(\)\.takeIf \{ it\.isNotBlank\(\) \} \?: "Registration failed: .*"\)\)""")
    content = regex.replace(content) {
        """val errorBody = response.bodyAsText(); val errMsg = if (errorBody.contains("\"error\"")) errorBody.substringAfter("\"error\":\"").substringBefore("\"") else errorBody; Result.failure(Exception(errMsg.takeIf { it.isNotBlank() } ?: "Registration failed"))"""
    }
    
    file.writeText(content)
}
