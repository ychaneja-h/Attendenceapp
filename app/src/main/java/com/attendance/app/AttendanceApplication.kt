package com.attendance.app

import android.app.Application
import com.attendance.app.core.di.appModule
import com.attendance.app.core.di.dataModule
import com.attendance.app.core.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class AttendanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@AttendanceApplication)
            modules(appModule, networkModule)
        }
        org.koin.java.KoinJavaComponent.getKoin()
            .get<com.attendance.app.core.data.attendance.AttendanceRepository>().initialize()
    }
}
