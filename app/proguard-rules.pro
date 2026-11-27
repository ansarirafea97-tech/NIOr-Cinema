# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep standard line number information and annotations
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,EnclosingMethod,InnerClasses,Signature,Signature,InnerClasses

# Jetpack Compose UI rules
-keepclassmembers class * extends androidx.compose.ui.node.ModifierNodeElement {
    <init>(...);
}
-dontwarn androidx.compose.**

# AndroidX Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Retrofit, OkHttp & Moshi API rules
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep class *JsonAdapter { *; }
-keep class com.example.model.** { *; }

# Keep App UI & Utility helper classes if they are reflectively accessed
-keep class com.example.util.** { *; }
-keep class com.example.ui.** { *; }

