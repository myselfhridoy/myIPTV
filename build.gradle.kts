plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.3" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}

// ═══════════════════════════════════════════════════════════════
// Kotlin stdlib ভার্সন জোরপূর্বক 2.2.20-এ নামিয়ে আনা হচ্ছে
// যাতে ট্রানজিটিভ ডিপেন্ডেন্সি থেকে আসা 2.4.10 মেটাডেটা
// কম্পাইলার 2.2.20-এর সাথে সংঘর্ষ না করে।
// ═══════════════════════════════════════════════════════════════
allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.20")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.20")
        }
    }
}
