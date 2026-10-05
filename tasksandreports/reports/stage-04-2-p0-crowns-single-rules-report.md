# Отчёт: этап 4.2 — P0 Crowns, единые правила экономики и выхода, понятные ошибки

## 1. SHA коммитов

| Пункт | SHA | Коммит |
|---|---|---|
| 0. P0 Crowns и проверка dex | `cc0ded5` | P0: Crowns no longer calls Java 21 List.removeLast on Android 8-14 |
| 1. Числа экономики | `dbada1e` | Economy numbers come from EconomyPolicy on both platforms |
| 2. Правило прогресса в ядре | `d83795b` | The "leaving costs a life" progress rule lives in the core |
| 3. Локализованные ошибки | `ecb9725` | Web: players see a localized reason instead of exception text |
| 4. `AppLog` | `3fde1be` | AppLog: a warning trace for failures that used to vanish silently |

Отчёт идёт отдельным коммитом. Фразы в `AGENTS.md` — в коммитах своих пунктов. П. 0 запушен первым и отдельно, его CI прошёл до остальных пунктов.

## 2. Что сделано по пунктам

### 0. P0: Crowns на Android 8–14

1. **Исправление.** `CrownsGeneratorV1.kt:108`: `crowns.removeLast()` → `crowns.removeAt(crowns.lastIndex)`. Генерация та же: golden-тесты на JVM, JS и Wasm и `FrozenCatalogLevelPackTest` не менялись и проходят.
2. **Исключение снято.** Временное исключение `FrozenCatalogLevelPackTest` убрано из `app/build.gradle.kts`.
   - **Проверка на JDK 17 локально.** Gradle запущен с `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`. С исправлением тест проходит (4/4). С временно возвращённым `removeLast()` падает с `NoSuchMethodError: 'java.lang.Object java.util.List.removeLast()'`.
   - **В CI на JDK 17** тест тоже проходит.
3. **Аудит по dex, а не по исходникам.** Свежий release APK без RuStore я разобрал `dexdump` и собрал все вызовы `removeFirst`/`removeLast`/`getFirst`/`getLast`/`addFirst`/`addLast`/`reversed` с их классом-владельцем:

| Владелец в dex | Вызовы | Безопасно |
|---|---|---|
| `java.util.ArrayDeque` | `addFirst` 2, `addLast` 5, `getFirst` 2, `getLast` 3, `removeFirst` 13 | да, с API 9 |
| `java.util.LinkedList` | `addFirst` 1 | да, с API 1 |
| `xf` (по `mapping.txt` это Kotlin `kotlin.collections.ArrayDeque`) | — | да, своя реализация |
| `java.util.List`, `ArrayList`, `Collection` | ни одного | — |

   - `.reversed()` в `Game2048Rules.kt:175`, `NonogramBoard.kt:247,263` и `Game2048Content.kt:292,295` компилируется в stdlib-функцию `CollectionsKt.reversed`, а не в Java 21 `List.reversed()`. В dex нет ни одного `List;.reversed`.
   - `NonogramLineSolver` (`stack`) и `CrownsRegionConnectivity` (`frontier`) работают на `ArrayDeque`.
   - Больше исправлять нечего.
4. **Профилактика в CI.** Скрипт `.github/scripts/check_dex_api.py`:
   - разбирает каждый `classes*.dex` из APK через `dexdump -d`;
   - падает, если есть `invoke-*` любого из перечисленных методов на `java.util.List`, `ArrayList`, `Collection`, `AbstractList`, `AbstractCollection`, `SequencedCollection`, `Vector`, `Stack`, `CopyOnWriteArrayList` или `Collections$*`;
   - печатает метод-нарушитель, по `mapping.txt` его можно перевести в исходное имя.

   Job `android` теперь собирает ещё и `:app:assembleRelease` и запускает этот шаг.

   **Доказательство, что проверка ловит исходную ошибку.** На APK этапа 3, собранном до исправления, скрипт падает:

   ```
   Java 21 List methods called (missing on Android API < 35):
   classes.dex: w50.d:(Ljava/util/ArrayList;)V: 0000: invoke-virtual {v0}, Ljava/util/ArrayList;.removeLast:()Ljava/lang/Object;
   ```

   `w50` — это `CrownsGeneratorV1$$ExternalSyntheticApiModelOutline0`. На APK с исправлением: `no Java 21 List calls in 1 dex file(s)`.

   **Почему не lint.** `NewApi` для Android-вариантов KMP-библиотек пришлось бы настраивать и проверять в каждом модуле отдельно. Проверка dex смотрит ровно на то, что исполнит устройство: после R8 и всех модулей, включая чужие библиотеки, и не зависит от того, где написан вызов.
5. **`AGENTS.md`**: фраза в абзаце о `:puzzle-core` — не использовать эти методы на списках, вместо них `removeAt(lastIndex)`, `first()`, `last()`, `asReversed()`, и CI это проверяет.

### 1. Числа экономики — из одного источника

**Новые константы в `EconomyPolicy`** (`platform-contracts/.../Economy.kt`):
- `LIFE_REFILL_GEM_COST` = 10;
- `HINT_SINGLE_GEM_COST` = 4, `HINT_PACK_SIZE` = 3, `HINT_PACK_GEM_COST` = 10;
- `REWARDED_AD_GEMS` = 1, `REWARDED_AD_LIVES` = 1;
- `GEM_PACK_SMALL`/`MEDIUM`/`LARGE` = 50/150/500;
- `STARTER_PACK_GEMS` = 100, `STARTER_PACK_HINTS` = 5.

Стартовые гемы, жизни и подсказки, максимум жизней, штраф за провал и интервал восстановления там уже были.

**Кто теперь читает политику:**
- **Android.** `EconomyRules` остался набором Android-имён, но каждая константа — это ссылка на `EconomyPolicy`. Так обошлось без правок в десятке мест, где используются его имена. `LIFE_REGENERATION_INTERVAL` строится из `LIFE_RESTORE_INTERVAL_MS`. `GemPack` берёт 50/150/500 и стартовый набор из политики, `HintOffer` по-прежнему ссылается на `EconomyRules.HINT_*`.
- **Web.** `WebStoreCatalog` (цены и размер набора подсказок, цена жизни), `WebRewardedPlacementController.GEM_REWARD`/`LIFE_REWARD` и `WebPaidProduct` (паки и стартовый набор) читают политику.
- **Стартовый набор.** `StarterPackContents` в `:shared-ui` стал представлением над политикой (`GEMS = EconomyPolicy.STARTER_PACK_GEMS` и т. д.). Для этого у `:shared-ui` появилась зависимость `implementation(project(":platform-contracts"))`. Это модуль проекта, а не новая библиотека; цели у модулей одинаковые: android, js, wasm. Теперь карточка показывает ровно то, что выдают Android `GemPack.STARTER_PACK` и Web `WebPaidProduct.STARTER_PACK`.

**Тесты паритета:**
- `EconomyParityTest` (`:app`) фиксирует сегодняшние числа политики литералами, чтобы случайная правка числа упала. Ещё он проверяет, что `EconomyRules`, `HintOffer`, `GemPack.CATALOG` и стартовый набор равны политике, а карточка — покупке.
- `WebEconomyParityTest` (web) проверяет то же для `WebStoreCatalog`, наград за рекламу, `WebPaidProduct` и стартового набора.

Ни одно число не изменилось.

### 2. Правило «реального прогресса» в ядре

**Где теперь правило.** У каждой игры `hasMeaningfulProgress` лежит в `:puzzle-core` рядом с её состоянием:

| Игра | Правило | Где |
|---|---|---|
| Balance, Crowns, Sudoku, Word | extension-свойства на состоянии | `BalanceGameState.kt`, `CrownsGameState.kt`, `SudokuGameState.kt`, `WordGameState.kt` |
| 2048 | `Game2048State.hasMeaningfulProgress(levelCleared, completionSaved)`; сравнение идёт с `Game2048Engine.INITIAL_SPAWN_COUNT`, константа стала `internal` | `Game2048Models.kt` |
| Block Sudoku | `hasMeaningfulProgress` вместо прежнего `hasProgress`, теперь с проверкой «игра не окончена» | `BlockSudoku.kt` |
| Nonogram | `hasMeaningfulProgress(initial)` сама проверяет, что игра не окончена; раньше это делали хосты | `NonogramGame.kt` |

**Хосты вызывают только ядро:**
- **Android.** ViewModel'и семи игр: открытое предложение второго шанса (`continueOffered`) остаётся состоянием хоста и добавляется через `||`, как и раньше.
- **Web.** Копии в `WebLeaveLevelGuard.kt` удалены. `WebApp.kt` импортирует правила ядра, `WebNonogramController` и `WebBlockSudokuController` вызывают их же. `secondChanceOffered` добавляется на стороне хоста, как раньше.

**Расхождений между Android и Web не нашлось.** Все пять «зеркал» были побуквенно одинаковы. У 2048 «уровень пройден» на обеих платформах означает Catalog и цель достигнута (Android `levelCleared` выставляется только для уровня Catalog, Web передаёт `!isDaily && goalReached`). Поведение не изменилось.

**Тесты.** `MeaningfulProgressTest` в `commonTest` идёт на JVM, JS и Wasm. Для каждой из семи игр: свежая попытка → `false`, первое реальное действие → `true`. Действия такие: значение в Balance, отметка в Crowns, цифра в Sudoku, буква в Word, ход в Nonogram, фигура в Block Sudoku.

**2048.** Один допустимый свайп уже считается прогрессом: каждый допустимый ход добавляет спавн сверх двух начальных, и так было на обеих платформах. Тест это фиксирует. Ещё он проверяет, что пройденный уровень защищён только до сохранения результата.

### 3. Понятные ошибки вместо текста исключения

- **Тип причины.** `WebLoadFailure` (`web-app/.../WebLoadFailure.kt`): `DATA_LOAD`, `DATA_CORRUPT`, `PROGRESS_UNAVAILABLE`, `UNKNOWN`, у каждой свой `StringResource`.
  - Состояния `Error` семи контроллеров (Balance, Crowns, Word, Sudoku, 2048, Nonogram, Block Sudoku) хранят `failure: WebLoadFailure` вместо `detail: String`.
  - Ветка «прогресс недоступен» даёт `PROGRESS_UNAVAILABLE`.
  - Остальное классифицирует `Throwable.toWebLoadFailure()`.
- **Сбой загрузки отличим от повреждённых данных.** `BrowserPuzzleDataLoader` при отказе `fetch` или HTTP-ошибке бросает новый `WebPuzzleDataLoadException`, это `DATA_LOAD`. Сломанный `check`/`require` на загруженном содержимом (битый пак, несовпадение версии генератора) — `DATA_CORRUPT`, всё прочее — `UNKNOWN`.
- **Экран ошибки уровня** (`WebCatalogLevelErrorContent`) показывает `stringResource(failure.message)`.
- **`FatalContent`** показывает локализованный `web_fatal_message`. `WebBootstrapState.FatalError` стал `data object`, причина уходит в `AppLog`.
- **Строки сохранения тоже чистые.** `web_save_error_catalog`/`daily`/`generic` больше не дописывают технический текст (`%1$s` убран), его деталь уходит в `AppLog`. Это тот же класс проблемы, игрок видел его в окне результата.
- **Новые строки** на русском, английском и турецком: четыре причины и `web_fatal_message`.
- **Тест** `WebLoadFailureTest`: сопоставление исключений с причинами.

### 4. `AppLog`

**Шов** — `AppLog.warn(tag, message, throwable?)` в `:platform-contracts`, с заменяемым `Sink`:
- **Android.** `LogicaApplication.onCreate` ставит `Log.w`.
- **Web.** `main()` ставит `console.warn`, сообщение со стеком исключения.
- **Тесты** оставляют приёмник по умолчанию, он ничего не делает. JVM-тесты Android не трогают `android.util.Log`.

**Где используется** — только там, где сбой раньше проходил бесследно:

| Платформа | Место | Что логируется |
|---|---|---|
| Android | `DailyRewardsViewModel` | отказ получения подарка, задания, награды за достижение |
| Android | `GemPurchases` | сбой финализации зачисленной покупки |
| Android | `GemStoreViewModel` | сбой сверки, загрузки каталога, покупки пака, покупки «без рекламы» |
| Web | `WebPlayerSessionController` | `bindPaymentsLocal` (раньше тихий `return`); сбой привязки контекста Player; сбои legacy-синхронизации статистики и Daily, включая неудачные `write` |
| Web | `WebUnifiedSaveScheduler` | исключение при restore и при записи |
| Web | контроллеры игр | причина плюс исключение при сбое загрузки (`toLoggedWebLoadFailure`) |
| Web | Catalog и Daily | ошибки сохранения |
| Web | `WebBootstrapController` | фатальный старт SDK |

**Без персональных данных.** id игрока, токены и id покупок, содержимое сохранений не логируются: в сообщениях только тип события, а в исключениях — пути ресурсов и технические причины. Никакой отправки и никаких зависимостей.

**Тест:** в `WebLoadFailureTest` приёмник собирает сообщения в список, и тест проверяет, что причина ушла в лог.

## 3. Новые и изменённые тесты

Новые:

| Тест | Модуль | Тестов | Что проверяет |
|---|---|---|---|
| `EconomyParityTest` | `:app` | 2 | числа политики, паритет Android |
| `WebEconomyParityTest` | web | 1 | паритет Web |
| `MeaningfulProgressTest` | `:puzzle-core` `commonTest` | 7 | по одному на игру |
| `WebLoadFailureTest` | web | 2 | причины; запись в `AppLog` |

Изменённые: существующие тесты не правились. `FrozenCatalogLevelPackTest` снова выполняется на JDK 17: снято исключение из сборки, сам тест не менялся.

Итого: `:app` — 91 JVM-тест (было 89), `:puzzle-core:jvmTest` — 101 (было 94), web — 123 (было 120).

## 4. Команды проверки и итог

| Команда | Итог |
|---|---|
| `./gradlew ktlintCheck :puzzle-core:jvmTest` | успешно, 101/101 |
| `./gradlew -Plogica.withoutRustore=true :app:testDebugUnitTest :app:assembleRelease` | 91/91. APK собран после п. 0, `check_dex_api.py`: нарушений нет; на каждом пуше то же проверяет CI |
| `bash .claude/scripts/web-tests-node.sh` | `passed=123 failed=0 skipped=0` |
| `./gradlew :web-app:compileKotlinWasmJs` | успешно |
| `FrozenCatalogLevelPackTest` с Gradle на JDK 17 | проходит; с возвращённым `removeLast()` падает, как и должно |

**CI:**

| Прогон | Коммит | Итог |
|---|---|---|
| https://github.com/StanisRyz/logica/actions/runs/37324721773 | `cc0ded5`, только п. 0 | все 5 job'ов зелёные: `FrozenCatalogLevelPackTest` прошёл на JDK 17 без исключения, шаг «Release dex has no Java 21 List calls» прошёл |
| https://github.com/StanisRyz/logica/actions/runs/37326404019 | `d83795b`, п. 1–2 | зелёный |
| https://github.com/StanisRyz/logica/actions/runs/37327490622 | `3fde1be`, все пункты | **все 5 job'ов зелёные**, включая проверку dex |

Job `android` с добавленной сборкой release и проверкой dex теперь идёт около 7 минут. Весь прогон — около 7,5 минуты.

## 5. Отклонения, найденные проблемы, вопросы

1. **Проверка dex вместо lint `NewApi`.** Причины — в разделе 2, п. 0.4. Проверку я доказал на APK до исправления.
2. **Новая зависимость модулей `:shared-ui` → `:platform-contracts`.** Она нужна, чтобы `StarterPackContents` был представлением над `EconomyPolicy`, как просило задание. Внешних библиотек не добавилось.
3. **`EconomyRules` оставлен, а не удалён.** Он только ссылается на `EconomyPolicy`. Удаление затронуло бы десятки мест в `:app` ради переименования.
4. **Сохранение по-прежнему показывает, что прогресс не сохранён, но без технической детали.** Деталь пишется в `AppLog`. Раньше она шла в текст окна результата.
5. **Восстановление жизни на Android** (`PlayerEconomy.withLifeRestored`) по смыслу добавляет одну жизнь и для обмена на гемы, и для рекламы. `REWARDED_AD_LIVES` в политике читает Web. На Android «одна жизнь» осталась свойством самой операции, а не константой: число то же, и я не стал менять логику кошелька.
