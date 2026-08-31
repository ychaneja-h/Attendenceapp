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
