$basePath = "d:\Projects\myIPTV"

# Create directories
$dirs = @(
    "$basePath\app\src\main\java\com\myselfhridoy\streambdplayer\ui\theme",
    "$basePath\app\src\main\res\values",
    "$basePath\app\src\main\res\xml",
    "$basePath\app\src\main\res\mipmap-anydpi-v26",
    "$basePath\app\src\main\res\mipmap-hdpi",
    "$basePath\.github\workflows",
    "$basePath\gradle\wrapper"
)
foreach ($dir in $dirs) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

# Write settings.gradle.kts
@"
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "StreamBD IPTV"
include(":app")
"@ | Out-File -FilePath "$basePath\settings.gradle.kts" -Encoding utf8

# Write build.gradle.kts (Project)
@"
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
}
"@ | Out-File -FilePath "$basePath\build.gradle.kts" -Encoding utf8

# Write gradle.properties
@"
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
"@ | Out-File -FilePath "$basePath\gradle.properties" -Encoding utf8

# Write app/build.gradle.kts
@"
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.myselfhridoy.streambdplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myselfhridoy.streambdplayer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")

    // Media3
    val media3Version = "1.2.1"
    implementation("androidx.media3:media3-exoplayer:`$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:`$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:`$media3Version")
    implementation("androidx.media3:media3-ui:`$media3Version")

    // Retrofit & Serialization
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // QuickJS for VOD Addon system
    implementation("app.cash.quickjs:quickjs-android:0.9.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
"@ | Out-File -FilePath "$basePath\app\build.gradle.kts" -Encoding utf8

# Write AndroidManifest.xml
@"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.StreamBDPlayer"
        android:usesCleartextTraffic="true"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.StreamBDPlayer">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"@ | Out-File -FilePath "$basePath\app\src\main\AndroidManifest.xml" -Encoding utf8

# Write xml resources
@"
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true" />
</data-extraction-rules>
"@ | Out-File -FilePath "$basePath\app\src\main\res\xml\data_extraction_rules.xml" -Encoding utf8

@"
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <include domain="sharedpref" path="."/>
</full-backup-content>
"@ | Out-File -FilePath "$basePath\app\src\main\res\xml\backup_rules.xml" -Encoding utf8

@"
<resources>
    <string name="app_name">StreamBD IPTV</string>
</resources>
"@ | Out-File -FilePath "$basePath\app\src\main\res\values\strings.xml" -Encoding utf8

@"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.StreamBDPlayer" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
"@ | Out-File -FilePath "$basePath\app\src\main\res\values\themes.xml" -Encoding utf8

# Write MainActivity.kt
@"
package com.myselfhridoy.streambdplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myselfhridoy.streambdplayer.ui.theme.StreamBDPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreamBDPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("StreamBD IPTV")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Welcome to `$name!",
        modifier = modifier
    )
}
"@ | Out-File -FilePath "$basePath\app\src\main\java\com\myselfhridoy\streambdplayer\MainActivity.kt" -Encoding utf8

# Write Theme
@"
package com.myselfhridoy.streambdplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF00ADB5),
    background = androidx.compose.ui.graphics.Color(0xFF1E2024),
    surface = androidx.compose.ui.graphics.Color(0xFF2A2D32)
)

@Composable
fun StreamBDPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
"@ | Out-File -FilePath "$basePath\app\src\main\java\com\myselfhridoy\streambdplayer\ui\theme\Theme.kt" -Encoding utf8

# GitHub Actions workflow
@"
name: Android Build

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - name: set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew

    - name: Build with Gradle
      run: ./gradlew assembleDebug

    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
"@ | Out-File -FilePath "$basePath\.github\workflows\build.yml" -Encoding utf8

Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar" -OutFile "$basePath\gradle\wrapper\gradle-wrapper.jar"
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.properties" -OutFile "$basePath\gradle\wrapper\gradle-wrapper.properties"
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/master/gradlew" -OutFile "$basePath\gradlew"
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/master/gradlew.bat" -OutFile "$basePath\gradlew.bat"

Write-Host "Setup Complete!"
