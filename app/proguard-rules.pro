# ==============================================================================
# ProGuard / R8 Configuration for University Attendance & Management System
# ==============================================================================

# General Optimization & Attributes
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, SourceFile, LineNumberTable
-dontwarn javax.annotation.**

# ------------------------------------------------------------------------------
# Kotlin & Kotlinx Serialization
# ------------------------------------------------------------------------------
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$$serializer {
    *;
}
-keep @kotlinx.serialization.Serializable class * {
    *;
}
-keep,allowobfuscation,allowshrinking class * {
    <init>(...);
}

# ------------------------------------------------------------------------------
# Application Domain Models
# ------------------------------------------------------------------------------
-keep class com.akshit.attendance.data.model.** { *; }
-keepclassmembers class com.akshit.attendance.data.model.** { *; }

# ------------------------------------------------------------------------------
# Retrofit 2 & OkHttp 3 Networking
# ------------------------------------------------------------------------------
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ------------------------------------------------------------------------------
# Jetpack Compose & Navigation
# ------------------------------------------------------------------------------
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.navigation.** { *; }

# ------------------------------------------------------------------------------
# Airbnb Lottie Compose
# ------------------------------------------------------------------------------
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ------------------------------------------------------------------------------
# Firebase Cloud Messaging & Analytics
# ------------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.akshit.attendance.notification.** { *; }

# ------------------------------------------------------------------------------
# AndroidX DataStore
# ------------------------------------------------------------------------------
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**
