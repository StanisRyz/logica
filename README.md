# Логика дня

Native Android application with playable Balance, Crowns, and Word puzzles. The stack is Kotlin, Jetpack Compose, Navigation 3, DataStore, Room, and Gradle Kotlin DSL.

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

Developer-only Word lexicon and generator verification:

```powershell
.\gradlew.bat :puzzle-core:wordQualityCheck
.\gradlew.bat :puzzle-core:wordLexiconPrepare
```

`wordLexiconPrepare` regenerates `puzzle-core/src/main/resources/word/v1/` from the offline curated
sources in `lexicon/word/`; the generated files are never edited by hand. `WordLexiconV1` is frozen —
changing an existing seed-to-answer mapping requires a new generator version.

Modules:

- `app/` — Android/Compose application shell, navigation, theme, and settings.
- `puzzle-core/` — deterministic Balance, Crowns, and Word generation/solving plus pure-Kotlin gameplay and diagnostics.
- `lexicon/word/` — curated offline Word corpus sources and their provenance note.

Balance is playable from the Catalog with optional interactive onboarding, selectable difficulties, undo/reset, conflicts, hints, and improved accessibility cues. The Today screen provides a deterministic, resumable Daily challenge that now contains Balance, Crowns, and Word (Policy V3): each puzzle is started, resumed, and completed independently in any order, aggregate progress is shown as `0 / 3` … `3 / 3`, and unfinished Catalog progress stays separate. A Word game that ends in failure still completes its Daily entry, so the run and the streak are never blocked. Daily challenges created earlier keep their original Balance-only (V1) or Balance + Crowns (V2) definition.

Word (Слово) is the third playable puzzle: guess a five-letter Russian word in six attempts. It is
available from the Catalog with selectable difficulty, an on-screen Russian keyboard, accessible
non-color feedback, independent save/restore, and a short onboarding. Unlike the other puzzles a Word
game is terminal on both outcomes — solved, or failed after six attempts — and both are recorded.

Completed games are persisted as durable results with a typed outcome and, for Word, the attempts used. The app reports core gameplay statistics and derives current and best Daily streaks from completed Daily history.

Dependency versions are managed in `gradle/libs.versions.toml`.
