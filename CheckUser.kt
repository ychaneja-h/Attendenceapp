import java.sql.DriverManager

fun requiredDbEnvironment(name: String): String =
    requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name must be configured" }

fun main() {
    val collegeId = requiredDbEnvironment("CHECK_USER_COLLEGE_ID")
    DriverManager.getConnection(
        requiredDbEnvironment("DB_URL"),
        requiredDbEnvironment("DB_USER"),
        requiredDbEnvironment("DB_PASSWORD")
    ).use { conn ->
        conn.prepareStatement("SELECT EXISTS (SELECT 1 FROM users WHERE college_id = ?) AS account_exists").use { stmt ->
            stmt.setString(1, collegeId)
            stmt.executeQuery().use { result ->
                check(result.next()) { "Account lookup returned no result" }
                println("Matching account exists: ${result.getBoolean("account_exists")}")
            }
        }
    }
}
