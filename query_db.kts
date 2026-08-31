import java.sql.DriverManager

val url = "jdbc:postgresql://localhost:5432/attendance"
val props = java.util.Properties()
props.setProperty("user", "postgres")
props.setProperty("password", "123456")
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
