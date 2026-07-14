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
        versionCode = 1
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

    // AIDL e BuildConfig vem desligados por padrao a partir do AGP 8.x.
    // aidl: precisa pro IFreeformService.aidl (Shizuku UserService).
    // buildConfig: usado so pro BuildConfig.VERSION_CODE no UserServiceArgs.
    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    // Unica excecao ao "zero dependencias externas": Shizuku e' o jeito
    // sem-root de rodar codigo com uid shell, necessario pro freeform de
    // verdade (ActivityOptions.setLaunchWindowingMode e' bloqueado por
    // hidden-API enforcement no processo normal do app - confirmado por
    // logcat no aparelho alvo).
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
