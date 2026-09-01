package com.attendance.app.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.attendance.app.BuildConfig
import java.security.MessageDigest
import kotlin.system.exitProcess

object SecurityUtils {

    private val expectedSigParts = arrayOf(
        "B537", "D234", "2289", "1112", "A0E5", "1FEE", "DA5A", "630E",
        "1A04", "3EBC", "9DE4", "CA1E", "5BD4", "4E74", "7BCA", "9CD9"
    )

    fun verifyEnvironment(context: Context) {
        if (BuildConfig.DEBUG) return
        verifyAppSignature(context)
        if (isRooted()) { 
            tamperDetected("Root Detected", context)
            return 
        }
        if (isEmulator()) { 
            tamperDetected("Emulator Detected", context)
            return
        }
    }

    private fun isEmulator(): Boolean {
        // Reduced emulator checks to avoid false positives on real devices like Xiaomi/Realme
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT
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
                tamperDetected("No signatures found in APK", context)
                return
            }

            val expectedHash = expectedSigParts.joinToString("")
            val digest = MessageDigest.getInstance("SHA-256")
            
            var matched = false
            for (signature in signatures) {
                val hashBytes = digest.digest(signature.toByteArray())
                val hashString = hashBytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
                if (hashString == expectedHash) {
                    matched = true
                    break
                }
            }

            if (!matched) {
                tamperDetected("Signature Hash Mismatch", context)
                return
            }

        } catch (e: Exception) {
            tamperDetected("Exception during signature verification: $e", context)
            return
        }
    }

    private fun tamperDetected(reason: String, context: Context) {
        Log.e("Security", "App Tampering Detected! Reason: $reason")
        Toast.makeText(context, "Security Alert: $reason", Toast.LENGTH_LONG).show()
        
        // Delay kill so user can read the toast message
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }, 3500)
    }
}
