import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun signingValue(env: String, property: String, fallback: String = ""): String =
    System.getenv(env) ?: keystoreProperties.getProperty(property) ?: fallback

android {
    namespace = "top.dynesshely.chargesentinel"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(signingValue(
                "RELEASE_KEYSTORE_PATH", "storeFile", "release.keystore"))
            storePassword = signingValue("RELEASE_STORE_PASSWORD", "storePassword")
            keyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias", "chargesentinel")
            keyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
        }
        getByName("debug") {
            storeFile = file(System.getenv("DEBUG_KEYSTORE_PATH")
                ?: System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "top.dynesshely.chargesentinel"
        minSdk = 30
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.1"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation(libs.junit)
}
