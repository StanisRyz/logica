# Этап 11.4a. Архив Daily: две мелочи из отчёта 11.4

Перед работой прочитай `AGENTS.md`, `tasksandreports/README.md` и раздел 5 отчёта 11.4. Работа идёт прямо в `main`.

1. **Верхняя панель архивной игры.** Сейчас она говорит «К играм», а назад ведёт в архив. Пусть говорит «К архиву» / «To the archive» / «Arşive dön», как кнопка карточки результата, на вебе (`WebTopBar`) и на Android.
   - Остальные игровые экраны (Каталог, сегодняшний Daily) не меняются.
   - Подпись передаётся параметром, не отдельной копией панели.
2. **Турецкий заголовок карточки результата архива** — по-турецки естественно: «3 Ekim'in bulmacası çözüldü / çözülemedi» или как точнее. Апостроф простой (см. `ComposeResourcesTextTest`).

## Проверка

```
./gradlew ktlintCheck
./gradlew -Plogica.withoutRustore=true :app:testDebugUnitTest :app:lintRelease
bash .claude/scripts/web-tests-node.sh
./gradlew :web-app:compileKotlinWasmJs
```

Скриншоты веба (RU, TR; 390×844): архивная игра с верхней панелью и карточка результата — в `tasksandreports/reports/img/stage-11-4a/`. После пуша — зелёный CI.

## Сдача

Отчёт — `tasksandreports/reports/stage-11-4a-archive-polish-report.md`. Если отчёт 11.6 уже написан, допиши в него одну строку о 11.4a.
