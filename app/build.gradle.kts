import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
    kotlin("kapt")
}

// API key lives in local.properties (not committed), never in source code
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// local.properties wins; otherwise the machine-wide ~/.gradle/gradle.properties, so a fresh
// worktree (which never has local.properties) still builds with real keys.
fun secret(name: String): String? =
    localProps.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
            .also { if (it == null) logger.warn("⚠ $name chybí v local.properties i ~/.gradle/gradle.properties") }

val mapboxAccessToken: String = secret("MAPBOX_ACCESS_TOKEN") ?: "MAPBOX_TOKEN_HERE"

// Incident Recorder feature toggle — disable locally with INCIDENT_RECORDER_ENABLED=false
val incidentRecorderEnabled: String = localProps.getProperty("INCIDENT_RECORDER_ENABLED", "true")

// Groq API klíč pro hlasové poznámky (free plán) — local.properties nebo ~/.gradle/gradle.properties,
// nikdy v gitu. Prázdný → widget ukáže "Chybí Groq API klíč" a nenahrává.
val groqApiKey: String = secret("GROQ_API_KEY") ?: ""

android {
    namespace = "com.example.carlauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.carlauncher"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", "\"$mapboxAccessToken\"")
        buildConfigField("boolean", "INCIDENT_RECORDER_ENABLED", incidentRecorderEnabled)
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
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
        // Release-like build for :macrobenchmark (performance testing). Debug Compose is several
        // times slower than release, so never measure the debug build. Own applicationId so the
        // measured app isn't the default HOME — otherwise the system relaunches it right after
        // macrobenchmark kills the process and the "cold" start isn't cold.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".benchmark"
            isDebuggable = false
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

    testOptions {
        // android.util.Log apod. vrací v JVM testech default místo "Method not mocked"
        unitTests.isReturnDefaultValues = true
    }
}

// Baseline Profile (fáze 2 výkonu): generuje :macrobenchmark (BaselineProfileGenerator) na
// připojeném tabletu přes `./gradlew :app:generateBaselineProfile`; výsledek se commituje do
// src/main/generated/baselineProfiles/ a profileinstaller ho při instalaci předá ART.
baselineProfile {
    // Jen ručně — generování potřebuje tablet a trvá minuty, nesmí běžet při každém buildu.
    automaticGenerationDuringBuild = false
    saveInSrc = true
    // Do main, ať profil dostanou všechny varianty včetně "benchmark".
    mergeIntoMain = true
}
// Plugin-created variant the profile is generated from (release without R8). Same id + debug
// signing as "benchmark": installs next to the debug build on the tablet instead of replacing
// it, and isn't the default HOME (which the system would relaunch after every process kill).
// (The plugin copies release's applicationIdSuffix over the DSL, hence the Variant API for the id.)
android.buildTypes.matching { it.name == "nonMinifiedRelease" }.configureEach {
    signingConfig = android.signingConfigs.getByName("debug")
}
androidComponents.onVariants(androidComponents.selector().withBuildType("nonMinifiedRelease")) {
    it.applicationId.set("com.example.carlauncher.benchmark")
}
// The plugin copies release's source dirs as strings into its build types; kapt registers its
// generated dir as a Provider, which turns into a literal "provider(?)" path and breaks
// kaptGenerateStubsNonMinifiedReleaseKotlin on Gradle 9 ("Could not stat file …/provider(?)").
// Runs after the plugin's own finalizeDsl (registered first by the plugins block).
androidComponents.finalizeDsl { ext ->
    ext.sourceSets.matching { it.name == "nonMinifiedRelease" || it.name == "benchmarkRelease" }
        .configureEach {
            listOf(java, kotlin).forEach { dirs -> dirs.directories.removeIf { "provider(" in it } }
        }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":macrobenchmark"))
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
    testImplementation("junit:junit:4.13.2")

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

    // WorkManager — offline fronta zpracování hlasových poznámek (Groq)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    // CameraX — Incident Recorder video capture + frame analysis
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)

    // ML Kit Text Recognition (bundled Latin model — fully offline, no Play Services)
    implementation(libs.mlkit.text.recognition)

    // Mapbox Maps SDK
    implementation("com.mapbox.maps:android:11.30.1")

    // Mapbox Navigation SDK — Fáze 3, turn-by-turn navigace
    implementation("com.mapbox.navigationcore:navigation:3.30.1")
    implementation("com.mapbox.navigationcore:ui-components:3.30.1")

    // Mapbox Search SDK — Fáze 3, vyhledávání cíle
    implementation("com.mapbox.search:mapbox-search-android:2.30.1")
    implementation("com.mapbox.search:mapbox-search-android-ui:2.30.1")

    // Unit testy (JVM) — org.json přebíjí prázdné stuby z android.jar
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.org.json)
}
