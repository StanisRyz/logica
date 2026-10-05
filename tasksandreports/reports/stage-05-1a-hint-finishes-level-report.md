# Отчёт: этап 5.1a — подсказка, открывшая последнюю клетку, завершает уровень

## 1. SHA коммитов

| Пункт | SHA | Коммит |
|---|---|---|
| Исправление и тесты | `d4c466a` | Android: a hint that opens the last cell finishes Balance and Crowns |

## 2. Что сделано

1. **Завершение после подсказки.** В `BalanceGameViewModel.requestHint` и `CrownsGameViewModel.requestHint` внутри блока `chargeAndShow`, после применения оплаченной подсказки, добавлено `if (paid && hintedGame.status.isTerminal) finishOrOffer(hintedGame)`. Логика завершения не дублируется: это тот же `finishOrOffer`, что вызывает `updateGame` после обычного хода. Подсказка не стоит ошибки, поэтому даёт только `SOLVED`, но путь общий, включая второй шанс.
2. **Остальные игры.** Свой обход `updateGame` был только у Balance и Crowns. Остальные пути завершения такие:

| Где | Путь подсказки | Завершение |
|---|---|---|
| Android Sudoku | `SudokuGameViewModel.kt:190–205`: `chargeAndShow { … updateGame(...) }` | `:290` `if (updated.status.isTerminal) finishOrOffer(updated)` |
| Android Nonogram | `NonogramGameViewModel.kt:148–152`: `if (paid) updateGame(current, hinted)` | `:193` то же |
| Android 2048, Блок-судоку, Word | подсказок нет | — |
| Web Balance | `WebBalanceController.kt:281`: `updateGame(current, hinted, …)` | `:388` переход в терминальное состояние |
| Web Crowns | `WebCrownsController.kt:278` | `:385` |
| Web Sudoku | `WebSudokuController.kt:371` | `:479` |
| Web Nonogram | `WebNonogramController.kt:243` | `:352` |

3. **Тесты.**
   - `BalanceHintChargeTest.aHintThatOpensTheLastCellRecordsTheSolvedLevel`: все пустые клетки, кроме последней, заполняются верными значениями из `BalanceSolver`, подсказка открывает последнюю. Проверяется `SOLVED`, одно записанное завершение `GameOutcome.SOLVED` и `CompletionPersistence.Saved` вместо вечного «сохраняется».
   - Новый `CrownsHintFinishTest`: то же для Crowns. Ставятся все короны решения, кроме одной, подсказка ставит последнюю.
   - Оба теста падают без исправления (проверено временным откатом двух ViewModel: 2 из 5 тестов упали) и проходят с ним.
   - Тестовые двойники (кошелёк с задержкой списания, замороженный уровень, записывающий репозиторий завершений) вынесены из `BalanceHintChargeTest` в общий `app/src/test/.../economy/HintTestDoubles.kt`, чтобы тесты Balance и Crowns их не копировали. Три прежних теста `BalanceHintChargeTest` по сути не менялись, изменилось только то, откуда они берут двойники.

## 3. Проверка

| Команда | Итог |
|---|---|
| `./gradlew ktlintCheck` | успешно |
| `./gradlew -Plogica.withoutRustore=true :app:testDebugUnitTest` | 105/105 (было 103) |
| CI https://github.com/StanisRyz/logica/actions/runs/37335826846 | все 5 job'ов зелёные |

## 4. Отклонения

Нет. Ответы по отчёту 5.1 приняты к сведению. Отложенная задача-карточка на это исправление снята, раз работа сделана здесь.
