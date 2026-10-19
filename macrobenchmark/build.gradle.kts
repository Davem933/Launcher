plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

// Performance tests (startup + frame timing) for :app, run on a real device:
//   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
// Results land in macrobenchmark/build/outputs/connected_android_test_additional_output/.
android {
    namespace = "com.example.carlauncher.macrobenchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Must match the app's "benchmark" build type (release-like, signed with debug key).
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Baseline Profile producer: `./gradlew :app:generateBaselineProfile` runs
// BaselineProfileGenerator on the connected tablet (no Gradle-managed emulator).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// Only release-like variants make sense — measuring debug would be meaningless.
// "benchmark" = our measurements, "nonMinifiedRelease" = Baseline Profile generation
// (created by the baselineprofile plugin; profiles must come from un-obfuscated code).
androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark" || it.buildType == "nonMinifiedRelease"
    }
}
