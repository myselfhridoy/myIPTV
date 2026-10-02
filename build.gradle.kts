// ═══════════════════════════════════════════════════════════════
// Root project build file — plugins are declared here with
// `apply false`, so each module can apply only what it needs.
// ═══════════════════════════════════════════════════════════════

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
// Force Kotlin stdlib to match the Kotlin compiler version, so
// transitive dependencies that pull newer stdlib metadata don't
// break the compiler.
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

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
