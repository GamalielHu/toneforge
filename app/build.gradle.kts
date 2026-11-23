plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val numeroCompilacion = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.gamalielhu.toneforge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gamalielhu.toneforge"
        minSdk = 26
        targetSdk = 35
        versionCode = numeroCompilacion
        versionName = "1.$numeroCompilacion"
    }

    signingConfigs {
        create("fija") {
            storeFile = file("firma.jks")
            storePassword = "toneforge"
            keyAlias = "toneforge"
            keyPassword = "toneforge"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fija") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fija")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
