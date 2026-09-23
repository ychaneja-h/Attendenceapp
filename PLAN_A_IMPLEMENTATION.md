# Plan A attendance

Implemented in this checkout from the interrupted Plan A working copy. Plan B is not implemented.

## Classroom workflow

- The teacher scans. Each student performs entry face verification, sends a short entry broadcast, then broadcasts for ten seconds at five-minute intervals. Students do not scan for the teacher.
- Teacher receptions and student transmission attempts are saved in Room before synchronization. Student transmission counts are explicitly unconfirmed until matched against teacher receptions.
- The teacher ends the local session and tells students to leave. Students complete face checkout within five minutes. There is no Bluetooth teacher-to-student stop message in this design.
- Back navigation cannot complete checkout. Unfinished attendance resumes after ordinary process interruption. Removing the student app from recents records a missing-face interruption when Android delivers the callback; force-stop recovery checks saved process-exit information when the app is reopened.
- An ongoing foreground notification covers active attendance. The student radio uses short bursts; wake-up alarms and bounded wake locks support ordinary screen-off operation without inventing missed checks.

## Reconciliation and history

- Immutable session and attempt events use stable identifiers. Repeated packets and uploads do not inflate check counts. Completion manifests distinguish missing uploads from missing face scans.
- Entry must successfully finish between teacher start and five minutes afterward while the session is active. Exit must finish between teacher stop and five minutes afterward. Exact boundaries qualify when timing can be established.
- A reconciled student needs both valid faces and at least 75% of received five-minute checks. Failed conditions produce Under Review with all reasons and counts. Never-attending students are Absent. A checked-in student stays provisionally Present while the required evidence is incomplete.
- Teacher Present/Absent decisions override computed outcomes and can be changed again. The client gives newer synchronized decisions precedence over older already-uploaded local decisions.
- Monthly attendance is inside each student class. Teacher history retains separate sessions, including multiple lectures on the same day. Each session has its own delete action; tombstones also suppress associated student attempts after late uploads.
- Class and student CSV exports include the new sessions. Legacy local records are retained and explicitly labeled because their original session/check evidence cannot be reconstructed reliably.
- Room migration 1 to 2 preserves existing records and supports one student in multiple class rosters. Refreshing a roster atomically replaces that class's cached roster.

## Clock handling

Wall time, elapsed real time and an installation/boot identifier accompany events. Confirmed server anchors resolve same-boot evidence, including records uploaded days later. The maximum anchor distance is 30 days, with an uncertainty allowance that grows with oscillator drift. A reboot without a usable anchor, inconsistent anchors or an uncertain deadline remains reviewable. Upload time itself is not the face deadline.

## Validation

Validated on 2026-09-23: debug assembly succeeded, all 41 unit tests passed (11 Android-side JVM tests and 30 backend tests), and Android lint completed with zero errors. Non-blocking lint warnings remain. `git diff --check` passed.

From this directory, with a configured Android SDK and JDK:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :backend:test :app:lintDebug --console=plain --max-workers=2
```

Tests cover face boundaries, the 75% threshold, missing scans, duplicate packets, late and out-of-order evidence, teacher overrides, tombstones, clock references and broadcast scheduling. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Gradle reports are under each module's `build/reports` directory.

## Deployment and device validation still required

The changes are local; no server deployment or phone installation was performed. Deploy the matching backend before using the new client synchronization endpoints. Database initialization adds the Plan A tables and conditionally widens earlier Plan A event IDs.

No Android device was connected during implementation. Validate teacher/student entry, reception counts, stop and checkout offline on the actual Samsung and Redmi phones. Test locked screens, Bluetooth toggles, permission denial, recents removal, process death, reboot, and teacher/student synchronization in both orders. Repeat at classroom scale (approximately 200 students).

Android Doze, vendor battery restrictions and force-stop can delay or prevent background work. An ongoing notification does not guarantee exact five-minute delivery. A student who never synchronizes a completed attempt can remain provisional; the teacher can still decide Present or Absent. BLE connectivity plus entry/exit face scans does not prove continuous physical presence throughout a lecture.

Relevant platform references: [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [Doze restrictions](https://developer.android.com/training/monitoring-device-state/doze-standby), [Java API compatibility](https://developer.android.com/studio/write/java8-support).
