$content = Get-Content "app/src/main/java/com/attendance/app/face/ui/FaceVerificationScreen.kt" -Raw
$content = $content -replace "com\.attendance\.app\.face\.FaceMath\.cosineDistance\(embedding, stored\)", "com.attendance.app.face.FaceMath.cosineSimilarity(embedding, stored)"
$content = $content -replace "val distance = (.*?)`n(.*?)if \(distance < 0\.4f\)", "val similarity = `$1`n`$2if (similarity > 0.7f)"
Set-Content "app/src/main/java/com/attendance/app/face/ui/FaceVerificationScreen.kt" $content
