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
.\.venv\Scripts\python.exe -m pip install -r tools\word-lexicon\requirements.lock
.\.venv\Scripts\python.exe tools\word-lexicon\extract_pymorphy3.py
```

`wordLexiconPrepare` regenerates `puzzle-core/src/main/resources/word/v1/` from the offline curated
sources in `lexicon/word/`; V1 is frozen and changing an existing V1 seed-to-answer mapping is not
allowed. The Python tool reproducibly regenerates V2 from pinned, locally installed pymorphy3 Russian
dictionary data, pinned `wordfreq` Russian frequency ranking, and project allow/block files. Android
uses only the generated bundled resources and every V2 difficulty has at least 500 possible answers.

Modules:

- `app/` — Android/Compose application shell, navigation, theme, and settings.
- `puzzle-core/` — deterministic Balance, Crowns, and Word generation/solving plus pure-Kotlin gameplay and diagnostics.
- `lexicon/word/` — curated offline Word corpus sources and their provenance note.

Balance is playable from the Catalog with optional interactive onboarding, selectable difficulties, undo/reset, conflicts, hints, and improved accessibility cues. The Today screen provides a deterministic, resumable Daily challenge that contains Balance, Crowns, and Word (Policy V4, with Word Generator V2): each puzzle is started, resumed, and completed independently in any order, aggregate progress is shown as `0 / 3` … `3 / 3`, and unfinished Catalog progress stays separate. A Word game that ends in failure still completes its Daily entry, so the run and the streak are never blocked. Earlier Daily runs keep their immutable V1, V2, or V3 definitions.

Word (Слово) is the third playable puzzle: EASY uses four letters, MEDIUM five, HARD six, and
EXPERT seven, with six valid attempts at every difficulty. It is available from the Catalog with an
on-screen Russian keyboard, accessible non-color feedback, independent version-aware save/restore,
and a short onboarding. Unlike the other puzzles a Word game is terminal on both outcomes — solved,
or failed after six attempts — and both are recorded.

Completed games are persisted as durable results with a typed outcome and, for Word, the attempts used. The app reports core gameplay statistics and derives current and best Daily streaks from completed Daily history.

All three puzzles share one calm Material 3 shell: a single Light/Dark colour scheme, one spacing and text hierarchy, one difficulty selector, and one loading/error/completion presentation, while each board keeps its own specialised gameplay. Puzzle states are always readable without colour, and the shared UI pieces live in `app/src/main/java/com/stanisryz/logica/ui/components/` and `.../ui/theme/`.

Dependency versions are managed in `gradle/libs.versions.toml`.
