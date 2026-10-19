plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
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

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// Only the benchmark variant makes sense — measuring debug would be meaningless.
androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
