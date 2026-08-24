// Top-level build file; per-module configuration happens in app/build.gradle.kts
plugins {
    // AGP 9's built-in Kotlin support means org.jetbrains.kotlin.android is no longer applied;
    // these Kotlin sub-plugins (Compose compiler, kotlinx.serialization) and KSP still are.
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
}
