plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jlxc.amapvoiceassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jlxc.amapvoiceassistant"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "2.0-offline-paraformer"
    }

    ndk {
        abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }
}

kotlin {
    jvmToolchain(17)
}
