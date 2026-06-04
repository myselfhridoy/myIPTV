# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Retrofit & Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.streambd.iptv.data.models.** { *; }
-keep class com.streambd.iptv.data.api.** { *; }
-keep interface com.streambd.iptv.data.api.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }

# Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.annotations.**
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.**
-keepclassmembers class kotlinx.serialization.internal.** {
    *** Companion;
}

# Keep Utils (AddonManager, Parsers) to prevent obfuscation breaking them
-keep class com.streambd.iptv.utils.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# QuickJS (used in AddonManager)
-keep class app.cash.quickjs.** { *; }
-dontwarn app.cash.quickjs.**

# Firebase
-keep class com.google.firebase.** { *; }
