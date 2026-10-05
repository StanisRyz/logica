# Отчёт: этап 3 — Android: готовность к релизу

## 1. SHA коммитов

| Пункт | SHA | Коммит |
|---|---|---|
| 0. Сборка без RuStore | `ac71986` | Android: build :app without the RuStore SDK on request |
| 1. R8 и сжатие ресурсов | `8feb174` | Android: shrink release builds with R8 |
| 2. Резервное копирование | `a995924` | Android: spell out what backup and device transfer carry |
| 3. Иконка и скрипт | `d9c5456` | Android, Web: launcher icon and favicons from one source picture |
| 3. Поправка по lint | `ae0bd8c` | Android: adaptive icon in mipmap-anydpi, as minSdk 26 needs no -v26 |
| 4. Окно и сплэш | `6c64093` | Android: light/dark launch window and the system splash |
| 5. Статус-бар | `4958b11` | Android: system bar icons follow the in-app theme |
| 6. `CancellationException` | `13722ff` | Android: stop swallowing coroutine cancellation in runCatching |
| 7. Награды и перевод часов | `27808ed` | Android: daily rewards cannot be farmed by turning the clock back |

Отчёт идёт отдельным коммитом следом. Фразы в `AGENTS.md` добавлены в коммит каждого пункта.

## 2. Что сделано по пунктам

### 0. Сборка `:app` без RuStore

**Свойство.** `logica.withoutRustore=true` читается как Gradle-свойство (`-P`, пользовательский `gradle.properties`) или из `local.properties`, тем же `privateSetting`, что и остальные `logica.*`. При нём:
- зависимости `rustore-bom` и `rustore-pay` не подключаются;
- вместо `src/rustore/java` подключается `src/noRustore/java`;
- для тестов добавляется `src/testNoRustore/java`.

Каталоги подключаются через `sourceSets.main.kotlin.srcDir`. В AGP 9 со встроенным Kotlin `java.srcDir` для `.kt` не срабатывает.

**Раскладка исходников.**
- В `main` (`store/RuStorePayGateway.kt`) остались общие типы, которые не зависят от SDK: `RuStoreProduct`, `RuStorePurchase`, `RuStorePurchaseResult`, интерфейс `RuStorePayGateway`, `StoreUnavailableException`, `UnconfiguredRuStorePayGateway`.
- В `src/rustore/.../RuStoreGemPayGateway.kt` перенесён без изменений весь код с SDK: `createRuStorePayGateway`, `RuStoreGemPayGateway`, `ruStoreSdkTheme`, `proceedRuStorePayIntent` и `Task.await`.
- В `src/noRustore/.../RuStoreGemPayGateway.kt` лежит заглушка с теми же именами:
  - `createRuStorePayGateway` всегда возвращает `UnconfiguredRuStorePayGateway`;
  - `ruStoreSdkTheme()` возвращает `Unit`, а параметр темы имеет тип `() -> Unit`, так что фиктивный тип `SdkTheme` не понадобился;
  - `proceedRuStorePayIntent` ничего не делает.

  Остальной код (`AndroidPlatformComposition`, тесты) компилируется без правок.

**Сборка по умолчанию не меняется.** Без свойства собирается с настоящим RuStore, как раньше. Проверить это здесь нельзя: репозиторий RuStore отвечает 404. Файл в `src/rustore` — это перенос без правок.

**Облачная среда.** В `.claude/hooks/session-start.sh` добавлена строка `logica.withoutRustore=true` в `.gradle-user-home/gradle.properties`, который хук пишет только в облаке (`CLAUDE_CODE_REMOTE=true`). Локальную сборку это не затрагивает: локально хук сразу выходит. Сейчас я запускал всё с явным `-Plogica.withoutRustore=true`.

### 1. R8 и сжатие ресурсов

- **Сжатие включено.** В release `isMinifyEnabled = true`, `isShrinkResources = true`.
- **Правила.** В `proguard-rules.pro` правил нет, только комментарий о том, почему они не нужны:
  - **рефлексии нет**: фабрики ViewModel вызывают конструкторы напрямую;
  - **Navigation 3**: back stack — это `remember { mutableStateListOf }`, ключи не сериализуются;
  - **kotlinx-serialization не используется**;
  - **`BuildConfig`** читается как константы;
  - **enum** восстанавливаются по `name`, а R8 сохраняет строковые имена;
  - **словари Word** читаются по абсолютному пути ресурса (`/word/v1/...`).
- **Проверка по `mapping.txt`.** Consumer-правила библиотек работают:
  - `LogicaDatabase_Impl` сохранил имя, это нужно Room;
  - у `PreferencesProto$PreferenceMap` сохранены поля `preferences_`, `DEFAULT_INSTANCE`, `PARSER`, это нужно protobuf-lite в DataStore;
  - в APK на месте `word/v1|v2/*.txt`, `assets/composeResources/...`, `assets/levels`, `assets/sudoku`;
  - R8 не сообщил о недостающих классах.
- **Размер release APK** (без RuStore, unsigned):

| Сборка | Размер |
|---|---|
| До, `isMinifyEnabled = false` | 31 797 966 байт (≈30,3 МиБ) |
| После R8 | 17 731 333 байта (≈16,9 МиБ) |
| Итог этапа, с иконками | 17 914 362 байта |

  Основной вес теперь — данные головоломок (`sudoku/v1/*.sdk` по 1,16 МБ), `libsqliteJni.so` для четырёх ABI и `classes.dex` (8,75 МБ).

### 2. Резервное копирование

- **Манифест.** `allowBackup="true"` оставлен. Добавлены `android:dataExtractionRules="@xml/data_extraction_rules"` (API 31+) и `android:fullBackupContent="@xml/backup_rules"`, а также `tools:targetApi="31"`.
- **Что включается.** Правила включают ровно:
  - `database/logica.db` и `logica.db-wal`. WAL нужен, потому что в нём могут лежать последние коммиты, если процесс убит без checkpoint;
  - `file/datastore/`, где лежат все `*.preferences_pb` DataStore: `user_settings`, `advertising`, `records` и новый `economy_clock`.
- **Где лежат файлы.** Room: `getDatabasePath("logica.db")`, это домен `database`. DataStore: `filesDir/datastore/`, это домен `file`.
- **Что не попадает.** Включаются только перечисленные файлы, поэтому в копию не попадают:
  - кэш звуков (`cacheDir/sounds`; кэш и так не копируется);
  - данные SDK рекламы и платежей (AppMetrica, shared_prefs SDK).
- **Device transfer** использует тот же набор.

### 3. Иконка приложения

- **Скрипт `tools/art/make_app_icon.py`** (Pillow + NumPy). Исходник — `art/app_icon/source.png`. Скрипт делает:
  - передний слой для mdpi…xxxhdpi (108 dp: 108…432 px). Масштаб подобран так, чтобы самый дальний непрозрачный пиксель касался безопасного круга 66 dp: это точнее, чем вписывать в квадрат 66 dp, ведь маски лаунчеров круглые;
  - монохромный слой — белый силуэт по альфе исходника;
  - `favicon-32.png` и `favicon-192.png` в `web-app/src/webMain/resources/`, рядом с `index.html`. Они попадают в корень дистрибутива: я проверил это по `processedResources`.
- **Временный исходник** — кристалл из `art/icons/sheet.png`, вырезан в исходном размере (286 px) на прозрачном квадрате 302 px. Для xxxhdpi нужно около 264 px, так что качества хватает. Своя картинка владельца: заменить `source.png` (лучше 1024 px) и запустить скрипт. Скрипт подсказывает, если исходник мелковат.
- **Фон адаптивной иконки** — цвет `ic_launcher_background` = `#F7F3EA`, светлый фон темы. Синий кристалл на нём читается хорошо, превью я проверил с круглой маской.
- **Ресурсы и манифест.**
  - `mipmap-anydpi/ic_launcher.xml` и `ic_launcher_round.xml` с background, foreground и monochrome.
  - В манифесте `android:icon` и `android:roundIcon`.
  - В `index.html` — `<link rel="icon">` на оба размера по относительным путям, потому что Яндекс отдаёт игру из вложенной папки.

### 4. Окно запуска и сплэш

- **`values/themes.xml`**: `android:Theme.Material.Light.NoActionBar` с `windowBackground` = `@color/window_background`. Зависимостей AppCompat или Material Components у `:app` нет, поэтому используются платформенные темы.
- **`values-night/themes.xml`**: `android:Theme.Material.NoActionBar` с тем же атрибутом.
- **Цвета.** `values/colors.xml` задаёт `#F7F3EA`, `values-night/colors.xml` — `#171A17`, как в `LogicaTheme`.
- **Android 12+.** В `values-v31` и `values-night-v31` добавлены `windowSplashScreenBackground` (тот же цвет) и `windowSplashScreenAnimatedIcon` = `@mipmap/ic_launcher_foreground`. Это системный сплэш, без `core-splashscreen`.
- **Проверка.** По `aapt2 dump resources` в APK есть `window_background` (обычный и night) и стили `v31`/`night-v31` с атрибутами сплэша.
- **Принятое ограничение.** Сплэш и окно следуют системной теме, а не выбранной в приложении.

### 5. Цвет иконок статус-бара

- **Хелпер для темы.** В `ui/theme/Theme.kt` появился `@Composable ThemeMode.isDarkTheme()`; на нём же построен прежний `LogicaTheme(themeMode)`.
- **`LogicaApp`** вычисляет `darkTheme` один раз, вызывает `SystemBarsFollowTheme(darkTheme)` и `LogicaTheme(darkTheme = …)`.
- **`SystemBarsFollowTheme`** (в `MainActivity.kt`) — `LaunchedEffect(activity, darkTheme)`. Он вызывает `enableEdgeToEdge` с `SystemBarStyle.dark(TRANSPARENT)` или `SystemBarStyle.light(TRANSPARENT, TRANSPARENT)` для статус-бара и для навигационной панели. При minSdk 26 платформа поддерживает светлые иконки обеих панелей, поэтому прозрачный фон безопасен. Начальный `enableEdgeToEdge()` в `onCreate` оставлен для первого кадра.

### 6. `CancellationException` не глотается

- **Хелпер.** `runCatchingCancellable` в `com/stanisryz/logica/CoroutineCatching.kt` — inline-функция. Она ловит всё как `runCatching`, но пробрасывает `CancellationException`.
- **Где заменён `runCatching`.** Везде, где он стоял вокруг suspend-вызовов, а это все места из задания и остальные:
  - `DailyRewards.kt`: подарок, задания, достижения;
  - `EconomyViewModel.kt`: три места `refresh`/`refill`;
  - `GemPurchases.kt`: два `finalize`;
  - `GemStoreViewModel.kt`: `reconcile`, `catalog`, `buy`, `buyNoAds`;
  - `GameHubScreen.kt` и `LogicaNavigation.kt` ×3: `currentLevelId`/`resolve`;
  - `Game2048BestScore.kt`, `InterstitialCooldown.kt`: `dataStore.edit`;
  - `YandexAdsInitializer.kt`, `RewardedLifeReward.kt`, `AndroidGameSoundPlayer.kt`: `withContext` при распаковке звуков;
  - `RoomDailyChallengeRepository.kt` ×3.
- **Где `runCatching` остался.** Вокруг несуспендящего кода: `valueOf`, `LocalDate.parse`, вызовы SDK рекламы, `soundPool.play`, `proceedIntent`.
- **Командный цикл `RoomDailyChallengeRepository`.** При отмене он сначала отменяет `reply` ожидающего вызова (`reply.cancel(cancellation)`), потом пробрасывает отмену. Иначе вызывающий ждал бы бесконечно. Для этого у `Command` появилось общее свойство `reply`.

### 7. Награды не фармятся переводом часов назад

1. **Высшая отметка времени.** `EconomyTimeMark` хранит её в новом файле DataStore `economy_clock` (ключ `latest_economy_time_ms`): Room и миграции не затронуты.
   - Метод `record(now)` запоминает максимум и возвращает его.
   - Если DataStore сбоит, отметкой считается текущее время: сбой хранилища не блокирует награды.
   - Для тестов есть `InMemoryEconomyTimeMark`; он же стоит по умолчанию в конструкторе.
2. **Часы ушли назад больше чем на 10 минут** (`CLOCK_TURNED_BACK_TOLERANCE_MILLIS`).
   - `claimLoginGift` и `claimQuest` возвращают `false`.
   - `observe` выставляет `claimsPaused` в общем `DailyRewardsUiState`. Новое поле со значением по умолчанию `false`, поэтому Web не меняется.
   - Карточка показывает строку `rewards_clock_changed` цветом ошибки: «Время на устройстве изменилось — награды станут доступны позже», en/tr тоже есть.
   - Кнопки получения заменяются тихими метками «+N», `hasClaimable = false`.
   - `refresh()` во ViewModel теперь всегда пересобирает состояние. Это нужно, чтобы при возврате в хаб в тот же день часы проверялись заново.
3. **День подарка не меньше последнего.** `claimLoginGift(day)` отказывает, если `day` меньше дня последнего подарка. В общем `dailyRewardsUiState` теперь `giftClaimed = last >= epochDay` вместо `==`, чтобы такой день не показывал кнопку. Web передаёт `min(last, today)`, так что его поведение не меняется.
4. **Серия по дню подарка.** Оба запроса последнего подарка в `EconomyDao` сортируют по `CAST(substr(event_id, 12) AS INTEGER)`, то есть по дню из `login_gift:<day>`, а не по `created_at`. Схема не меняется, меняется только текст `@Query`.
5. **Жизни** не трогал: при движении часов назад восстановление и так ничего не даёт.

**Принятый риск.** Перевод часов вперёд без сервера не закрыть:
- можно получить подарок и задания «будущего» дня и раньше времени восстановить жизни;
- после возврата часов награды встают на паузу, пока реальное время не догонит отметку, а подарок за дни раньше «будущего» больше не выдаётся.

Игрок, который уже так делал до этого этапа, тоже не получит подарок, пока реальная дата не дойдёт до дня его последнего «будущего» подарка. Это ожидаемо.

## 3. Новые и изменённые тесты

Новые (JVM unit, `:app`):

- **`CoroutineCatchingTest`**:
  - `anOrdinaryFailureIsStillAResult` — обычная ошибка по-прежнему попадает в `Result`;
  - `cancellationIsRethrownSoTheCoroutineReallyStops` — после отмены корутина не продолжает работу и не получает `Result.failure`.
- **`DailyRewardsTest`**:
  - `aClockTurnedBackMoreThanTenMinutesPausesEveryClaim` — назад на 9 минут ничего не меняется; на 11 минут — пауза, подарок и задание не выдаются; когда время догоняет, оба выдаются;
  - `theNormalNextDayStillPays`;
  - `noGiftIsPaidForADayBeforeTheLastGift`;
  - `theGiftCycleFollowsTheGiftDayNotWhenItWasWritten` — подарок с меткой `created_at` из прошлого не ломает серию: на четвёртый день подряд это день 4.
- **`WithoutRustoreStoreTest`** (`src/testNoRustore`, только при `withoutRustore`): даже с непустым console id выбирается `UnconfiguredRuStorePayGateway`; товаров нет, неподтверждённых покупок нет, покупка — `Failed`. То, что такой шлюз даёт магазину `Unavailable`, уже проверяет существующий `GemStoreViewModelTest.aBuildWithoutAConsoleApplicationIdOpensTheStoreAsUnavailable`: он проходит, как и остальные тесты магазина.

Изменённые:

- **`FakeEconomyDao.lastLoginGift()`** теперь выбирает подарок по дню из `event_id`, а не по `createdAtEpochMillis`. Фейк должен повторять Room-запрос, который изменился в пункте 7.4. Без этого фейк проверял бы старое поведение.
- **`DailyRewardsTest`** — только добавлены тесты и константы `HOUR`/`MINUTE`, существующие не менялись.

Итого 84 JVM-теста `:app` (было 78: +2 отмены, +4 награды). Тест `WithoutRustoreStoreTest` в это число входит.

## 4. Команды проверки и итог

| Команда | Итог |
|---|---|
| `./gradlew -Plogica.withoutRustore=true :app:testDebugUnitTest :app:assembleRelease :app:lintRelease` | BUILD SUCCESSFUL; 84 теста, 0 падений; lint — только предупреждения (ниже) |
| `./gradlew :app:ktlintCheck` (без свойства, то есть с `src/rustore`) | успешно |
| `./gradlew -Plogica.withoutRustore=true :app:ktlintCheck` | успешно |
| `./gradlew :shared-ui:ktlintCheck :web-app:ktlintCheck` | успешно |
| `bash .claude/scripts/web-tests-node.sh` (favicon и общая карточка наград) | `passed=120 failed=0 skipped=0` |
| `./gradlew :web-app:compileKotlinWasmJs` | успешно |
| `aapt2 dump` release APK | `icon`, `roundIcon`, `dataExtractionRules`, `fullBackupContent` в манифесте; `ic_launcher` (anydpi), `window_background` (обычный и night), стили `v31` со сплэшем |

**lintRelease.** Ошибок нет. Из того, что относится к этапу, lint предупредил только об `ObsoleteSdkInt` для `mipmap-anydpi-v26`, это исправлено в `ae0bd8c`. Ранее существовавшие предупреждения я не трогал:

| Предупреждение | Сколько | Где и почему |
|---|---|---|
| `UnusedResources` | 97 | строки в `app/src/main/res/values*/strings.xml` |
| `InlinedApi` | 17 | `HapticFeedbackConstants.CONFIRM`/`REJECT` (API 30) в игровых экранах; на API 26–29 вибрация просто не срабатывает |
| `GradleDependency` / `NewerVersionAvailable` / `AndroidGradlePluginVersion` | 11 / 6 / 3 | версии зависимостей |
| `PluralsCandidate` | 10 | строки `values-en` |
| `AutoboxingStateCreation` | 2 | `BlockSudokuScreen.kt:78`, `SudokuGameScreen.kt:201` |
| `OldTargetApi` | 1 | |
| `ModifierParameter` | 1 | `ScreenLayout.kt:65` |

## 5. Отклонения, найденные проблемы, вопросы

1. **`mipmap-anydpi` вместо `mipmap-anydpi-v26`.** Так предложил `lintRelease` (`ObsoleteSdkInt`, minSdk 26), смысл тот же. После переименования инкрементальный `mergeReleaseResources` один раз не подхватил новую папку («resource mipmap/ic_launcher not found»), помог `--rerun`. Если у владельца локально будет такая ошибка, достаточно `./gradlew clean`.
2. **Новый файл DataStore `economy_clock`** вместо существующего. Отметке времени не место ни в настройках, ни в рекламе, ни в рекордах. Файл попадает в бэкап вместе с остальными, это безопасно: время идёт только вперёд.
3. **Награды за достижения** не ставятся на паузу при переводе часов: они не зависят от времени, а ledger и так платит один раз.
4. **Сборку с настоящим RuStore здесь проверить нельзя.** Код в `src/rustore` — перенос без правок, но компиляцию нужно проверить локально (список ниже).
5. **Возможная доработка (вне этапа).** Константы `HapticFeedbackConstants.CONFIRM`/`REJECT` используются без проверки API — давний `InlinedApi`. Вреда нет, но на Android 8–10 обратной связи нет. Исправить можно отдельной небольшой задачей.

## 6. Ручная проверка для владельца

Сборка и запуск с настоящим RuStore, локально (`local.properties` с `logica.rustoreConsoleAppId`, рекламными юнитами и подписью):

1. `./gradlew :app:assembleRelease` без `logica.withoutRustore`: сборка проходит, R8 не жалуется на классы RuStore.
2. Установить release на устройство и проверить запуск. Иконка — кристалл на кремовом фоне; на Android 13+ при тематических иконках — силуэт. На Android 12+ сплэш кремовый со светлой темой системы и тёмный с тёмной, без тёмной вспышки в светлом режиме.
3. Запустить каждую из семи игр, пройти и провалить по одному уровню, открыть Daily и «Задачу дня»: это проверка, что R8 ничего нужного не вырезал (Room, DataStore, ресурсы Compose, словари Word).
4. Магазин: открыть, увидеть цены RuStore, купить самый дешёвый пакет. Гемы начисляются, после повторного открытия магазина покупка не приходит второй раз.
5. Реклама: rewarded +1 жизнь и +1 гем в магазине. Interstitial после завершённого уровня (через 2 минуты после запуска).
6. Статус-бар: в приложении выбрать светлую тему при тёмной системе и наоборот. Иконки статус-бара и навигации видны в обоих случаях.
7. Награды: забрать подарок, перевести часы на час назад. Карточка показывает «Время на устройстве изменилось…», кнопок нет. Вернуть часы — награды снова доступны.
8. Бэкап (по желанию): `adb shell bmgr backupnow com.stanisryz.logica`, переустановить приложение, `adb shell bmgr restore …` или перенос на другое устройство. Прогресс, гемы и настройки на месте.
