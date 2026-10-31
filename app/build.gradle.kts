plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.mskynas.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mskynas.tv"
        minSdk = 26
        targetSdk = 35
        // GitHub Actions pasa el número de compilación; en Android Studio queda en 1.
        val build = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
        // Token de TMDB (Calendar): viene del secreto TMDB_TOKEN de GitHub, nunca del código.
        val tmdb = (project.findProperty("tmdbToken") as String?).orEmpty()
        buildConfigField("String", "TMDB_TOKEN", "\"$tmdb\"")
    }

    signingConfigs {
        // Llave fija (en /keystore) para que cada versión se instale encima de la anterior
        // en todas las TVs, sin desinstalar ni volver a iniciar sesión.
        create("home") {
            storeFile = rootProject.file("keystore/mskynas.jks")
            storePassword = "mskynas-home"
            keyAlias = "mskynas"
            keyPassword = "mskynas-home"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("home")
        }
        debug {
            signingConfig = signingConfigs.getByName("home")
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
        compose = true
        // BuildConfig.VERSION_CODE: con él se compara contra la última versión en GitHub.
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.0.0")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
