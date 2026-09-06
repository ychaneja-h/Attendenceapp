# 📱 Smart Attendance System

An enterprise-grade, secure, and offline-capable Smart Attendance application built with modern Android development practices. This system utilizes **Machine Learning for Face Recognition**, **Liveness Detection**, and **Local Network/BLE** capabilities to accurately and securely record student attendance.

---

## ✨ Key Features

### 👤 For Students
- **Face Enrollment:** Secure 3-angle face registration utilizing TFLite and MobileFaceNet.
- **Liveness Detection:** Integrated blink and smile detection to prevent spoofing using Google ML Kit.
- **Class Management:** Join classes via secure codes (requires Teacher approval).
- **Offline Attendance:** Mark attendance even without an active internet connection; securely syncs when back online.

### 👨‍🏫 For Teachers
- **Live Session Dashboard:** Real-time visibility of students checking in during an active session.
- **Manual Overrides:** Manually mark students present/absent with automated database syncing.
- **Pending Approvals:** Review and approve/reject new student requests to join classes.
- **Export Reports:** Generate and download monthly attendance reports.

### 🏛️ For HODs & Admins
- **Hierarchy Management:** Oversee multiple classes, teachers, and student rosters.
- **Analytics & Logs:** View comprehensive attendance analytics and system health.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework:** Jetpack Compose (100% Kotlin)
- **Local Database:** Room Database (Offline-first architecture)
- **Face Recognition:** 
  - **Google ML Kit** for bounding box, landmark, and liveness detection.
  - **MobileFaceNet (TFLite)** for high-accuracy facial embeddings (dynamic batch processing support).
- **Networking:** Ktor Client (Android) & Ktor Server (Backend)
- **Dependency Injection:** Koin
- **Asynchronous Flow:** Kotlin Coroutines & Flows
- **Proximity:** Bluetooth Low Energy (BLE) / Wi-Fi direct scanning.

---

## 🔒 Security & Privacy

- **On-Device Processing:** Face embeddings are extracted and matched entirely on the device. No raw images are sent to the backend.
- **Liveness Checks:** Requires dynamic facial movements (blinking, smiling) to prevent static photo spoofing.
- **Dynamic Tensor Shapes:** Auto-adapts to various MobileFaceNet model variants to prevent buffer overflows.

---

## 🚀 Download & Installation

This repository acts as the release hub for the Smart Attendance System.

### How to Install
1. Navigate to the [Releases](../../releases) section of this repository.
2. Download the latest `app-release.apk` file to your Android device.
3. Open the downloaded file to install it (you may need to allow installation from unknown sources in your device settings).
4. Launch the app and log in with your credentials.

---

---

*Designed and developed for seamless classroom management and zero-friction attendance tracking.*
