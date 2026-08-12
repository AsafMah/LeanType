import org.gradle.api.JavaVersion

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.parcelize")
}

android {
    compileSdk = 36
    namespace = "com.leanbitlab.leantype.voice.contract"

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        getByName("debug") {
            matchingFallbacks += listOf("debugNoMinify", "runTests")
        }
        getByName("release") {
            matchingFallbacks += listOf("nouserlib")
        }
        create("debugNoMinify") {
            initWith(getByName("debug"))
        }
        create("nouserlib") {
            initWith(getByName("release"))
        }
        create("runTests") {
            initWith(getByName("debug"))
        }
    }

    buildFeatures {
        aidl = true
    }
}
