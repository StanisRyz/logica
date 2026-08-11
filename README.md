# Логика дня

Native Android application with playable Balance, Crowns, Word, Sudoku, and 2048 puzzles. The stack is Kotlin, Jetpack Compose, Navigation 3, DataStore, Room, and Gradle Kotlin DSL.

Sudoku uses a frozen, validated Puzzle Bank corpus bundled offline; puzzles are never generated or
downloaded at runtime. It is the fourth Catalog game, with EASY, MEDIUM, HARD, and EXPERT selection,
independent save/restore, onboarding, economy/results, and Profile statistics. Sudoku remains outside
the three-game Daily challenge until the later five-game Daily stage.

2048 is the fifth Catalog game. New games are scored: Легко, Средне, Сложно, and Эксперт ask for
12 000, 30 000, 100 000, and 250 000 points. Reaching the goal does not end the game — you keep
playing until no move is left, and the final score decides victory or defeat. Games saved under the
earlier target-tile rules (256/512/1024/2048, won the moment the tile appears) keep playing by those
rules. Deterministic spawning and all other rules are identical in both. It has independent
save/restore, onboarding, results/economy, and Profile statistics, and remains outside Daily.

Requires JDK 17 and Android SDK Platform 36.

Crowns, the second puzzle type, now has a pure-Kotlin domain model, deterministic solving and generation, solve-based difficulty evaluation, and unique/multiple-solution detection in `puzzle-core/`.

Crowns also has a pure-Kotlin gameplay/session layer with committed crowns and blocked marks, pencil notes, a three-mistake attempt limit, structured conflicts, completion tracking, and logic-based hints. Its complete Android experience is available from the Game tab's catalog with selectable difficulty, independent save/restore, explainable hints, and a short replayable interactive onboarding, and it is also part of the Daily challenge.

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

The application has three primary sections, reached from the bottom navigation:

- **Игра** — the Game hub: the Daily challenge as a horizontal row of puzzle cards on top, and the
  regular puzzle catalog as the vertical list below it.
- **Магазин** — the RuStore gem store: the gem balance and the three gem packs at RuStore's prices.
- **Профиль** — the local gameplay profile: a summary of your statistics and the detailed per-puzzle
  breakdown below it. There is no account and nothing leaves the device.

The wallet and the Settings gear sit in the same place on all three, and Settings, the start screens,
the tutorials, and gameplay open on top of them with normal Back navigation.

Modules:

- `app/` — Android/Compose application shell, navigation, theme, and settings.
- `puzzle-core/` — deterministic Balance, Crowns, Word, Sudoku, and 2048 domain/gameplay code and diagnostics.
- `lexicon/word/` — curated offline Word corpus sources and their provenance note.

Balance is playable from the Game tab's catalog with optional interactive onboarding, selectable difficulties, conflicts, hints, and improved accessibility cues.

Balance and Crowns use explicit input rather than cycling taps: you pick the value to place (● or ○ in Balance, a crown or a × mark in Crowns) and tap a cell. A correct value is confirmed and fixed for the rest of the game; a wrong one stays on the board, marked as an error, until you replace it or tap it again with the same value to remove it — the app never shows you what the right value was. The separate Pencil toggle writes small unchecked notes in a cell's upper-right corner; committing a value clears that cell's notes. There is no Undo, no eraser, and no reset.

Each wrong value you commit costs one mistake, and the third one ends the attempt: the board freezes with your answers still visible and you can replay the very same puzzle from scratch. Pencil notes are never checked and never cost a mistake. Word keeps its six attempts and can be replayed the same way. Every finished attempt is recorded whether it was solved or failed, but a Daily puzzle only counts as done once you actually solve it — a failed Daily attempt leaves the entry open for another try and never advances progress or the streak. The Game tab provides a deterministic, resumable Daily challenge that contains Balance, Crowns, and Word (Policy V4, with Word Generator V2): each puzzle is started, resumed, and completed independently in any order, aggregate progress is shown as `0 / 3` … `3 / 3`, and unfinished Catalog progress stays separate. A puzzle that ends in failure leaves its Daily entry open, so the run reaches 3/3 only once all three are solved. Earlier Daily runs keep their immutable V1, V2, or V3 definitions.

Word (Слово) is the third playable puzzle: EASY uses four letters, MEDIUM five, HARD six, and
EXPERT seven, with six valid attempts at every difficulty. It is available from the Game tab's catalog with an
on-screen Russian keyboard, accessible non-color feedback, independent version-aware save/restore,
and a short onboarding. Like the other puzzles a Word game is terminal on both outcomes — solved, or failed after six
attempts — and both are recorded.

Completed games are persisted as durable results with a typed outcome and, for Word, the attempts used. The app reports core gameplay statistics and derives current and best Daily streaks from completed Daily history. Once a Daily run is fully completed, the Game tab offers a spoiler-free plain-text share of that run's per-puzzle results (each puzzle's solved result and, for Word, the attempts it took, never the Word answer), progress, and current streak through the standard Android Sharesheet.

The app has an offline economy of gems and lives. You start with five lives and no gems: a solved
attempt earns gems according to its difficulty — 1 for Легко, 2 for Средне, 3 for Сложно, 4 for
Эксперт — every failed attempt costs one life whatever the difficulty, and a missing life comes back
on its own after 15 minutes (the countdown keeps running while the app is closed and never restarts
when you lose another life). With no lives left you can still open and look at a saved puzzle, but
playing, starting, and replaying wait until a life is available; ten gems restore one life immediately.
Tutorials never touch gems or lives, and everything is stored locally — there is no account and no
server.

Gems can also be bought, entirely optionally, through RuStore: tapping the gem balance opens the
Store tab with three packs (50, 250, and 600 gems) at RuStore's own prices. Nothing else is for sale —
no lives, no hints, no subscriptions, no removing ads — and every puzzle stays fully playable without
ever opening it.

Only when you are completely out of lives, the lives dialog adds one more optional way back in: watch
a rewarded ad and get one life. It is never forced and never shown anywhere else — no banners, no
interstitials, nothing after a solved or failed game — and if there is no network or no ad to show,
the 15-minute timer and the ten-gem refill work exactly as before.

All five Catalog puzzles share one calm Material 3 shell: a single Light/Dark colour scheme, one spacing and text hierarchy, one difficulty selector, and one loading/error/completion presentation, while each board keeps its own specialised gameplay. Puzzle states are always readable without colour, and the shared UI pieces live in `app/src/main/java/com/stanisryz/logica/ui/components/` and `.../ui/theme/`.

Dependency versions are managed in `gradle/libs.versions.toml`.
