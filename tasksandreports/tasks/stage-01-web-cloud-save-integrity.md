# Этап 1. Веб: целостность облачных сохранений и ошибки загрузки

Перед работой прочитай `AGENTS.md`: это обязательная спецификация проекта. Протокол работы описан в `tasksandreports/README.md`.

Работай только в рамках этой задачи, без попутных рефакторингов.

## Проблема A (критическая): неудачное чтение облака перезаписывает облачное сохранение

### Как сейчас

- `web-app/.../WebCloudSaves.kt:105-110`: `YandexCloudSaveRepository.load()` превращает `CloudSaveReadResult.Failed` (и `Unsupported`) в `null`. Это неотличимо от «сохранения нет». `WebSaveCodec.decode()` тоже возвращает `null` на нечитаемый или незнакомый конверт.
- `WebSaveManager.restore()` на `null` возвращает `false`, а `WebUnifiedSaveScheduler.restoreAndEstablish()` (`WebUnifiedSaveScheduler.kt` ~78-90) всё равно вызывает `establish()`. Тот вызывает `persist()` и пишет весь локальный конверт в `logica_unified_save_v1`.
- `markDirty()`, `drain()`, `flushNow()` и повторы тоже пишут унифицированный конверт, даже если восстановление для этого контекста так и не получило определённого ответа.
- Секции (`WebSaveSections.*.applyRestoring/apply`) молча пропускают нечитаемый payload: `?.let(Codec::decode)` даёт `null`, и происходит `return`. После этого `persist()` затирает эту секцию в облаке локальными данными.

### Сценарий потери данных

Игрок открывает игру в новом браузере или на новом устройстве, или после очистки данных сайта. Один сбой сети на `player.getData`, и облако перезаписывается свежим состоянием: 10 гемов, уровень 1, пустой журнал покупок, нет «без рекламы». Тот же эффект, если старая вкладка не может прочитать конверт, записанный более новой сборкой.

### Что сделать

1. **Ввести явный результат загрузки вместо nullable.** Например, sealed `SaveLoadResult { Found(data), Missing, Failed(cause), Undecodable }` (имена на твоё усмотрение).
   - Контракт `SaveRepository` живёт в `:platform-contracts` (`CloudSaveContracts.kt`), его можно менять. Android его не использует, проверь это grep'ом.
   - Local fallback repository адаптируй так же.
   - Соответствие:
     - `CloudSaveReadResult.Missing` → `Missing`.
     - `Failed` → `Failed`.
     - `Unsupported` → «облака нет»: никаких облачных записей, обычный standalone-путь работает как раньше.
     - `Found`, но конверт не декодируется (битый, неизвестная версия) → `Undecodable`.
2. **`WebSaveManager.restore()` возвращает исход** `RESTORED`, `EMPTY` (определённо пусто) или `UNRESOLVED`.
   - `UNRESOLVED` бывает в любом из случаев:
     - чтение вернуло `Failed`;
     - конверт `Undecodable`;
     - хотя бы одна присутствующая в конверте секция не декодируется своим кодеком;
     - в конверте есть неизвестный id секции (от более новой сборки).
   - Для этого секции должны сообщать о неудаче декодирования, а не молча пропускать её.
   - Секции, которые декодировались, при `UNRESOLVED` применять можно: слияния монотонны и безопасны. Но писать облако нельзя.
3. **`WebUnifiedSaveScheduler`.** Любая унифицированная запись (`establish`, `drain` после `markDirty`, повторы, `flushNow`) разрешена только после того, как `restore` для текущего токена вернул `RESTORED` или `EMPTY`. Пока исход `UNRESOLVED`:
   - `unifiedSaveActive = false`, статус `ERROR`; legacy-путь продолжает работать как сейчас;
   - выполняются ограниченные повторы именно `restore` (например, через 2 с, 8 с, 30 с), а после успеха — `establish`;
   - `markDirty()` в этом состоянии не пишет конверт, а запускает (с дедупликацией) повтор `restore` → `establish`;
   - `flushNow()` возвращает `false`. Это безопасно: `WebPayments` тогда не потребляет токен покупки, и она переобработается позже. Проверь, что это действительно так и ничего не ломается;
   - `invalidateContext()` и смена Player отменяют все повторы, как сейчас.
4. **Legacy-синхронизация.** В `WebPlayerSessionController` (`synchronize`, statistics, daily, ~ строки 520-830) при `Failed` уже происходит переход в `syncFailed`. Проверь это и не ломай.

## Проблема B: сетевой сбой загрузки уровня оставляет экран загрузки навсегда

### Как сейчас

- В `BrowserPuzzleDataLoader.kt:84-97` и `YandexGamesBridge.kt:651-662` отклонённый Promise превращается в `reason.asJsException()`.
- `JsException` в Kotlin/Wasm и JS наследуется от `Throwable`, а не от `Exception`. Проверь это по stdlib.
- Все семь контроллеров (`WebBalanceController.kt:~157` и аналоги в Crowns/Word/Sudoku/2048/Nonogram/BlockSudoku) ловят только `Exception`.
- Поэтому ошибка улетает в `SupervisorJob`-scope, состояние не становится `Error`, и кнопки «Повторить» нет.

### Что сделать

- В обоих await-хелперах оборачивай отклонение в наследника `Exception` с `cause`, например `IllegalStateException("...", reason.asJsException())`. Тогда существующие `catch` сработают.
- Проверь, что ни один вызывающий код не опирается на тип `JsException`.
- Если в контроллерах или bootstrap есть ещё места, где `Throwable` из JS-интеропа может обойти обработку, перечисли их в отчёте. Исправляй только очевидные однострочные случаи.

## Тесты (webTest)

Новые тесты:

- **`restore` при `Failed`** даёт `UNRESOLVED`. Ни одной записи в `repository.save`: ни при `establish`, ни при `markDirty`, ни при `flushNow`. После успешного повторного чтения — обычное слияние и `establish`.
- **Нечитаемые данные:** `Undecodable` конверт и конверт с одной нечитаемой секцией не перезаписывают облако.
- **Пустое облако:** `Missing` даёт `EMPTY`, и `establish` пишет, как раньше.
- **Смена токена во время повторов:** запись старого контекста невозможна.
- **Проблема B:** если await-хелпер можно вынести в общий internal-файл и протестировать на отклонённом Promise, сделай это. Если нельзя, объясни почему.

Существующие тесты должны проходить: `WebCloudSavesTest`, `WebUnifiedSaveStage4513Test`, `WebUnifiedSaveHardening4513aTest`, `WebPaymentsTest`, `WebPlayerSessionControllerTest`. Правь их, только если меняется контракт, и объясни каждую правку в отчёте.

## Ограничения

- Не менять бинарные форматы (`WebSaveCodec`, кодеки секций), ключи хранилища и семантику слияний.
- Не трогать Android `:app`. Без новых зависимостей.
- В `AGENTS.md` добавь или поправь одну-две строки о правиле: «унифицированное облако пишется только после определённого восстановления (Found/Missing) для текущего Player-контекста; неудачное или нечитаемое чтение никогда не перезаписывает облако».

## Проверка перед пушем

```
bash .claude/scripts/web-tests-node.sh
./gradlew :web-app:ktlintCheck :platform-contracts:ktlintCheck
./gradlew :web-app:compileKotlinWasmJs :web-app:compileKotlinJs
./gradlew :app:compileDebugKotlin        # если менялся :platform-contracts
```

## Сдача

Закоммить и запушь в `ccr-e9e9918f-j8alsq` (см. `tasksandreports/README.md`). Можно двумя коммитами: A и B.

Отчёт положи в `tasksandreports/reports/stage-01-web-cloud-save-integrity-report.md` в формате из README. Пункты отчёта: A1–A4 и B.
