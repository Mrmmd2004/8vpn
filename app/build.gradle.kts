plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.vpn8"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.vpn8"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.8"
    }

    val ksPath = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ksPath != null && file(ksPath).exists()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Falls back to the debug key when no release keystore is provided.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Three outputs: armeabi-v7a, arm64-v8a and a universal APK (v7a + v8a + x86_64).
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    // The Aether / Psiphon / Lyrebird executables ship as lib*.so and are run from
    // nativeLibraryDir, so they must be extracted to disk.
    packaging { jniLibs { useLegacyPackaging = true } }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Clear error when the sing-box library has not been put in place.
if (!file("libs/libbox.aar").exists()) {
    throw GradleException(
        "app/libs/libbox.aar is missing. Build it with the 'libbox' job of .github/workflows/main.yml " +
            "(or copy a libbox.aar built from sing-box into app/libs/) and try again."
    )
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // sing-box (libbox), built by the "libbox" job of the GitHub workflow into app/libs/libbox.aar.
    implementation(files("libs/libbox.aar"))
}
