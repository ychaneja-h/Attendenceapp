# Android release 1.4.0

This version uses application ID `com.attendance.app`, version name `1.4.0`, and
version code `8`. It retains the existing release key so devices with a release
signed by that key can upgrade without losing their local data. An installation
signed with the Android debug key cannot be updated in place using the release
APK; export any necessary data before removing a debug installation.

## Signing configuration

Keep the original `app/attendance_release.keystore` in secure private storage.
Copy `signing.properties.example` to `signing.properties` and supply its real
credentials locally. The keystore and populated properties file are ignored by
Git. Do not upload them to GitHub, Docker builds, issue reports, or APK releases.

Alternatively, configure these secret environment variables in CI:

| Local property | Environment variable |
| --- | --- |
| `storeFile` | `ATTENDANCE_SIGNING_STORE_FILE` |
| `storePassword` | `ATTENDANCE_SIGNING_STORE_PASSWORD` |
| `keyAlias` | `ATTENDANCE_SIGNING_KEY_ALIAS` |
| `keyPassword` | `ATTENDANCE_SIGNING_KEY_PASSWORD` |

Environment variables take precedence. Keystore paths resolve from the repository
root; an absolute path is also supported. A release build fails when signing
configuration is missing instead of silently delivering an unsigned APK. Debug
and backend builds do not require release credentials.

Existing release certificate SHA-256 fingerprint:

```text
B5:37:D2:34:22:89:11:12:A0:E5:1F:EE:DA:5A:63:0E:1A:04:3E:BC:9D:E4:CA:1E:5B:D4:4E:74:7B:CA:9C:D9
```

This fingerprint is public certificate information, not a signing secret. Keep
the same certificate for future APK upgrades. If the private key was previously
published, removing it from the current checkout does not revoke historical
copies; review key exposure and supported distribution key rotation separately.

## Build and verify

Use JDK 17 or 21, the Android SDK, and a private `local.properties` containing
`sdk.dir`. Machine-specific Java paths do not belong in tracked Gradle settings.
On this Windows workstation, Android Studio's bundled JDK 21 can be selected for
the current shell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
```

The signed APK is `app/build/outputs/apk/release/app-release.apk`. Verify it using
the installed Android SDK's `apksigner`:

```text
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Confirm that the signer certificate SHA-256 equals the fingerprint above and
check the APK version before distributing it. Upgrade compatibility with any
older distributed APK should be confirmed against that APK's certificate.

## Device verification

Before wide deployment, test an upgrade from the previously distributed release
and verify online and offline attendance, face checkout recovery, reconciliation,
teacher corrections, and class history on the intended Samsung/Redmi devices.
The large class scenario still requires a physical test with 200 students.
