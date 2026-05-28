plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pckeyboard.ime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pckeyboard.ime"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release keystore is NOT committed to the repo — provide it locally
    // (default path: app/release.keystore) or point at it through the
    // PCK_KEYSTORE_FILE env var. Password / alias / key-password also come
    // from env vars so nothing sensitive is checked in.
    val releaseKeystoreFile = System.getenv("PCK_KEYSTORE_FILE")?.let { file(it) }
        ?: file("release.keystore")
    val hasReleaseKeystore = releaseKeystoreFile.exists() &&
        System.getenv("PCK_KEYSTORE_PASSWORD") != null &&
        System.getenv("PCK_KEY_PASSWORD") != null

    signingConfigs {
        // Stable debug keystore committed to the repo so debug APKs built
        // anywhere (local / CI / another machine) share the same signature.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = System.getenv("PCK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PCK_KEY_ALIAS") ?: "pckeyboard"
                keyPassword = System.getenv("PCK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            // If hasReleaseKeystore is false the release APK is unsigned —
            // assembleRelease still builds; signing is the caller's job.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
}
