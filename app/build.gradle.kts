plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val media3Version = "1.2.1"

android {
    namespace = "com.streambd.iptv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.streambd.iptv"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        // Java 11+ APIs (e.g. URLDecoder.decode(String, Charset)) on old Android
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Kotlin 1.9.22-এর সাথে সামঞ্জস্যপূর্ণ Compose Compiler
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    lint {
        // Fixes the CI failure on `registerForActivityResult`:
        //   InvalidFragmentVersionForActivityResult
        //
        // We use ComponentActivity (Fragment-free), so this Fragment-version
        // check does not apply. It's a Google-recognized false positive.
        disable += "InvalidFragmentVersionForActivityResult"

        // Keep failing on real lint errors — only the false positive is silenced.
        abortOnError = true
    }
}

dependencies {
    // Desugaring library
coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-config")

    // AndroidX core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Compose
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-graphics:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")

    // TV Material & Foundation
    val tvComposeVersion = "1.0.0-alpha10"
    implementation("androidx.tv:tv-foundation:$tvComposeVersion")
    implementation("androidx.tv:tv-material:$tvComposeVersion")

    // Media3
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // Retrofit & Serialization
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.google.code.gson:gson:2.10.1")

    // Room — Kotlin 1.9.22 + KSP1-এর সাথে সামঞ্জস্যপূর্ণ
    // (2.6.1-ও কাজ করবে, 2.7.1 ভবিষ্যতের জন্য প্রস্তুত)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // QuickJS (VOD addon system) — আপনার সফল ভার্সন
    implementation("io.github.dokar3:quickjs-kt-android:1.0.15")
    testImplementation("io.github.dokar3:quickjs-kt-jvm:1.0.15")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Image loading & Navigation
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // NewPipeExtractor for YouTube Live & Video stream extraction
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

    // ─────────────────────────────────────────────────────────────────────
    // Conscrypt — Android's official TLS provider (Google BoringSSL).
    //
    // WHY THIS IS HERE
    // ----------------
    // Cloudflare's Bot Management evaluates the TLS handshake's JA3
    // fingerprint. On high-risk ISP IPs (some WiFi networks flagged for
    // bot traffic), Cloudflare only accepts requests whose JA3 matches a
    // whitelisted client (curl, Chrome, Firefox). OkHttp's default
    // BoringSSL variant on Android has a *different* JA3 — flagged as
    // suspicious — so those requests get a 403 even with perfect headers.
    //
    // curl passes because it uses OpenSSL (whitelisted).
    // Chrome passes because its JA3 is whitelisted.
    // OkHttp fails because its BoringSSL variant is not on the list.
    //
    // Installing Conscrypt as the top-priority JSSE provider makes
    // OkHttp use Google's BoringSSL — the SAME TLS stack Chrome uses —
    // so the JA3 fingerprint Cloudflare sees matches a browser and the
    // 403 disappears. Works on WiFi and mobile data identically.
    // ─────────────────────────────────────────────────────────────────────
    implementation("org.conscrypt:conscrypt-android:2.5.2")
}
