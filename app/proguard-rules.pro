# ═══════════════════════════════════════════════════════════════
# StreamBD IPTV — R8/ProGuard rules
# ═══════════════════════════════════════════════════════════════

# ── General attributes ──
-keepattributes Signature, Exceptions, *Annotation*, InnerClasses,
                EnclosingMethod, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ── Enum values (used in models & QuickJS bridge) ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ═══════════════════════════════════════════════════════════════
# Networking — Retrofit / OkHttp / OkIO / Gson
# ═══════════════════════════════════════════════════════════════
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.** { *; }
-dontwarn okhttp3.internal.**

-keep class okio.** { *; }
-dontwarn okio.**

-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ── Our app data / api layers (kotlinx.serialization + Retrofit POJOs) ──
-keep class com.streambd.iptv.data.models.** { *; }
-keep class com.streambd.iptv.data.api.** { *; }
-keep interface com.streambd.iptv.data.api.** { *; }

# ── All utils kept (AddonManager, Parsers, Resolvers, JNI bridge, …) ──
-keep class com.streambd.iptv.utils.** { *; }

# Native method linkage — CryptoUtils.getNativeAesKey / getNativeAesIv
# The JNI symbol names are generated from the FULL class + method name, so
# obfuscation would break the native lookup. (Already covered by the
# `utils.**` keep above, restated for clarity.)
-keepclasseswithmembernames class com.streambd.iptv.utils.CryptoUtils {
    native <methods>;
}

# ═══════════════════════════════════════════════════════════════
# Kotlinx Serialization
# ═══════════════════════════════════════════════════════════════
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

# ═══════════════════════════════════════════════════════════════
# Room
# ═══════════════════════════════════════════════════════════════
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ═══════════════════════════════════════════════════════════════
# QuickJS (dokar3/quickjs-kt) — CORRECTED package name
#
# The dependency is `io.github.dokar3:quickjs-kt-android`, but the actual
# Kotlin package inside the AAR is `com.dokar.quickjs` (the `dokar3` part is
# only the Maven group ID). Keeping the old `app.cash.quickjs` name (from
# the deprecated Cash App library) has no effect and lets R8 strip the
# real QuickJS classes, breaking every VOD addon and JS playlist at runtime.
# ═══════════════════════════════════════════════════════════════
-keep class com.dokar.quickjs.** { *; }
-dontwarn com.dokar.quickjs.**

# ═══════════════════════════════════════════════════════════════
# Conscrypt — TLS provider installed via Security.insertProviderAt()
#
# Conscrypt registers itself through JSSE's SPI. R8 obfuscation renames the
# Provider class and breaks the ServiceLoader lookup → the Conscrypt
# provider silently fails to install in MainActivity.onCreate(), and the
# Cloudflare JA3-bypass feature stops working on flagged ISP IPs.
# ═══════════════════════════════════════════════════════════════
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# BouncyCastle / OpenJSSE — pulled in transitively by some TLS paths
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ═══════════════════════════════════════════════════════════════
# Firebase
# ═══════════════════════════════════════════════════════════════
-keep class com.google.firebase.** { *; }

# ═══════════════════════════════════════════════════════════════
# NewPipeExtractor + Rhino JS engine (YouTube resolver)
# ═══════════════════════════════════════════════════════════════
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn org.jsoup.**
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.jsoup.** { *; }

# ═══════════════════════════════════════════════════════════════
# Media3 / ExoPlayer
# ═══════════════════════════════════════════════════════════════
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ═══════════════════════════════════════════════════════════════
# Coil image loader
# ═══════════════════════════════════════════════════════════════
-keep class coil.** { *; }
-dontwarn coil.**

# ═══════════════════════════════════════════════════════════════
# Kotlinx Coroutines
# ═══════════════════════════════════════════════════════════════
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.debug.**
-keep class kotlinx.coroutines.android.** { *; }

# ═══════════════════════════════════════════════════════════════
# Compose — keep runtime pieces that the compiler plugin relies on
# ═══════════════════════════════════════════════════════════════
-keep class androidx.compose.runtime.** { *; }

# ═══════════════════════════════════════════════════════════════
# WebView JavaScript bridges — still used by WebViewSniffer and
# JsPlaylistParser (both register @JavascriptInterface methods).
# ═══════════════════════════════════════════════════════════════
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
