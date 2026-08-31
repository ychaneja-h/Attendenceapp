$content = Get-Content "app/src/main/java/com/attendance/app/face/ui/FaceVerificationScreen.kt" -Raw
$content = $content -replace "val distance = com\.attendance\.app\.face\.FaceMath\.cosineSimilarity\(embedding, stored\)", "val similarity = com.attendance.app.face.FaceMath.cosineSimilarity(embedding, stored)"
$content = $content -replace "if \(distance < 0\.4f\)", "if (similarity > 0.7f)"
Set-Content "app/src/main/java/com/attendance/app/face/ui/FaceVerificationScreen.kt" $content
