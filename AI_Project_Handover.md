# Smart Attendance App: AI Project Handover Document

This document contains the complete requirements, architecture, workflows, and current problems for the **Smart Attendance System**. It is designed to be provided to an AI assistant to get context on the project and make it deployment-ready.

---

## 1. Project Overview
The Smart Attendance System is an enterprise-grade Android application designed for secure, offline-capable attendance tracking. It uses Machine Learning (Face Recognition & Liveness Detection) and Proximity (BLE/Wi-Fi Direct) to ensure students are physically present in the class.

### Key Roles
*   **Students**: Register face data, join classes, and mark attendance securely using their device camera.
*   **Teachers**: Create classes, manage student approvals, broadcast active sessions (via BLE), and view live attendance dashboards.
*   **HODs & Admins**: Oversee departments, view analytics, and export reports.

---

## 2. Architecture & Tech Stack

### Frontend (Android App)
*   **Language:** Kotlin (100%)
*   **UI Framework:** Jetpack Compose
*   **Local Database:** Room Database (Offline-first architecture)
*   **Machine Learning (Face Recognition):**
    *   **Google ML Kit:** Bounding box, landmark, and liveness detection (blink/smile).
    *   **TensorFlow Lite (TFLite):** `mobile_face_net.tflite` model for generating high-accuracy facial embeddings.
*   **Proximity/Networking:** Bluetooth Low Energy (BLE) & Ktor Client.
*   **Dependency Injection:** Koin
*   **Asynchrony:** Kotlin Coroutines & Flows

### Backend
*   **Language/Framework:** Kotlin + Ktor Server
*   **Database ORM:** Exposed
*   **Database:** PostgreSQL (Neon DB)
*   **Deployment:** Render (Auto-deploys from the `main` branch)

---

## 3. Core System Workflows

### 3.1 Student Face Registration (Enrollment)
1. Student logs into the app.
2. Navigates to Face Registration.
3. App captures face from multiple angles using CameraX + ML Kit to ensure high quality.
4. TFLite model generates facial embeddings.
5. Embeddings are stored locally (encrypted) and synced with the backend.

### 3.2 Class Management & Joining
1. **Teacher** creates a class. The system generates a unique Class Code.
2. **Student** inputs the Class Code to join.
3. Student's status in the class becomes `"PENDING"`.
4. **Teacher** reviews pending requests and approves or rejects them.
5. Once approved, the student can view the class in their dashboard.

### 3.3 Active Session & Attendance Marking
1. **Teacher** starts an attendance session for a specific class.
2. Teacher's device starts a **Foreground Service** to broadcast a BLE token representing the session.
3. **Student** opens the class in their app and scans for the BLE token.
4. Once proximity is verified via BLE, the student's camera opens for Face Verification.
5. **Liveness Check**: Student must blink or smile (prevent static photo spoofing).
6. **Face Matching**: The camera feed is processed. The TFLite model generates embeddings of the live face and compares them against the stored enrollment embeddings.
7. If similarity > threshold, attendance is marked locally and synced to the backend.
8. **Check-Out**: The student must optionally scan/check out when the session ends.

### 3.4 Offline Synchronization
*   If the student has no internet, attendance events (with timestamps) are stored in the Room Database.
*   When internet is restored, a background worker syncs the pending attendance records to the Ktor backend.

---

## 4. Current Problems to Resolve (AI Tasks)

To make this app **deployment-ready**, the following critical problems must be resolved:

### 🚨 Task 1: Fix Face Verification AI (CRITICAL - HIGH PRIORITY)
*   **Issue:** The Face Recognition system (`FaceAnalyzer.kt` / `mobile_face_net.tflite`) is broken. It accepts the wrong person's face. The similarity score maxes out around `0.76` for the correct person, but also hits `0.76` for completely different people.
*   **Root Cause:** The TFLite model is receiving identical or corrupted image data (e.g., black boxes, unnormalized pixels, or wrong aspect ratios). The `ImageProxy` (CameraX) to `Bitmap` conversion, Face Cropping (`Canvas`), or `ByteBuffer` pixel normalization `((r - 127.5f) / 128.0f)` is flawed.
*   **Goal:** Rewrite the Face Extraction logic in `FaceAnalyzer.kt`. Use `Matrix.setRectToRect()` to ensure the bounding box crops the exact face accurately. Validate what the AI sees by temporarily rendering the cropped `faceBitmap` to the UI for visual debugging. Ensure liveness detection remains intact.

### 🎨 Task 2: Polish Teacher and HOD Dashboards
*   **Issue:** `StudentHomeScreen` has a premium UI, but `TeacherHomeScreen` and `HodDashboardScreen` are very basic and visually inconsistent.
*   **Goal:** Redesign Teacher/HOD dashboards to include premium profile headers (Photo, Name, Email). Enhance the Teacher's class list with modern cards featuring prominent "Start Session" (Green) and "Stop Session" (Red) buttons.

### 📄 Task 3: Implement Report Generation & Export
*   **Issue:** The backend tracks attendance, but users cannot export this data from the app.
*   **Goal:** Add a "Download Report" feature in class detail screens. Generate a PDF or CSV/Excel file of monthly attendance records. Utilize Android `MediaStore` or `FileProvider` to save/share the generated file securely.

### ✨ Task 4: UI/UX Smoothness & Transitions
*   **Issue:** Screen transitions are abrupt, loading states are missing, and error messages use standard Android Toasts.
*   **Goal:** Introduce `AnimatedVisibility` for smooth screen transitions. Implement `CircularProgressIndicator` or skeleton loaders for all API calls. Replace Toasts with Compose `Snackbar` for modern error handling.

### ⚙️ Task 5: Background Service Stability (Android 14+)
*   **Issue:** `AttendanceBroadcastService` (handles BLE) is prone to being killed by the OS when the screen is off.
*   **Goal:** Audit and update the Foreground Service implementation. Ensure it complies with the latest Android 14+ permissions (`FOREGROUND_SERVICE_CONNECTED_DEVICE` or similar) so the BLE broadcast remains stable during a 45-minute lecture.

---

## 5. Deployment Readiness Checklist
Before releasing `app-release.apk` to production, ensure:
- [ ] Face AI correctly distinguishes between two different people (Similarity < 0.4 for different, > 0.7 for same).
- [ ] BLE Foreground Service stays alive when the Teacher locks their phone.
- [ ] UI is fully polished, animated, and uses Snackbars.
- [ ] ProGuard/R8 rules do not strip Ktor/ML Kit classes in the release build (partially fixed, needs regression test).
- [ ] Teachers can export PDF/CSV files without permission crashes on Android 13+.
