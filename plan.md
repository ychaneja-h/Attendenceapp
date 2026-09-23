# Attendance App - Current Status & Remaining Problems

This document outlines the current state of the Attendance App codebase, recent technical solutions applied, and the specific problems that still need to be addressed by the AI.

## 1. Project Context & Architecture
- **Repository:** `https://github.com/ychaneja-h/Attendenceapp.git`
- **Frontend (Android):** Kotlin, Jetpack Compose, CameraX, Google ML Kit (Face Detection), TensorFlow Lite (`mobile_face_net.tflite`), Koin (DI).
- **Backend:** Kotlin + Ktor, Exposed ORM, PostgreSQL (Neon). Hosted on Render (`main` branch auto-deploy).
- **Core Mechanic:** Teachers broadcast a BLE token -> Students detect it -> Students pass Offline AI Face Verification (Liveness + MobileFaceNet) -> Backend marks attendance.

---

## 2. Recently Solved Problems (Current Code State)
- **Check-Out Enforcement:** Fixed a bug where students could use the Android system back button to exit an active session without checking out. A `BackHandler` now intercepts this to enforce a mandatory check-out scan.
- **Pending Class Lockout:** Fixed the UI to actively check `memberStatus`. Classes marked `"PENDING"` are visually locked so students cannot mark attendance until teacher approval.
- **Render ProGuard Timeouts:** Resolved `SocketTimeoutException` in Release builds by adding explicit `-keep` rules for Ktor and OkHttp.

---

## 3. Remaining Problems to Solve (HIGH PRIORITY)

### 🚨 Problem 1: Face Verification AI is Broken (CRITICAL)
- **Issue:** The Face Recognition system (`FaceAnalyzer.kt` and `mobile_face_net.tflite`) is fundamentally broken. It is accepting the wrong person's face. The similarity score maxes out around `0.76` for the correct person, but it *also* hits `0.76` for completely different people.
- **Suspected Root Cause:** The TFLite model is likely receiving an identical image for everyone (e.g., a pure black box, or a crop of the background wall). We attempted to fix the matrix math by converting the CameraX `ImageProxy` to a `Bitmap` before passing it to ML Kit, but **the fix failed**. The `Canvas` cropping logic or the `ByteBuffer` pixel normalization `((r - 127.5f) / 128.0f)` is outputting bad data to the model.
- **Required Fix:** Completely rewrite the Face Extraction logic in `FaceAnalyzer.kt`. Use `Matrix.setRectToRect()` to guarantee the bounding box maps correctly, or log/display the cropped `faceBitmap` to the UI temporarily so we can visually debug what the AI is actually seeing.

### Problem 2: Basic & Inconsistent Dashboards
- **Issue:** While the `StudentHomeScreen` has a premium design with a profile header and dynamic cards, `TeacherHomeScreen` and `HodDashboardScreen` are visually basic and lack this premium aesthetic.
- **Required Fix:** Overhaul the Teacher and HOD dashboards to include their profile details (photo, name, email) at the top. The Teacher's class list needs modern cards with prominent "Start Session" (green) and "Stop Session" (red) buttons.

### Problem 3: Missing Export/Reporting Functionality
- **Issue:** The backend tracks all attendance data, but there is no mechanism for users to download it.
- **Required Fix:** Implement a "Download Report" feature in the class details screens to generate PDF or Excel exports of the monthly attendance records, utilizing the Android `MediaStore` or `FileProvider` to save/share the file.

### Problem 4: Lack of UI Polish and Smoothness
- **Issue:** The app lacks modern visual feedback. Screen transitions are abrupt, loading states are sometimes missing, and error messages use basic Android `Toast` popups.
- **Required Fix:** Introduce Compose `AnimatedVisibility` and transition animations between screens. Ensure all API calls have proper `CircularProgressIndicator` or skeleton loaders, and upgrade error handling to use Compose `Snackbar` notifications.

### Problem 5: Background Service Stability
- **Issue:** The `AttendanceBroadcastService` handles the BLE broadcasting, but it needs to be fully compliant with the latest Android 14+ Foreground Service permissions to ensure it doesn't get killed by the OS while the teacher's screen is off.
- **Required Fix:** Audit and update the Foreground Service implementation and permissions for modern Android API levels.
