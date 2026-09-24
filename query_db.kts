import java.sql.DriverManager

fun requiredDbEnvironment(name: String): String = requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name must be configured" }

val url = requiredDbEnvironment("DB_URL")
val props = java.util.Properties()
props.setProperty("user", requiredDbEnvironment("DB_USER"))
props.setProperty("password", requiredDbEnvironment("DB_PASSWORD"))
val conn = DriverManager.getConnection(url, props)

val stmt = conn.createStatement()
var rs = stmt.executeQuery("SELECT id, name, class_code FROM classes")
while (rs.next()) {
    println("Class: " + rs.getString("id") + " " + rs.getString("name") + " " + rs.getString("class_code"))
}

rs = stmt.executeQuery("SELECT class_id, student_id FROM class_members")
while (rs.next()) {
    println("Member: " + rs.getString("class_id") + " " + rs.getString("student_id"))
}

conn.close()
