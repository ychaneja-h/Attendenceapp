import java.sql.DriverManager

fun main() {
    val conn = DriverManager.getConnection("jdbc:postgresql://ep-summer-darkness-a58z2n1q.us-east-2.aws.neon.tech/attendance_db?sslmode=require", "attendance_db_owner", "aZt2z4jSHOhG")
    val stmt = conn.createStatement()
    val rs = stmt.executeQuery("SELECT id, college_id, password_hash FROM users WHERE college_id = 'YogeshRtu'")
    while (rs.next()) {
        println("ID: " + rs.getString("id") + ", Hash: " + rs.getString("password_hash"))
    }
}
