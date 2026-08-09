# Логика дня

Native Android application with a playable Balance puzzle. The stack is Kotlin, Jetpack Compose, Navigation 3, DataStore, Room, and Gradle Kotlin DSL.

Requires JDK 17 and Android SDK Platform 36.

Crowns, the second puzzle type, now has a pure-Kotlin domain model, deterministic solving and generation, solve-based difficulty evaluation, and unique/multiple-solution detection in `puzzle-core/`.

Crowns also has a pure-Kotlin gameplay/session layer with user marks, undo/reset, structured conflicts, completion tracking, and logic-based hints.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat ktlintCheck
.\gradlew.bat ktlintFormat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Developer-only Balance generator verification:

```powershell
.\gradlew.bat :puzzle-core:balanceQualityCheck
.\gradlew.bat :puzzle-core:balanceQualityCheck -PbalanceSeeds=100
```

The optional `balanceSeeds` property controls the sequential seed count checked per difficulty.

Developer-only Crowns generator verification:

```powershell
.\gradlew.bat :puzzle-core:crownsQualityCheck
.\gradlew.bat :puzzle-core:crownsQualityCheck -PcrownsSeeds=100
```

The optional `crownsSeeds` property controls the sequential seed count checked per difficulty.

Modules:

- `app/` — Android/Compose application shell, navigation, theme, and settings.
- `puzzle-core/` — deterministic Balance generation/solving plus pure-Kotlin gameplay and diagnostics.

Balance is playable from the Catalog with optional interactive onboarding, selectable difficulties, undo/reset, conflicts, hints, and improved accessibility cues. The Today screen provides a deterministic, resumable Daily Balance challenge while unfinished Catalog progress remains independent.

Completed games are persisted as durable results. The app reports core gameplay statistics and derives current and best Daily streaks from completed Daily history.

Dependency versions are managed in `gradle/libs.versions.toml`.
