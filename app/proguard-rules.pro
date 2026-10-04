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
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-keep class com.streambd.iptv.data.models.** { *; }
-keep class com.streambd.iptv.data.api.** { *; }
-keep interface com.streambd.iptv.data.api.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

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

# Keep Utils (AddonManager, Parsers, Resolvers) to prevent obfuscation breaking them
-keep class com.streambd.iptv.utils.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# QuickJS (used in AddonManager)
-keep class app.cash.quickjs.** { *; }
-dontwarn app.cash.quickjs.**

# Firebase
-keep class com.google.firebase.** { *; }

# NewPipeExtractor & Rhino Javascript Engine
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn org.jsoup.**
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }

# OkHttp3 — CookieJar, Cookie, internal classes used by DlhdResolver
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.** { *; }
-dontwarn okhttp3.internal.**

# OkIO
-keep class okio.** { *; }
-dontwarn okio.**

# Media3 / ExoPlayer — DataSource, HLS, DRM
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil image loader
-keep class coil.** { *; }
-dontwarn coil.**

# Kotlinx Coroutines — debug info for crash traces
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.debug.**
-keep class kotlinx.coroutines.android.** { *; }

# Keep Enum values (used in models)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Preserve line number info for crash reports (Firebase Crashlytics)
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
