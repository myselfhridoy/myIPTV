# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Retrofit & Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.streambd.iptv.data.models.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }

# Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.annotations.**
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.**
-keepclassmembers class kotlinx.serialization.internal.** {
    *** Companion;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# QuickJS (used in AddonManager)
-keep class app.cash.quickjs.** { *; }
-dontwarn app.cash.quickjs.**

# Firebase
-keep class com.google.firebase.** { *; }
