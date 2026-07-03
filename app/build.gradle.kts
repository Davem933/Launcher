import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    kotlin("kapt")
}

// API key lives in local.properties (not committed), never in source code
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapyczApiKey: String = localProps.getProperty("MAPYCZ_API_KEY", "MAPY_API_KEY_HERE")

android {
    namespace = "com.example.carlauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.carlauncher"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "MAPYCZ_API_KEY", "\"$mapyczApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.play.services.location)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.coroutines.android)

    // Material icons (SkipNext, SkipPrevious, MusicNote etc. — not in core icons)
    implementation("androidx.compose.material:material-icons-extended")

    // DataStore — dock slot persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room — trip data persistence
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // MapLibre
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    // PMTiles HTTP server (serves PMTiles file to MapLibre via localhost)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
