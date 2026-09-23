import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

// Keep release credentials on the build machine or in the CI secret store.
val releaseSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("signing.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}

fun releaseSigningValue(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName).orNull
        ?: releaseSigningProperties.getProperty(propertyName)

val validateReleaseSigning by tasks.registering {
    doLast {
        val requiredValues = mapOf(
            "storeFile" to "ATTENDANCE_SIGNING_STORE_FILE",
            "storePassword" to "ATTENDANCE_SIGNING_STORE_PASSWORD",
            "keyAlias" to "ATTENDANCE_SIGNING_KEY_ALIAS",
            "keyPassword" to "ATTENDANCE_SIGNING_KEY_PASSWORD"
        )
        val missing = requiredValues.filter { (propertyName, environmentName) ->
            releaseSigningValue(environmentName, propertyName).isNullOrBlank()
        }.keys
        check(missing.isEmpty()) {
            "Release signing is not configured. Set signing.properties or the " +
                "ATTENDANCE_SIGNING_* environment variables. Missing: ${missing.joinToString()}. " +
                "See RELEASE.md."
        }
        val keyStorePath = releaseSigningValue("ATTENDANCE_SIGNING_STORE_FILE", "storeFile")!!
        check(rootProject.file(keyStorePath).isFile) { "Release keystore not found. See RELEASE.md." }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(validateReleaseSigning)
    }
}

android {
    namespace = "com.attendance.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.attendance.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseSigningValue("ATTENDANCE_SIGNING_STORE_FILE", "storeFile")
                ?.let { rootProject.file(it) }
            storePassword = releaseSigningValue("ATTENDANCE_SIGNING_STORE_PASSWORD", "storePassword")
            keyAlias = releaseSigningValue("ATTENDANCE_SIGNING_KEY_ALIAS", "keyAlias")
            keyPassword = releaseSigningValue("ATTENDANCE_SIGNING_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    // Core
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Koin DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.work)

    // ML Kit Face Detection
    implementation(libs.mlkit.face)

    // TensorFlow Lite
    implementation(libs.tflite)
    implementation(libs.tflite.support)

    // Security
    implementation(libs.security.crypto)
    implementation(libs.argon2kt)

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.json)

    // Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.android)

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Shared module
    implementation(project(":shared"))

    // Testing
    testImplementation(libs.junit)
}



