plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sla.briefpopup"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sla.briefpopup"
        // createWindowContext() exige API 30. O aparelho alvo e' Android 16.
        minSdk = 30
        targetSdk = 35
        versionCode = 7
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Zero dependencias externas: so API de plataforma.
}
