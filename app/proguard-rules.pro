# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Retrofit & Gson
-keepattributes Signature, Exceptions, *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

-keep class com.streambd.iptv.data.models.** { *; }
-keep class com.streambd.iptv.data.api.** { *; }
-keep interface com.streambd.iptv.data.api.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }

# Javascript Interfaces for Addons WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.annotations.**
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.**
-keepclassmembers class kotlinx.serialization.internal.** {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static <fields>;
    static <methods>;
}
-keep class *$$serializer { *; }

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
