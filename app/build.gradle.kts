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
    compileSdk = 35 // Android 15 (API 35) — স্থিতিশীল ও নিরাপদ

    defaultConfig {
        applicationId = "com.streambd.iptv"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "4.0"

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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // "-Xskip-metadata-version-check" সরানো হয়েছে — এটি বিপজ্জনক
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Kotlin 1.9.22-এর সাথে সামঞ্জস্যপূর্ণ Compose Compiler
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    lint {
        disable += "InvalidFragmentVersionForActivityResult"
        abortOnError = true
    }
}

dependencies {
    // Desugaring library
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-config")

    // AndroidX core — পিন করা ভার্সন (১.১৩.১, AGP 8.5.2 এর সাথে সামঞ্জস্যপূর্ণ)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // ═══════════════════════════════════════════════════════════════
    // Compose — BOM 2024.05.00 ব্যবহার করা হচ্ছে
    // এটি Compose UI 1.6.8 এবং androidx.core 1.13.0 আনবে,
    // যা AGP 8.5.2-এর সাথে সম্পূর্ণ সামঞ্জস্যপূর্ণ।
    // ═══════════════════════════════════════════════════════════════
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ═══════════════════════════════════════════════════════════════
    // TV Material & Foundation — stable releases (AGP 8.5.2 সামঞ্জস্যপূর্ণ)
    // tv-material: 1.0.0 এবং tv-foundation: 1.0.0 উভয়ই AGP 8.5.2-এর সাথে কাজ করে।
    // ═══════════════════════════════════════════════════════════════
    implementation("androidx.tv:tv-foundation:1.0.0")
    implementation("androidx.tv:tv-material:1.0.0")

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

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // QuickJS (VOD addon system)
    implementation("io.github.dokar3:quickjs-kt-android:1.0.15")
    testImplementation("io.github.dokar3:quickjs-kt-jvm:1.0.15")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Image loading & Navigation
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // NewPipeExtractor for YouTube Live & Video stream extraction
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
}
