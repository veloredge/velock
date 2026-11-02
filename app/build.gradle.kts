import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing is optional: it reads keystore.properties, which is never committed.
// Without it the project still builds; only `assembleRelease` produces an unsigned APK.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.veloredge.velock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.veloredge.velock"
        minSdk = 28
        // Deliberately 34: it keeps the overlay and activity layout behaviour this app
        // was verified against, including non edge-to-edge activities.
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Nothing to obfuscate in 900 lines of framework-only code, and an
            // unobfuscated APK keeps the published artifact readable.
            isMinifyEnabled = false
            isShrinkResources = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Intentionally no dependencies: the whole app runs on framework APIs only.
// Smaller APK, no AndroidX, nothing extra resident in memory.
