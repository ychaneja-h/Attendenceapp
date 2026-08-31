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
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
        ).fallbackToDestructiveMigration().build()
    }

    single { get<AppDatabase>().classDao() }
    single { get<AppDatabase>().studentDao() }
    single { get<AppDatabase>().attendanceDao() }

    // Repositories
    single { TokenManager(androidContext()) }
    single { AuthRepository(get(), get()) }
    single { ClassRepository(get(), get(), get()) }
    single { ProfileRepository(get(), get(), androidContext()) }

    // ViewModels
    viewModel { AuthViewModel(get()) }
    viewModel { ClassViewModel(get()) }
    viewModel { StudentAttendanceViewModel(get(), get(), get()) }

    viewModel { TeacherAttendanceViewModel(get(), get(), get()) }
    viewModel { LiveSessionViewModel(get(), get()) }
    viewModel { TeacherReviewsViewModel(get(), get()) }
    viewModel { ProfileViewModel(get()) }
    viewModel { com.attendance.app.face.presentation.FaceRegistrationViewModel(get()) }
    viewModel { com.attendance.app.hod.presentation.HodDashboardViewModel() }
    viewModel { com.attendance.app.admin.presentation.AdminDashboardViewModel() }
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
    single { ClassRepository(get(), get(), get()) }
}

val networkModule = module {
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
    }
}
