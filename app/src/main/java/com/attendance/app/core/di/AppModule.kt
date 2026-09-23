package com.attendance.app.core.di

import androidx.room.Room
import com.attendance.app.core.data.local.room.AppDatabase

import com.attendance.app.auth.data.AuthRepository
import com.attendance.app.auth.data.TokenManager
import com.attendance.app.auth.presentation.AuthViewModel
import com.attendance.app.classes.data.ClassRepository
import com.attendance.app.classes.presentation.ClassViewModel
import com.attendance.app.student.presentation.*
import com.attendance.app.teacher.presentation.*
import com.attendance.app.profile.data.ProfileRepository
import com.attendance.app.profile.presentation.ProfileViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Room Database
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "attendance_db"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()
    }

    single { get<AppDatabase>().classDao() }
    single { get<AppDatabase>().studentDao() }
    single { get<AppDatabase>().attendanceDao() }
    single { get<AppDatabase>().planAAttendanceDao() }
    single { com.attendance.app.core.data.attendance.AttendanceRepository(androidContext(), get(), get(), get()) }

    // Repositories
    single { TokenManager(androidContext()) }
    single { AuthRepository(get(), get()) }
    single { ClassRepository(get(), get(), get(), get()) }
    single { ProfileRepository(get(), get(), androidContext()) }

    // ViewModels
    viewModel { AuthViewModel(get()) }
    viewModel { ClassViewModel(get()) }
    viewModel { StudentAttendanceViewModel(get()) }

    viewModel { TeacherAttendanceViewModel(get()) }
    viewModel { LiveSessionViewModel(get()) }
    viewModel { com.attendance.app.teacher.presentation.TeacherHistoryViewModel(get()) }
    viewModel { TeacherReviewsViewModel(get()) }
    viewModel { ProfileViewModel(get()) }
    viewModel { com.attendance.app.face.presentation.FaceRegistrationViewModel(get()) }
    viewModel { com.attendance.app.hod.presentation.HodDashboardViewModel(get(), get()) }
    viewModel { com.attendance.app.admin.presentation.AdminDashboardViewModel(get(), get()) }
}

val dataModule = module {
    // Room Database
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "attendance_db"
        ).build()
    }
    
    // DAOs
    single { get<AppDatabase>().classDao() }
    single { get<AppDatabase>().studentDao() }
    single { get<AppDatabase>().attendanceDao() }

    // Repositories
    single { TokenManager(androidContext()) }
    single { AuthRepository(get(), get()) }
    single { ClassRepository(get(), get(), get(), get()) }
}

val networkModule = module {
    single {
        val tokenManager = get<com.attendance.app.auth.data.TokenManager>()
                io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            engine {
                config {
                    connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                kotlinx.serialization.json.Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                }.let { json(it) }
            }
            defaultRequest {
                val token = tokenManager.getToken()
                if (token != null) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        }
    }
 
}














