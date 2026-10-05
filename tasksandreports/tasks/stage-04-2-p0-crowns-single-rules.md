# Этап 4.2. P0: Crowns на Android 8–14, единые правила экономики и выхода, понятные ошибки

Перед работой прочитай `AGENTS.md` и `tasksandreports/README.md`. Работа идёт прямо в `main`. Без попутных рефакторингов. Этап 4.1 принят, CI зелёный.

## 0. P0 — сделать первым и отдельным коммитом

### Находка исполнителя из 4.1

`CrownsGeneratorV1.kt:108` вызывает `crowns.removeLast()`. Android-вариант `:puzzle-core` привязывает этот вызов к Java 21 `List.removeLast()`, которого нет на API < 35. В итоге на Android 8–14 генерация Crowns падает с `NoSuchMethodError`. Владелец решил: чинить сейчас.

### Что сделать

1. `crowns.removeLast()` → `crowns.removeAt(crowns.lastIndex)`. Семантика та же, генерация та же.
2. Убери временное исключение `FrozenCatalogLevelPackTest` из `app/build.gradle.kts`. Тест должен пройти на JDK 17 в CI.
3. Проверь остальные похожие места во всех модулях с Android-целью (`:puzzle-core`, `:shared-ui`, `:platform-contracts`, `:app`). Подозрительны вызовы Java 21 `SequencedCollection` на `List`/`MutableList`/`ArrayList`: `removeFirst`, `removeLast`, `getFirst`, `getLast`, `addFirst`, `addLast`, `reversed`.
   - Мой беглый grep нашёл ещё `NonogramLineSolver.kt:48` (`stack.removeLast()`), `CrownsRegionConnectivity.kt:16` (`frontier.removeFirst()`), `.reversed()` в `Game2048Rules.kt:175`, `NonogramBoard.kt:247,263` и `Game2048Content.kt:292,295`.
   - По отчёту 4.1 первые два безопасны: это `ArrayDeque`. Проверь все остальные по dex, а не по исходникам.
   - Если что-то из них тоже уходит в Java 21 API, исправь так же.
4. Профилактика, чтобы класс ошибок не вернулся. В CI добавь проверку release dex (`-Plogica.withoutRustore=true :app:assembleRelease`, затем `dexdump` или `apkanalyzer`): упасть, если в dex есть вызов любого из перечисленных методов на `java.util.List`/`ArrayList`/`Collection`. Если надёжнее и проще включить lint `NewApi` для Android-вариантов библиотек так, чтобы он ловил именно этот случай, — сделай так и докажи в отчёте, что он ловит исходную ошибку: временно верни `removeLast()` и покажи падение.
5. Одна фраза в `AGENTS.md`: в общем коде не использовать `removeFirst/removeLast/getFirst/getLast` на списках, это Java 21 на Android API < 35, а CI это проверяет.

## 1. Числа экономики — из одного источника

### Как сейчас

Числа совпадают на обеих платформах, но только потому, что одни и те же литералы набраны в нескольких местах:

- `EconomyRules.kt:15-49` (Android) повторяет `EconomyPolicy` (`platform-contracts/.../Economy.kt:19-31`), хотя `:app` уже зависит от `:platform-contracts`.
- `WebStore.kt:~369-386` хардкодит цены подсказок и жизни: 4/10/3/10.
- `WebAds.kt:~146-147` хардкодит награду за рекламу: 1.
- `WebPayments.kt:~28`: `STARTER_PACK("starter_pack", 100, hintReward = 5 …)` набран вручную, а общий `StarterPackCard` показывает `StarterPackContents` (`shared-ui/.../StoreRows.kt:~227`). Стоит поменять одно — игрок увидит новый состав, а получит старый.

### Что сделать

- **Все числа экономики** — в `EconomyPolicy`:
  - стартовые гемы, жизни и подсказки;
  - интервал восстановления;
  - цена жизни;
  - цена одной подсказки, размер и цена набора;
  - награды за рекламу (гем и жизнь);
  - размеры паков 50/150/500.
- **Android.** `EconomyRules` делегирует в `EconomyPolicy` или удаляется, если это проще без лишнего шума.
- **Web.** `WebStoreCatalog`, `WebAds` и `WebPaidProduct` берут числа оттуда же.
- **Состав стартового набора.** Один источник для обоих хостов: `StarterPackContents` и Android `GemPack.STARTER_PACK` тоже ссылаются на него. Если `StarterPackContents` живёт в `:shared-ui`, а `:platform-contracts` от него не зависит, перенеси сами числа в `EconomyPolicy`, а `StarterPackContents` оставь представлением над ними.
- **Тест паритета.** Проверяет, что Android-правила, Web-каталог, Web-паки и стартовый набор дают одни и те же числа.
- **Поведение не меняется.** Ни одно число не меняется — это чистая консолидация.

## 2. Правило «реального прогресса» (выход стоит жизнь) — в ядре

### Как сейчас

`WebLeaveLevelGuard.kt:~158-206` вручную «зеркалит» Android ViewModel'и: `BalanceGameViewModel.kt:~56`, `CrownsGameViewModel.kt:~54`, `SudokuGameViewModel.kt:~58`, `WordGameViewModel.kt:~49`, `Game2048ViewModel.kt:~47`, плюс Block Sudoku, если есть. У Nonogram правило уже в ядре (`NonogramGame.kt:~52`).

### Что сделать

- **Правило в ядре.** Для каждой игры правило `hasMeaningfulProgress(state)` переезжает в `:puzzle-core` рядом с движком, как у Nonogram.
- **Хосты.** Оба хоста вызывают только эти функции.
- **Расхождения.** Логика должна остаться в точности той же. Если версии Android и Web где-то расходятся, не выбирай молча: опиши расхождение в отчёте и оставь более мягкий для игрока вариант, то есть тот, что реже снимает жизнь.
- **Тесты.** Короткие тесты в `commonTest` на границы: пустое состояние → `false`, первый реальный ход → `true`. Для 2048 оговори, считается ли один свайп прогрессом, и приведи текущее поведение.

## 3. Игрок видит понятную ошибку, а не текст исключения

### Как сейчас

- Контроллеры Web показывают `exception.message ?: "Balance level is unavailable."` (`WebBalanceController.kt:~168` и аналоги).
- Этот текст доходит до `WebCatalogDialogs.kt:~117` и до `FatalContent` (`WebApp.kt:~2344`).
- Игрок видит английский технический текст на любом языке.

### Что сделать

- **Состояния ошибок** несут тип причины (enum): загрузка данных, данные повреждены, прогресс недоступен, неизвестно. Строка исключения в них больше не хранится.
- **Экран** показывает локализованный текст из `WebRes` на трёх языках.
- **`FatalContent`** — тоже локализованный текст.

## 4. Минимальное логирование ошибок

### Как сейчас

Логов нет совсем. Сбои глотаются молча:

- Android: награды (`DailyRewards`), покупки, магазин;
- Web: привязка репозитория платежей (`WebPlayerSessionController.kt:~251-268` тихо делает `return`), облачная синхронизация, загрузка данных.

### Что сделать

- **Маленький шов `AppLog.warn(tag, message, throwable?)`**:
  - Android — `Log.w`;
  - Web — `console.warn`;
  - тесты — no-op или сбор в список.
- **Где использовать.** Только в местах, где ошибка сейчас глотается без следа: перечисленные выше и те, что встретишь по пути в этом же коде.
- **Без персональных данных:** id игрока, токены покупок и содержимое сохранений не логируются.
- **Без инфраструктуры:** никакой отправки на сервер и никаких новых зависимостей.

## Проверка перед пушем

```
./gradlew ktlintCheck :puzzle-core:jvmTest
./gradlew -Plogica.withoutRustore=true :app:testDebugUnitTest :app:assembleRelease
bash .claude/scripts/web-tests-node.sh
./gradlew :web-app:compileKotlinWasmJs
```

После пуша убедись, что CI зелёный, включая новую проверку dex. Ссылку на прогон приложи к отчёту.

## Ограничения

- Форматы, ключи, миграции и экономические числа не меняются.
- Новых зависимостей нет.
- `AGENTS.md`:
  - фраза из п. 0;
  - строка про `EconomyRules` («the only place Android economy numbers live») — исправь, теперь это `EconomyPolicy`;
  - по одной фразе о правиле прогресса в ядре, о локализованных ошибках и о `AppLog`.

## Сдача

Коммиты в `main`, по одному на пункт, п. 0 — первым. Отчёт — в `tasksandreports/reports/stage-04-2-p0-crowns-single-rules-report.md`.
