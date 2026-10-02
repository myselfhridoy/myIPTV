import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
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
        targetSdk = 36
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += "InvalidFragmentVersionForActivityResult"
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // ═══════════════════════════════════════════════════════════════
    // Core Library Desugaring
    // ═══════════════════════════════════════════════════════════════
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // ═══════════════════════════════════════════════════════════════
    // Firebase
    // ═══════════════════════════════════════════════════════════════
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-config")

    // ═══════════════════════════════════════════════════════════════
    // AndroidX Core
    // ═══════════════════════════════════════════════════════════════
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // ═══════════════════════════════════════════════════════════════
    // Compose BOM
    // ═══════════════════════════════════════════════════════════════
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ═══════════════════════════════════════════════════════════════
    // TV Material & Foundation
    // ═══════════════════════════════════════════════════════════════
    implementation("androidx.tv:tv-foundation:1.0.0")
    implementation("androidx.tv:tv-material:1.1.0")

    // ═══════════════════════════════════════════════════════════════
    // Media3
    // ═══════════════════════════════════════════════════════════════
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // ═══════════════════════════════════════════════════════════════
    // Retrofit & Serialization
    // ═══════════════════════════════════════════════════════════════
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.google.code.gson:gson:2.10.1")

    // ═══════════════════════════════════════════════════════════════
    // Room — Kotlin 2.2.x + KSP2 সামঞ্জস্যপূর্ণ ভার্সন
    // ═══════════════════════════════════════════════════════════════
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ═══════════════════════════════════════════════════════════════
    // QuickJS — VOD addon system
    // ═══════════════════════════════════════════════════════════════
    implementation("io.github.dokar3:quickjs-kt-android:1.0.15")
    testImplementation("io.github.dokar3:quickjs-kt-jvm:1.0.15")

    // ═══════════════════════════════════════════════════════════════
    // Coroutines — Kotlin 2.2.20 সামঞ্জস্যপূর্ণ
    // ═══════════════════════════════════════════════════════════════
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // ═══════════════════════════════════════════════════════════════
    // Image loading & Navigation
    // ═══════════════════════════════════════════════════════════════
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ═══════════════════════════════════════════════════════════════
    // NewPipeExtractor — YouTube Live & Video stream extraction
    // ═══════════════════════════════════════════════════════════════
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
}
