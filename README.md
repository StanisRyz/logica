# Логика дня

Native Android application with playable Balance and Crowns puzzles. The stack is Kotlin, Jetpack Compose, Navigation 3, DataStore, Room, and Gradle Kotlin DSL.

Requires JDK 17 and Android SDK Platform 36.

Crowns, the second puzzle type, now has a pure-Kotlin domain model, deterministic solving and generation, solve-based difficulty evaluation, and unique/multiple-solution detection in `puzzle-core/`.

Crowns also has a pure-Kotlin gameplay/session layer with user marks, undo/reset, structured conflicts, completion tracking, and logic-based hints. Its complete Android experience is available from the Catalog with selectable difficulty, independent save/restore, explainable hints, and a short replayable interactive onboarding, and it is also part of the Daily challenge.

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

Balance is playable from the Catalog with optional interactive onboarding, selectable difficulties, undo/reset, conflicts, hints, and improved accessibility cues. The Today screen provides a deterministic, resumable Daily challenge that now contains both Balance and Crowns: each puzzle is started, resumed, and completed independently in either order, aggregate progress is shown as `0 / 2` … `2 / 2`, and unfinished Catalog progress stays separate. Daily challenges created before this change keep their original Balance-only definition.

Completed games are persisted as durable results. The app reports core gameplay statistics and derives current and best Daily streaks from completed Daily history.

Dependency versions are managed in `gradle/libs.versions.toml`.
