# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Personal Android Car Launcher for Lenovo Tab M10 Plus (3rd Gen). Landscape-locked fullscreen app that replaces the default Android launcher. Built with Kotlin + Jetpack Compose + Hilt.

**Target:** minSdk 31, targetSdk 34, screen ~1143×686 dp landscape only.

## Build Commands

All build/run commands require Android Studio or the Android SDK with Gradle wrapper. Open the project root in Android Studio — it will generate `gradle/wrapper/` on first sync.

```bash
# Assemble debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run lint
./gradlew lint

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.example.carlauncher.ExampleTest"
```

## Architecture

Single-module app (`app/`). No multi-module split yet.

**DI:** Hilt (`@HiltAndroidApp` on `CarLauncherApp`, `@AndroidEntryPoint` on `MainActivity`). All ViewModels injected via `@HiltViewModel`.

**UI layer:** Jetpack Compose throughout. Entry point is `MainActivity → CarLauncherTheme → LauncherScreen`. New screens/widgets go under `ui/` grouped by feature:

```
ui/
  launcher/   ← main screen composables
  theme/      ← CarLauncherTheme, color tokens
  <feature>/  ← future: map, music, speed, dock
```

**Theme:** `ui/theme/Theme.kt` defines `CarColorScheme` (dark, no light variant). Key colors: background `#0D0D0F`, surface `#1A1A1F`, primary (accent green) `#00C853`. Always use `MaterialTheme.colorScheme.*` tokens rather than hardcoded hex in new composables.

**Fullscreen:** `MainActivity` hides status + nav bars via `WindowInsetsControllerCompat` and sets `FLAG_KEEP_SCREEN_ON`. Do not call `enableEdgeToEdge()` — it conflicts with the manual insets setup.

**Manifest:** `MainActivity` has `HOME` + `DEFAULT` categories making it a system launcher. `screenOrientation="landscape"` + `windowSoftInputMode="adjustNothing"` are intentional and must not be changed.

## Planned Modules (not yet implemented)

- Map widget (GPS/location — permissions already declared)
- Speed display (location-based)
- Music/media widget
- App dock (QUERY_ALL_PACKAGES permission already declared)
