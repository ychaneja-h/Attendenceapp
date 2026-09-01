package com.attendance.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.attendance.app.core.ui.navigation.AppNavHost
import com.attendance.app.core.ui.theme.AttendanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        com.attendance.app.core.security.SecurityUtils.verifyAppSignature(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendanceTheme {
                AppNavHost()
            }
        }
    }
}

