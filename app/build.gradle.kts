plugins {
    id("com.android.application")
    // AGP 9's built-in Kotlin support replaces the org.jetbrains.kotlin.android plugin.
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.griboedov.sentencecards"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.griboedov.sentencecards"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Sentence translation (see data/translation/DeepLTranslator.kt) needs a DeepL API key.
        // Supplied via an env var at build time - never committed to source - and left "" (i.e.
        // translation quietly disabled, see NoOpTranslator) when not set, e.g. for local dev.
        buildConfigField("String", "DEEPL_API_KEY", "\"${System.getenv("DEEPL_API_KEY").orEmpty()}\"")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // kuromoji-ipadic and its kuromoji-core dependency both ship the same project-level
            // META-INF docs (CONTRIBUTORS.md etc.) - identical either way, so just keep one copy.
            excludes += "/META-INF/{CONTRIBUTORS.md,LICENSE.md,NOTICE.md}"
        }
    }

    // The bundled dictionary (assets/dictionary/jmdict.db) is copied out to internal storage and
    // opened with the plain SQLite APIs on first use - keeping it uncompressed in the APK avoids
    // paying compression overhead on every one of those first-run copies.
    androidResources {
        noCompress += "db"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.5")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // On-device Japanese morphological tokenizer for the plain-text book import path
    // (data/importer/BookImporter.kt) - pure Java/Kotlin, bundles the IPADIC dictionary, no
    // native code or network access needed. Mirrors what tools/import_book.py does offline with
    // fugashi/unidic-lite, so the app can do the same job when running that script isn't an option.
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
