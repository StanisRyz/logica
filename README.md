# Логика дня

Logica contains the native Android application with playable Balance, Crowns, Word, Sudoku, and 2048 puzzles plus an initial playable Yandex Games Web slice. The deterministic core and canonical puzzle data are shared through `:puzzle-core`, the focused Compose Multiplatform presentation pilot lives in `:shared-ui`, and neutral Store, Ads, Player, Cloud Save, lifecycle, and capability contracts live in `:platform-contracts`. Android remains the complete application; Web currently shares the theme, difficulty selector, and Balance gameplay presentation only.

Every catalog game is played as a numbered sequence of fixed levels. Level 1 of Легко is the same
puzzle for everyone, and so is level 743 — each game and each difficulty keeps its own level number,
shown above the board. Solving a level moves you to the next one; a
failed attempt lets you replay the same level. Unfinished levels are not saved: leaving one asks
for confirmation and then discards that attempt, and opening the game again starts the same level
from the beginning.

Opening a Catalog game shows one shared entry screen: a short «Как играть?» tutorial action followed
by four direct level cards — Легко, Средне, Сложно, and Эксперт.

Sudoku uses a frozen, validated Puzzle Bank corpus bundled offline; puzzles are never generated or
downloaded at runtime. It is the fourth Catalog game, with EASY, MEDIUM, HARD, and EXPERT selection,
onboarding, economy/results, and Profile statistics, and it is also part of the Daily challenge.

2048 is the fifth Catalog game. Легко, Средне, Сложно, and Эксперт ask for 12 000, 30 000, 100 000,
and 250 000 points. Reaching the goal clears the level right away — the reward and the next level
are yours immediately — and the board keeps running, so you can play on for a higher score as long
as you like. Running out of moves after that is not a defeat and costs no life; running out before
the goal is. It has animated tile movement, merges and deterministic spawning, onboarding,
results/economy, and Profile statistics, and it is also part of the Daily challenge.

Requires JDK 17 and Android SDK Platform 36.

The `:web-app` module builds Kotlin/JS and Kotlin/Wasm browser executables. Local runs use a clearly
labelled standalone mode when the Yandex SDK is absent; the compatibility distribution selects Wasm
where supported and falls back to JavaScript. Build the Web outputs and the uploadable Yandex ZIP with:

```powershell
.\gradlew.bat :web-app:jsBrowserDevelopmentRun
.\gradlew.bat :web-app:wasmJsBrowserDevelopmentRun
.\gradlew.bat :web-app:composeCompatibilityBrowserDistribution
.\gradlew.bat :web-app:packageYandexDistribution
```

The ZIP is written to `web-app/build/distributions/logica-yandex.zip`. The current Web host includes
SDK bootstrap, lifecycle/gameplay activity handling, lazy frozen Level Pack loading, responsive 9:16
presentation, and a playable Balance Catalog Level 1 flow for all four difficulties. It does not yet
include durable progression, the other four games, Player, Payments, ads, Store, Profile, Daily, or a
sticky banner.

Crowns, the second puzzle type, now has a pure-Kotlin domain model, deterministic solving and generation, solve-based difficulty evaluation, and unique/multiple-solution detection in `puzzle-core/`.

Crowns also has a pure-Kotlin gameplay layer with committed crowns and blocked marks, pencil notes, a three-mistake attempt limit, structured conflicts, completion tracking, and logic-based hints. Its complete Android experience is available from the Game tab's catalog with selectable difficulty, explainable hints, and a short replayable interactive onboarding, and it is also part of the Daily challenge.

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

The frozen Catalog levels are built offline and committed as compact assets:

```powershell
.\gradlew.bat :puzzle-core:buildCatalogLevelPacks
.\gradlew.bat :puzzle-core:buildCatalogLevelPacks -PlevelPackGames=balance,crowns
```

It reuses the shipped generators, solvers, dataset, and lexicon to regenerate candidate buckets for
the 10 000 accepted puzzles per game and difficulty. Level Pack V1 is frozen: matching candidates
verify successfully, changed content requires a new pack version, and past level 10 000 the content
cycles while the level number keeps counting up. The single canonical corpus lives under
`puzzle-data/levels/v1/`; Android packages it under the unchanged `levels/v1/` runtime asset path.

`wordLexiconPrepare` regenerates `puzzle-core/src/commonMain/resources/word/v1/` from the offline curated
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

- `app/` — Android/Compose shell plus neutral platform-service contracts and Android adapters for Store, ads, identity, cloud capability, and lifecycle.
- `puzzle-core/` — Kotlin Multiplatform deterministic Balance, Crowns, Word, Sudoku, and 2048 runtime in `commonMain`, with Android/JVM/JS/Wasm library targets and JVM-only developer tooling.
- `puzzle-data/` — the canonical frozen Catalog Level Pack and Sudoku Dataset V1 corpus shared by Android and future Web builds.
- `lexicon/word/` — curated offline Word corpus sources and their provenance note.

Balance is playable from the Game tab's catalog with optional interactive onboarding, selectable difficulties and levels, conflicts, hints, and improved accessibility cues.

Balance and Crowns use explicit input rather than cycling taps: you pick the value to place (● or ○ in Balance, a crown or a × mark in Crowns) and tap a cell. A correct value is confirmed and fixed for the rest of the game; a wrong one stays on the board, marked as an error, until you replace it or tap it again with the same value to remove it — the app never shows you what the right value was. The separate Pencil toggle writes small unchecked notes in a cell's upper-right corner; committing a value clears that cell's notes. There is no Undo, no eraser, and no reset.

Each wrong value you commit costs one mistake, and the third one ends the attempt: the board freezes with your answers still visible and you can replay the very same puzzle from scratch. Pencil notes are never checked and never cost a mistake. Word keeps its six attempts and can be replayed the same way. Every finished attempt is recorded whether it was solved or failed, but a Daily puzzle only counts as done once you actually solve it — a failed Daily attempt leaves the entry open for another try and never advances progress or the streak. The Game tab provides a deterministic Daily challenge that contains all five games — Balance, Crowns, Word, Sudoku, and 2048, every one of them at Medium (Policy V5): each puzzle is started and completed independently in any order, aggregate progress is shown as `0 / 5` … `5 / 5`, and an unfinished Daily attempt is simply started again. Solving any one of the five keeps your streak for that day; solving all five completes the Daily itself. A puzzle that ends in failure leaves its Daily entry open for another try. Daily runs created earlier keep their immutable V1–V4 definitions and their original streak rule.

Word (Слово) is the third playable puzzle: EASY uses four letters, MEDIUM five, HARD six, and
EXPERT seven, with six valid attempts at every difficulty. It is available from the Game tab's catalog with an
on-screen Russian keyboard, position-by-position editing of the current attempt, accessible non-color feedback,
short interaction animations, and a short onboarding. Like the other puzzles a Word game is terminal on both outcomes — solved, or failed after six
attempts — and both are recorded.

Completed games are persisted as durable results with a typed outcome and, for Word, the attempts used. The app reports core gameplay statistics and derives current and best Daily streaks from durable Daily history — never from a stored counter. Once a Daily run is fully completed, the Game tab offers a spoiler-free plain-text share of that run's five per-puzzle results (each puzzle's solved result and, for Word, the attempts it took, never the Word answer, the Sudoku board, or the 2048 seed), progress, and current streak through the standard Android Sharesheet. A day that only kept the streak is not shareable.

The app has an offline economy of gems and lives. You start with five lives and no gems: a solved
attempt earns gems according to its difficulty — 1 for Легко, 2 for Средне, 3 for Сложно, 4 for
Эксперт — every failed attempt costs one life whatever the difficulty, and a missing life comes back
on its own after 15 minutes (the countdown keeps running while the app is closed and never restarts
when you lose another life). With no lives left, playing, starting, and replaying wait until a life
is available; ten gems restore one life immediately. A level you already finished stays finished.
Tutorials never touch gems or lives, and everything is stored locally — there is no account and no
server.

Gems can also be bought, entirely optionally, through RuStore: tapping the gem balance opens the
Store tab with three packs (50, 250, and 600 gems) at RuStore's own prices. Nothing else is for sale —
no lives, no hints, no subscriptions, no removing ads — and every puzzle stays fully playable without
ever opening it.

Purchases need the RuStore Console application ID in private build configuration: set
`logica.rustoreConsoleAppId` in `local.properties`, in a private `gradle.properties`, or as
`ORG_GRADLE_PROJECT_logica.rustoreConsoleAppId`. Leaving it unset is a supported build — it is warned
about at configuration time, no RuStore call is ever made, and the Store simply opens with the gem
balance and an unavailable notice. Nothing else in the application changes.

Only when you are completely out of lives, the lives dialog adds one more optional way back in: watch
a rewarded ad and get one life. It is never forced and never shown anywhere else, and if there is no
network or no ad to show, the 15-minute timer and the ten-gem refill work exactly as before.

Separately from that, a fullscreen ad may appear after a game attempt is finished — whether you
solved it or not — with at least five minutes between two of them. It never appears at startup, in
the Game hub, in the Store, in Profile, in Settings, in a tutorial, or while you are playing, it
never affects gems, lives, your result, the Daily challenge, or your statistics, and when it closes
you are back on exactly the same result screen. If there is no ad ready, nothing waits and nothing
changes. There are no banners.

All five Catalog puzzles share one calm Material 3 shell: a single Light/Dark colour scheme, one spacing and text hierarchy, one difficulty selector, and one loading/error/completion presentation, while each board keeps its own specialised gameplay. Puzzle states are always readable without colour, and the shared UI pieces live in `app/src/main/java/com/stanisryz/logica/ui/components/` and `.../ui/theme/`.

Dependency versions are managed in `gradle/libs.versions.toml`.
