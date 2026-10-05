# Отчёт: этап 1 — Веб: целостность облачных сохранений и ошибки загрузки

Задача: `tasksandreports/tasks/stage-01-web-cloud-save-integrity.md`.

## 1. SHA коммитов

Коммиты в `main` (перенесены cherry-pick из `claude/cloud-saves-integrity-loading-fk7vlo`, где были `966a75b` и `71aa384`; конфликтов не было, код совпадает байт в байт):

- `e6ef660` — A: облако больше не перезаписывается после неудачного или нечитаемого восстановления.
- `16a3b69` — B: отклонённые Promise доходят до обработки ошибок в контроллерах.

## 2. Что сделано

### A1 — явный результат загрузки

- `platform-contracts/.../CloudSaveContracts.kt`: новый `SaveLoadResult` — `Found(data)`, `Missing`, `Unavailable`, `Failed(cause)`, `Undecodable(reason)`. `SaveRepository.load()` возвращает его вместо nullable. В Android эти типы нигде не используются (grep по `app` и `shared-ui`).
- `WebSaveCodec.decodeForLoad`: битый конверт или конверт с `version > CURRENT_VERSION` даёт `Undecodable`. Бинарный формат не менялся.
- `YandexCloudSaveRepository`: `Missing → Missing`, `Failed → Failed`, `Unsupported → Unavailable`.
- `LocalSaveRepository`: нет ключа → `Missing`, битый Base64 или конверт → `Undecodable`, исключение хранилища → `Failed`.

### A2 — исход восстановления

- `WebSaveManager.restore()` возвращает `WebSaveRestoreOutcome`: `RESTORED`, `EMPTY`, `UNRESOLVED` или `UNAVAILABLE`.
- `UNRESOLVED` — неудачное чтение, нечитаемый конверт, нечитаемая секция или неизвестный id секции.
- `UNAVAILABLE` — это `Unsupported`: облака нет, ничего не пишется и не повторяется.
- Секции сообщают о неудаче: `apply(): Boolean` и `applyRestoring(): Boolean`. Каждая сначала декодирует payload и только потом проверяет привязку репозитория. Economy и Store проверяют обе части пары.
- Читаемые секции при `UNRESOLVED` всё равно сливаются (слияния монотонны и безопасны при повторе).

### A3 — планировщик (`WebUnifiedSaveScheduler.kt`)

Все записи (establish, drain, повторы записи, `flushNow`) проходят через одну проверку `canWrite`: запись разрешена, только если restore вернул `RESTORED` или `EMPTY` для текущего `activeToken`. Пока исход `UNRESOLVED`:

- `unifiedSaveActive = false`, статус `ERROR`, legacy-путь работает как раньше;
- restore повторяется через ~2s, ~8s, ~30s, после успеха выполняется establish;
- `markDirty()` ничего не пишет и запускает повтор restore с дебаунсом; если повтор уже запланирован, второй не создаётся;
- `flushNow()` возвращает `false`.

`invalidateContext()` и новый `restoreAndEstablish` отменяют все задания, включая повторы restore. Проверил `WebPayments.completeFulfillment`: при `flushNow() == false` он возвращает `PendingRetry` и не потребляет токен покупки. Гемы уже начислены локально, токен обрабатывается при следующей привязке Player.

### A4 — legacy-синхронизация

Проверил `synchronize`, `synchronizeStatistics`, `synchronizeDaily` в `WebPlayerSessionController`: `Failed`, `Unsupported` и недекодируемый payload ведут в `*SyncFailed` без записи. Код не менялся.

### B — ошибки загрузки уровня

- Оба приватных `await` (в `BrowserPuzzleDataLoader.kt` и `YandexGamesBridge.kt`) заменены одним `internal` хелпером в новом `WebPromiseAwait.kt`. Отклонение приходит как `IllegalStateException("A JavaScript promise was rejected.", reason.asJsException())`, поэтому срабатывают существующие `catch (Exception)` во всех семи контроллерах.
- Что `JsException` не наследник `Exception`, подтверждено тестом на JS-цели.
- Никакой код не проверяет тип `JsException` (grep).
- Другие места: все вызовы в `YandexGamesBridge` ловят `Throwable`; bootstrap ловит `Throwable`, а отклонение `init()` обрабатывает в `onRejected`; `WebCurrencyIcon`, `WebSettings`, `WebDailyTextSharer` обрабатывают отклонения сами. Других обходов обработки в контроллерах и bootstrap не нашёл.

### AGENTS.md

Добавлена одна строка о правиле: унифицированное облако пишется только после определённого восстановления для текущего Player-контекста; неудачное или нечитаемое чтение никогда его не перезаписывает.

## 3. Тесты

Новые:

- `WebUnifiedSaveIntegrityTest` (8 тестов):
  - `Failed` → ни одной записи ни при establish, ни при `markDirty`, ни при `flushNow`; после успешного повторного чтения — слияние и establish;
  - повторы restore ограничены, а `markDirty` после них запускает ещё один restore;
  - `Undecodable` (битый конверт и более новая версия) → облако не перезаписывается;
  - нечитаемая секция или неизвестный id → нет записи, читаемая секция всё равно слилась;
  - `Missing` → `EMPTY` → запись, как раньше;
  - `Unsupported` → ни повторов, ни записей;
  - смена токена во время повторов → запись старого контекста невозможна;
  - `LocalSaveRepository` возвращает явные результаты.
- `WebPromiseAwaitTest` (2 теста): отклонённый Promise ловится через `catch (Exception)` с причиной из JS; выполненный Promise возвращает значение.
- `WebUnifiedSaveStage4513Test.unresolvedUnifiedRestoreNeverOverwritesThePlayersCloudSave` — сквозной сценарий с настоящими секциями и `WebPlayerSessionController`: свежий браузер, `getData` падает → облако остаётся байт в байт прежним; затем облако читается → уровень 12 сливается и записывается; второй браузер с нечитаемой секцией statistics → каталог сливается, облако не трогается. Для этого в приватный фейк `FakeCloudSaveGateway` добавлен флаг `failReads`.

Правки существующих тестов (только из-за смены контракта, ни одна проверка не ослаблена):

- `WebCloudSavesTest`: `assertNull(load())` → `assertEquals(SaveLoadResult.Missing, …)`, чтение данных через `SaveLoadResult.Found`; `assertTrue(manager.restore())` → `assertEquals(RESTORED, …)`; `apply` тестовых секций возвращает `true`.
- `WebUnifiedSaveStage4513Test`: фейковые репозитории возвращают `SaveLoadResult` вместо `SaveData?`; `assertNull(repoOther.load())` → `assertEquals(Missing, …)`; `apply` возвращает `true`.
- `WebUnifiedSaveHardening4513aTest`: фейковые `load()` возвращают `SaveLoadResult.Missing` вместо `null`; `apply` возвращает `true`.

`WebPaymentsTest` и `WebPlayerSessionControllerTest` не менялись и проходят.

## 4. Проверки (на `main` после cherry-pick)

- `bash .claude/scripts/web-tests-node.sh` → `web tests: passed=78 failed=0 skipped=0`
- `./gradlew :web-app:ktlintCheck :platform-contracts:ktlintCheck` → `BUILD SUCCESSFUL`
- `./gradlew :web-app:compileKotlinWasmJs :web-app:compileKotlinJs :platform-contracts:compileAndroidMain` → `BUILD SUCCESSFUL`
- На ветке дополнительно: `./gradlew :web-app:compileTestKotlinWasmJs` → `BUILD SUCCESSFUL`.
- `./gradlew :app:compileDebugKotlin` → `BUILD FAILED` по причине среды: `Could not find ru.rustore.sdk:bom:2026.07.01`, хост `artifactory-external.vkpartner.ru` сбрасывает соединение. Вместо этого собран `:platform-contracts:compileAndroidMain` (`BUILD SUCCESSFUL`), и grep подтверждает, что `:app` не ссылается на изменённые типы.

## 5. Отклонения, найденные проблемы, вопросы

Отклонения:

- Четвёртый исход `UNAVAILABLE`, чтобы `Unsupported` не писал в облако и не крутил бесполезные повторы. Обычный standalone-путь не затронут: он использует `LocalSaveRepository`, который отвечает `Found` или `Missing`.
- Хелпер B — `internal` в отдельном файле; тест покрывает `await` на отклонённом и выполненном Promise. Тест самого загрузчика с подменой `fetch` не делал.
- В standalone битый локальный ключ `logica_unified_save_v1` теперь никогда не перезаписывается. По правилу так и должно быть; касается только разработки, данные доменов лежат в отдельных ключах.

Найдено, но не исправлено:

- Если домен не привязан (например, `WebStatisticsBinding.Unavailable`), его `export()` возвращает `null`, и `persist()` пишет конверт без этой секции — существующая потеря данных. Не стал считать это `UNRESOLVED`: тогда сломанное локальное хранилище одного домена навсегда заблокирует все записи.
- Если `mergeCloud` не смог сохраниться локально (`PersistenceFailed` или сбой, проглоченный `runCatching` в statistics и daily), следующий `persist()` запишет в облако несмёрженное локальное состояние. В задании такого условия не было.
- Если restore разрешился только после повтора, `reconcilePendingPurchases` в этой сессии второй раз не запускается. Неподтверждённые токены обрабатываются при следующей привязке Player; ничего не теряется и не начисляется дважды.

Вопросы:

- Нужно ли закрыть две проблемы выше (выпадающая секция отвязанного домена и запись после неудачного локального слияния) отдельным этапом?
- Нужно ли запускать сверку покупок сразу после позднего успешного восстановления, а не ждать следующей привязки Player?
