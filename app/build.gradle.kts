plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "in.swatantra.focus"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.swatantra.focus"
        minSdk = 30
        targetSdk = 35
        versionCode = 710
        versionName = "7.1.0-zero-harm"
        resConfigs("en", "hi")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = false
        buildConfig = false
    }
}

dependencies {
    // Pure Android SDK bootclasspath only -- <1.2 MB APK, <15 MB RAM, 0% idle CPU
    // JUnit4 for local JVM unit tests ONLY -- zero runtime APK dependency added
    testImplementation("junit:junit:4.13.2")
}
