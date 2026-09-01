-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt

# Kotlinx Serialization
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# TFLite
-keep class org.tensorflow.lite.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
-keep class com.attendance.shared.dto.** { *; }
-keep class com.attendance.shared.models.** { *; }
# Koin
-keep class org.koin.** { *; }

# Serialization
-keepattributes *Annotation*, InnerClasses
-keep class kotlinx.serialization.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose / Navigation
-keep class androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }
-keep class com.attendance.app.** { *; }
