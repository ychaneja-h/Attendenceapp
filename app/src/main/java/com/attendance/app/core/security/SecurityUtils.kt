package com.attendance.app.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import com.attendance.app.BuildConfig
import java.security.MessageDigest
import kotlin.system.exitProcess

object SecurityUtils {

    // Obfuscated Official Release Signature (SHA-256)
    // Original: B5:37:D2:34:22:89:11:12:A0:E5:1F:EE:DA:5A:63:0E:1A:04:3E:BC:9D:E4:CA:1E:5B:D4:4E:74:7B:CA:9C:D9
    private val expectedSigParts = arrayOf(
        "B537", "D234", "2289", "1112", "A0E5", "1FEE", "DA5A", "630E",
        "1A04", "3EBC", "9DE4", "CA1E", "5BD4", "4E74", "7BCA", "9CD9"
    )

    fun verifyEnvironment(context: Context) {
        if (BuildConfig.DEBUG) return
        verifyAppSignature(context)
        if (isRooted() || isEmulator()) {
            tamperDetected()
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }

    private fun isRooted(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (java.io.File(path).exists()) return true
        }
        return false
    }

    private fun verifyAppSignature(context: Context) {
        // Only enforce in release builds
        if (BuildConfig.DEBUG) return

        try {
            val signatures: Array<Signature>
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                signatures = packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                signatures = packageInfo.signatures ?: emptyArray()
            }

            if (signatures.isEmpty()) {
                tamperDetected()
            }

            val expectedHash = expectedSigParts.joinToString("")
            val digest = MessageDigest.getInstance("SHA-256")
            
            var matched = false
            for (signature in signatures) {
                val hashBytes = digest.digest(signature.toByteArray())
                val hashString = hashBytes.joinToString("") { "%02X".format(it) }
                if (hashString == expectedHash) {
                    matched = true
                    break
                }
            }

            if (!matched) {
                tamperDetected()
            }

        } catch (e: Exception) {
            tamperDetected()
        }
    }

    private fun tamperDetected() {
        Log.e("Security", "App Tampering Detected! Invalid Signature.")
        // Hard crash to prevent bypass
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(1)
    }
}

