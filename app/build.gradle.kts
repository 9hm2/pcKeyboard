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
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        // Stable debug keystore committed to the repo so debug APKs built
        // anywhere (local / CI / another machine) share the same signature.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            // Bundled keystore — override via env vars / CI secrets if you need
            // a different signing identity.
            val keystoreFile = file(
                System.getenv("PCK_KEYSTORE_FILE") ?: "release.keystore"
            )
            storeFile = keystoreFile
            storePassword = System.getenv("PCK_KEYSTORE_PASSWORD")
                ?: "pcKeyboardRelease2026"
            keyAlias = System.getenv("PCK_KEY_ALIAS") ?: "pckeyboard"
            keyPassword = System.getenv("PCK_KEY_PASSWORD")
                ?: "pcKeyboardRelease2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
