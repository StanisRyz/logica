# Отчёт: этап 10.1 — Word на английском и турецком: словари и ядро

## 1. SHA коммитов

| SHA | Коммит |
|---|---|
| `3758452` | Word core: the generator version names the language |
| `57ee829` | Word V3/V4 lexicons: English and Turkish data, tools, and provenance |

Абзац `AGENTS.md` о V3/V4 — во втором коммите. Паки уровней не писались, интерфейс не менялся: игроки видят прежний русский Word.

## 2. Что сделано

### Архитектура

Решения задачи приняты без пересмотра.

- **Язык в версии генератора.** V1 и V2 — русские, V3 — английский, V4 — турецкий. Таблица «версия → язык → генератор → словарь» одна: `WordRuntimeResolver` (`language()` и `resolve()`). `WordRuntime` получил поле `language`; у него значение по умолчанию «русский», поэтому два веб-теста, которые сами собирают `WordRuntime`, не менялись.
- **Длины V3/V4** — как у V2: 4/5/6/7 по сложностям (`WordRules.wordLengthForV2`).
- **Русские V1/V2 не тронуты:**
  - их словари и паки не менялись ни на байт, `verifyCatalogLevelPacks` проходит;
  - `RussianWordNormalizer` только объявлен реализацией общего интерфейса, его правила те же;
  - все прежние тесты Word проходят без правок.
- **Общий прогресс уровней.** В Room, ключах и форматах сохранений ничего не ломается: прогресс хранится по `PuzzleType + Difficulty + версия пака`, статистика — по `PuzzleType`, рейтинг Word — один.
  - **Что понадобится в 10.2.** Путь файла пака — `levels/v1/word/<difficulty>.lvp`, в нём нет языка. Паку второго языка нужен свой сегмент пути, например `word_en`, или своя версия пака. Заголовок пака уже хранит версию генератора, а значит и язык.
  - **Статистика.** Результаты Word на трёх языках сольются в одну статистику Word, включая полоски попыток. По-моему, это верно: игра одна.

### 1. Нормализация

- **`WordNormalizer`** — общий контракт: нижний регистр, только буквы своего алфавита, явная длина.
- **`WordLanguage`** (`RUSSIAN`/`ENGLISH`/`TURKISH`) несёт нормализатор своего языка. Черновик, попытки, разметка, знание букв и загрузка словаря берут его из версии головоломки.
- **`EnglishWordNormalizer`**: 26 букв a–z, верхний регистр по таблице ASCII.
- **`TurkishWordNormalizer`**: своя таблица без локали платформы.
  - `I → ı`, `İ → i`, `Ç Ğ Ö Ş Ü` → строчные.
  - 29 букв, без q, w, x.
  - **Циркумфлекс.** `â î û` складываются в `a i u`, как `ё → е` в русском: на большинстве турецких клавиатур этих букв нет, и современное письмо их чаще опускает.
  - В ответы при этом не берутся слова, которые в словаре Zemberek записаны с циркумфлексом. Без него они выглядят непривычно (`hâlâ` / `hala`), так что я их исключил; см. вопросы владельцу.

### 2. Словари

| | Английский (V3) | Турецкий (V4) |
|---|---|---|
| Догадки | ENABLE (public domain), все слова 4–7 букв с формами | Леммы Zemberek (Apache-2.0) без имён собственных и сокращений + формы из списка wordfreq, которые Zemberek разбирает (`evler`) |
| Ответы | Леммы Open English WordNet 2023 (CC BY 4.0), где существительное преобладает | Леммы Zemberek, которые только существительные |
| Ранжирование | wordfreq 3.1.1 (CC BY-SA 4.0), Zipf ≥ 3.0 | wordfreq 3.1.1, есть в турецком списке (Zipf ≥ 3.0 — его порог) |
| Ручные списки | block 240, allow 0 | block 325, allow 86 |
| Инструмент | `tools/word-lexicon/extract_english.py` | `tools/word-lexicon/extract_turkish.py` |

**Английские фильтры ответов.** Слово отсеивается, если оно:
- регулярный plural другого существительного;
- форма глагола (-s, -ed, -ing) с одним значением-существительным;
- имя или место (есть такая же запись WordNet с заглавной буквы), а у нарицательного не больше двух значений.

**Турецкие фильтры ответов.** Слово отсеивается, если анализатор читает его как:
- форму глагола;
- прилагательное на -sal/-sel;
- plural другой леммы;
- другую словоформу леммы, которая на 0.5 Zipf чаще (`adamı` = `adam` + винительный).

**Зачем турецкий allowlist.** Фильтр «только существительное» строгий: Zemberek часто пишет существительное ещё и прилагательным, а `oyun` или `düşünce` читает как формы глагола. Поэтому 86 частых хороших существительных добавлены вручную (`insan`, `kadın`, `çocuk`, `gece`, `oyun`, `anahtar`, …).

**Блоклисты.** Не попадают в ответы, но остаются догадками:
- грубое и сексуальное, оскорбления;
- насилие и смерть, наркотики и болезни;
- рабство и расизм;
- в турецком ещё религиозные обряды и политические символы;
- имена, места и бренды, которые фильтры пропустили;
- числа, служебные слова и формы глаголов.

**Воспроизводимость.**
- Источники закреплены: pip-версии, ENABLE и WordNet — по SHA-256, инструмент их проверяет.
- Повторный прогон даёт байт-в-байт те же файлы.
- Турецкий инструмент перезапускает себя с `PYTHONHASHSEED=0`: анализатор Zemberek перебирает множества строк, и без этого 1–3 догадки с удвоенной согласной (`reddi`, `tıbbı`) появлялись и пропадали.
- Происхождение, лицензии, команды и числа лежат в `datasets/word/en/` и `datasets/word/tr/` (`PROVENANCE.md`, `import-report.json`). Таблицы для ревью — в `lexicon/word/v3|v4/generated/`.

**Числа:**

| Длина | EN ответы / кандидаты / догадки | TR ответы / кандидаты / догадки |
|---|---|---|
| 4 | 500 / 864 / 3 903 | 500 / 697 / 2 653 |
| 5 | 500 / 1 040 / 8 636 | 500 / 1 279 / 7 018 |
| 6 | 500 / 1 186 / 15 232 | 500 / 1 000 / 9 017 |
| 7 | 500 / 1 236 / 23 109 | 500 / 738 / 11 096 |

Минимум 500 ответов на длину набирается везде.

### 3. Генераторы и ядро

- **`WordGeneratorByLength`** (версии 3 и 4) — выбор V2, одна выборка `PuzzleRandomV1` из пула длины, над словарём своего языка.
- **`WordLexiconByLength`** — общий загрузчик словаря V3/V4 по образцу `WordLexiconV2`: файл ответов `<слово>\t<сложность>`, алфавитный порядок.
- **Правила те же:** шесть попыток, двухпроходная разметка, отказ без траты попытки.
- **Ресурсы** лежат в `puzzle-core/src/commonMain/resources/word/v3|v4/` и попадают в Android и веб тем же путём, что V2.
  - На вебе `WebPuzzleData` теперь принимает эти пути, а загрузчик берёт файлы из `requiredResourcePaths` версии, то есть только нужный язык.
  - Размер: V3 384 КБ, V4 252 КБ без сжатия.

## 3. Новые и изменённые тесты

- **Новый `WordLanguageNormalizerTest`** (`commonTest`, все цели):
  - английский регистр и отказ от `é`, `ï`, кириллицы, дефиса;
  - турецкие `I/İ/ı/i`;
  - `ÇĞÖŞÜ`;
  - `â/î/û` → `a/i/u`;
  - отказ от q, w, x и кириллицы;
  - язык каждой версии.
- **`GoldenDeterminismTest`** (существующий файл) получил новый тест `wordV3AndV4SelectFromTheirPoolsTheSameWayOnEveryTarget`, прежние тесты в нём не менялись.
  - Словари на JS/Wasm из `commonTest` не читаются, поэтому выбор V3/V4 проверяется на синтетических пулах реального размера (500) с турецким алфавитом для V4.
  - Описание класса поправлено: раньше там было «Word left out».
- **Новый `WordLanguageLexiconTest`** (JVM):
  - 500 ответов на сложность, все — догадки, порядок алфавитный;
  - резолвер для V3/V4;
  - golden-ответы для сидов 1 и 2 каждой сложности;
  - турецкая партия с вводом заглавными `İZİN` и отказом от `q`;
  - английская: слово вне догадок не тратит попытку.
- Других изменений существующих тестов нет.

## 4. Команды проверки и итог

| Команда | Итог |
|---|---|
| `./gradlew ktlintCheck` | OK |
| `./gradlew :puzzle-core:jvmTest` | OK, все прежние тесты Word и golden без изменений |
| `./gradlew :puzzle-core:verifyCatalogLevelPacks` | OK, контрольные суммы паков те же |
| `./gradlew :puzzle-core:allTests` | Цели JS/Wasm в этой среде не запускаются (браузерным тестам нужен Karma, его пакеты не скачиваются). Проверит CI |
| `./gradlew -Plogica.withoutRustore=true :app:testDebugUnitTest` | 126/126 |
| `bash .claude/scripts/web-tests-node.sh` | 140 passed, 0 failed |
| `./gradlew :web-app:compileKotlinWasmJs` | OK |

CI прошлого пуша (`5023fed`, этап 8.1a): зелёный, https://github.com/StanisRyz/logica/actions/runs/37381874463.

## 5. Образцы для владельца

Выборка детерминированная (`random.Random(20261006 + длина)`), её можно воспроизвести из `import-report.json`.
- **Отброшенные** — 20 самых частых слов, которые фильтр отсеял по существенной причине. Служебные слова без записи-существительного (`that`, `için`) не показаны: их тысячи. Коды причин:
  - `NOT_NOUN_DOMINANT` — существительное не главное значение;
  - `VERB_FORM` — форма глагола;
  - `PLURAL_FORM` / `INFLECTED_FORM` — словоформа;
  - `ALSO_OTHER_PART_OF_SPEECH` — у леммы есть и другая часть речи;
  - `CIRCUMFLEX` — лемма с циркумфлексом;
  - `MANUAL_BLOCK` — ручной блоклист;
  - `PROPER_ONLY` — только имя собственное.

### Английский (V3)

#### Английский, 4 букв (Легко)

Ответов 500 (кандидатов после фильтров 864), догадок 3903.

- **40 ответов:** aide, beam, bite, buck, bush, card, coup, crab, crew, dawn, dose, east, edge, expo, flaw, foam, folk, gaze, gear, goal, gown, grip, herb, hill, isle, joke, logo, mine, noun, park, plea, pony, root, scam, self, size, slam, sofa, stop, wing.
- **20 отброшенных фильтром:** have (NOT_NOUN_DOMINANT), like (NOT_NOUN_DOMINANT), more (PROPER_ONLY), good (NOT_NOUN_DOMINANT), know (NOT_NOUN_DOMINANT), make (NOT_NOUN_DOMINANT), over (NOT_NOUN_DOMINANT), then (NOT_NOUN_DOMINANT), back (NOT_NOUN_DOMINANT), want (NOT_NOUN_DOMINANT), well (NOT_NOUN_DOMINANT), much (NOT_NOUN_DOMINANT), even (NOT_NOUN_DOMINANT), here (NOT_NOUN_DOMINANT), work (NOT_NOUN_DOMINANT), take (NOT_NOUN_DOMINANT), down (NOT_NOUN_DOMINANT), last (NOT_NOUN_DOMINANT), best (NOT_NOUN_DOMINANT), look (NOT_NOUN_DOMINANT).

#### Английский, 5 букв (Средне)

Ответов 500 (кандидатов после фильтров 1040), догадок 8636.

- **40 ответов:** actor, album, amino, arena, armor, badge, cabin, charm, color, curse, death, derby, elbow, fruit, giant, glass, grade, grief, lobby, mayor, metal, month, mouse, orbit, print, promo, river, rugby, scent, shade, share, spear, stone, thing, toast, trial, truck, usage, water, yacht.
- **20 отброшенных фильтром:** there (NOT_NOUN_DOMINANT), first (NOT_NOUN_DOMINANT), think (NOT_NOUN_DOMINANT), right (NOT_NOUN_DOMINANT), years (PLURAL_FORM), being (MANUAL_BLOCK), going (MANUAL_BLOCK), still (NOT_NOUN_DOMINANT), great (NOT_NOUN_DOMINANT), while (MANUAL_BLOCK), three (MANUAL_BLOCK), found (NOT_NOUN_DOMINANT), might (MANUAL_BLOCK), start (NOT_NOUN_DOMINANT), times (PLURAL_FORM), today (MANUAL_BLOCK), small (NOT_NOUN_DOMINANT), white (NOT_NOUN_DOMINANT), using (VERB_FORM), black (NOT_NOUN_DOMINANT).

#### Английский, 6 букв (Сложно)

Ответов 500 (кандидатов после фильтров 1186), догадок 15232.

- **40 ответов:** accent, advice, author, backup, beacon, belief, border, bureau, candle, cinema, cousin, critic, damage, editor, family, fossil, height, hunter, jungle, minute, monkey, mother, nation, needle, nephew, palace, parole, portal, prince, roller, school, shield, spirit, subway, tenant, tenure, threat, tongue, virtue, worker.
- **20 отброшенных фильтром:** better (NOT_NOUN_DOMINANT), little (NOT_NOUN_DOMINANT), things (PLURAL_FORM), enough (NOT_NOUN_DOMINANT), thanks (MANUAL_BLOCK), social (NOT_NOUN_DOMINANT), single (NOT_NOUN_DOMINANT), coming (MANUAL_BLOCK), taking (VERB_FORM), saying (VERB_FORM), future (NOT_NOUN_DOMINANT), living (NOT_NOUN_DOMINANT), behind (NOT_NOUN_DOMINANT), former (NOT_NOUN_DOMINANT), common (NOT_NOUN_DOMINANT), inside (NOT_NOUN_DOMINANT), return (NOT_NOUN_DOMINANT), middle (NOT_NOUN_DOMINANT), answer (NOT_NOUN_DOMINANT), longer (MANUAL_BLOCK).

#### Английский, 7 букв (Эксперт)

Ответов 500 (кандидатов после фильтров 1236), догадок 23109.

- **40 ответов:** airport, athlete, barrier, borough, brigade, captive, chapter, concern, contact, country, current, defence, example, grocery, hunting, illness, interim, lawsuit, mistake, network, nominee, opening, outlook, package, partner, pathway, patriot, reading, receipt, release, removal, reserve, storage, student, surgery, tractor, traffic, utility, warrant, warrior.
- **20 отброшенных фильтром:** someone (MANUAL_BLOCK), getting (VERB_FORM), looking (MANUAL_BLOCK), nothing (MANUAL_BLOCK), general (NOT_NOUN_DOMINANT), working (NOT_NOUN_DOMINANT), special (NOT_NOUN_DOMINANT), fucking (NOT_NOUN_DOMINANT), million (MANUAL_BLOCK), minutes (PLURAL_FORM), talking (VERB_FORM), process (NOT_NOUN_DOMINANT), outside (NOT_NOUN_DOMINANT), running (NOT_NOUN_DOMINANT), project (NOT_NOUN_DOMINANT), perfect (NOT_NOUN_DOMINANT), english (PROPER_ONLY), private (NOT_NOUN_DOMINANT), present (NOT_NOUN_DOMINANT), average (NOT_NOUN_DOMINANT).

### Турецкий (V4)

#### Турецкий, 4 букв (Легко)

Ответов 500 (кандидатов после фильтров 697), догадок 2653.

- **40 ответов:** adak, arpa, atom, ağıt, baca, bent, defa, demo, depo, dram, ezgi, faul, felç, filo, gaye, gece, golf, harf, harp, hobi, iade, idil, iyot, içki, kedi, kişi, lale, mira, oluk, papa, puma, rica, soru, sure, sıra, tarz, tema, tren, vade, şaka.
- **20 отброшенных фильтром:** daha (ALSO_OTHER_PART_OF_SPEECH), önce (ALSO_OTHER_PART_OF_SPEECH), olur (ALSO_OTHER_PART_OF_SPEECH), alan (VERB_FORM), geri (ALSO_OTHER_PART_OF_SPEECH), gece (ALSO_OTHER_PART_OF_SPEECH), eski (ALSO_OTHER_PART_OF_SPEECH), hala (ALSO_OTHER_PART_OF_SPEECH), günü (MANUAL_BLOCK), kısa (ALSO_OTHER_PART_OF_SPEECH), süre (VERB_FORM), açık (ALSO_OTHER_PART_OF_SPEECH), eder (VERB_FORM), genç (ALSO_OTHER_PART_OF_SPEECH), hava (ALSO_OTHER_PART_OF_SPEECH), oyun (VERB_FORM), beri (ALSO_OTHER_PART_OF_SPEECH), spor (ALSO_OTHER_PART_OF_SPEECH), elde (MANUAL_BLOCK), tabi (ALSO_OTHER_PART_OF_SPEECH).

#### Турецкий, 5 букв (Средне)

Ответов 500 (кандидатов после фильтров 1279), догадок 7018.

- **40 ответов:** ahlak, albüm, anket, asist, aslan, balon, cadde, delil, duvar, esnaf, eylem, fikir, göbek, hüzün, idrak, ifade, ikram, ipucu, korku, kusur, köfte, kırım, lazer, mesaj, perde, poker, sakal, saray, sergi, sonuç, stres, tekne, vatan, yürek, yıkım, çarşı, çevre, çorap, ısrar, şükür.
- **20 отброшенных фильтром:** kadar (ALSO_OTHER_PART_OF_SPEECH), sonra (ALSO_OTHER_PART_OF_SPEECH), büyük (ALSO_OTHER_PART_OF_SPEECH), güzel (ALSO_OTHER_PART_OF_SPEECH), artık (ALSO_OTHER_PART_OF_SPEECH), doğru (ALSO_OTHER_PART_OF_SPEECH), devam (ALSO_OTHER_PART_OF_SPEECH), neden (ALSO_OTHER_PART_OF_SPEECH), şimdi (ALSO_OTHER_PART_OF_SPEECH), karşı (ALSO_OTHER_PART_OF_SPEECH), bütün (ALSO_OTHER_PART_OF_SPEECH), bugün (ALSO_OTHER_PART_OF_SPEECH), küçük (ALSO_OTHER_PART_OF_SPEECH), insan (ALSO_OTHER_PART_OF_SPEECH), kadın (ALSO_OTHER_PART_OF_SPEECH), çocuk (ALSO_OTHER_PART_OF_SPEECH), kimse (ALSO_OTHER_PART_OF_SPEECH), hayır (ALSO_OTHER_PART_OF_SPEECH), erkek (ALSO_OTHER_PART_OF_SPEECH), gerek (ALSO_OTHER_PART_OF_SPEECH).

#### Турецкий, 6 букв (Сложно)

Ответов 500 (кандидатов после фильтров 1000), догадок 9017.

- **40 ответов:** abartı, afiyet, avukat, bahane, baykuş, başarı, beceri, bozkır, büyücü, devlet, eziyet, fatiha, ferman, gümrük, hikmet, inşaat, kardeş, kayısı, kongre, mektup, mercek, merkez, meteor, meydan, miktar, numara, otoyol, rağbet, reçete, stoper, tahlil, tasarı, tehdit, teşvik, vampir, vazife, vizyon, yelken, çevrim, şirket.
- **20 отброшенных фильтром:** gerçek (ALSO_OTHER_PART_OF_SPEECH), yüksek (ALSO_OTHER_PART_OF_SPEECH), yerine (ALSO_OTHER_PART_OF_SPEECH), tekrar (ALSO_OTHER_PART_OF_SPEECH), dikkat (ALSO_OTHER_PART_OF_SPEECH), dakika (ALSO_OTHER_PART_OF_SPEECH), sosyal (ALSO_OTHER_PART_OF_SPEECH), yalnız (ALSO_OTHER_PART_OF_SPEECH), yanlış (ALSO_OTHER_PART_OF_SPEECH), teknik (ALSO_OTHER_PART_OF_SPEECH), yardım (VERB_FORM), günlük (ALSO_OTHER_PART_OF_SPEECH), yıllık (ALSO_OTHER_PART_OF_SPEECH), normal (ALSO_OTHER_PART_OF_SPEECH), ulusal (ADJECTIVE_FORM), yıldız (ALSO_OTHER_PART_OF_SPEECH), aralık (ALSO_OTHER_PART_OF_SPEECH), teslim (ALSO_OTHER_PART_OF_SPEECH), toprak (ALSO_OTHER_PART_OF_SPEECH), benzer (ALSO_OTHER_PART_OF_SPEECH).

#### Турецкий, 7 букв (Эксперт)

Ответов 500 (кандидатов после фильтров 738), догадок 11096.

- **40 ответов:** altyapı, asansör, ağustos, basamak, belirti, bunalım, dağıtım, ekipman, ekonomi, eşofman, feragat, fiyasko, hükümet, karaoke, kiremit, kolordu, kraliçe, kuyumcu, mobilya, muhatap, mücahit, müşteri, nezaket, nitelik, ortaçağ, padişah, palmiye, sağanak, segment, senaryo, senatör, senfoni, telefon, telgraf, tersane, vardiya, vasiyet, yumurta, öğrenci, öğrenim.
- **20 отброшенных фильтром:** gelecek (ALSO_OTHER_PART_OF_SPEECH), merhaba (ALSO_OTHER_PART_OF_SPEECH), kırmızı (ALSO_OTHER_PART_OF_SPEECH), serbest (ALSO_OTHER_PART_OF_SPEECH), sevgili (ALSO_OTHER_PART_OF_SPEECH), kişilik (ALSO_OTHER_PART_OF_SPEECH), savunma (VERB_FORM), verecek (VERB_FORM), değişik (ALSO_OTHER_PART_OF_SPEECH), intihar (MANUAL_BLOCK), çıkacak (VERB_FORM), elinden (INFLECTED_FORM), anlaşma (VERB_FORM), düşünce (VERB_FORM), dijital (ALSO_OTHER_PART_OF_SPEECH), paralel (ALSO_OTHER_PART_OF_SPEECH), üstelik (ALSO_OTHER_PART_OF_SPEECH), anahtar (ALSO_OTHER_PART_OF_SPEECH), benzeri (ALSO_OTHER_PART_OF_SPEECH), şikayet (CIRCUMFLEX).

## 6. Сомнения

- **Английский: два написания.** В ответах есть пары `color/colour`, `honor/honour`, `center/centre`, `defense/defence`, `theater/theatre`, `license/licence`, `armor/armour`, `flavor/flavour`, `labor/labour`, `favor/favour`, `humor/humour`. Игрок, ждущий американское написание, может не угадать британское. Варианты: оставить оба или оставить в ответах только американское (догадками остаются оба).
- **Взрослые темы, которые я не блокировал.** Алкоголь, табак, азартные игры, оружие и тюрьма: EN `beer`, `vodka`, `whiskey`, `alcohol`, `tobacco`, `casino`, `poker`, `weapon`, `pistol`, `missile`, `bullet`, `sword`, `prison`; TR `rakı`, `bira`, `içki`, `şarap`, `sigara`, `kumar`, `tüfek`, `füze`, `hırsız`, `mezar`, `cenaze`, `domuz`. Это обычные слова, но для семейной игры их можно убрать.
- **Турецкий циркумфлекс.** Ввод `â/î/û` складывается в `a/i/u`, это часть нормализатора. Леммы, записанные в Zemberek с циркумфлексом, в ответы не идут (389 слов), и среди них частые `hikaye`, `rüzgar`, `şikayet`, `dükkan`. Их можно вернуть через allowlist; `kağıt` выпал по другой причине (у него есть и прилагательное).
- **Турецкие догадки.** Кроме лемм, это 13 377 словоформ из wordfreq, которые разбирает Zemberek (`evler`, `kitabı`). Без них угадывать по-турецки было бы тесно, но их можно убрать и оставить только леммы.
- **Заимствования и редкие слова в турецких ответах:** `mode`, `toto`, `soma`, `bent` (плотина), `klor`, `dart`, `tayt`. Кандидатов больше, чем мест, их легко заменить.
- **Английские слова с несколькими значениями,** где существительное не самое частое (`left`, `will`, `plus`), остались, потому что в WordNet существительное у них преобладает.
- **500 ответов на длину** — как у русского V2. Кандидатов хватает и на больший пул: EN 864–1 236, TR 697–1 279.

### Вопросы владельцу

1. Английские пары написаний — оставить оба варианта или только американский? Пока оставил оба.
2. Слова про алкоголь, табак, азартные игры и оружие (списки выше) — оставить в ответах? Пока оставил.
3. Турецкие слова с циркумфлексом (`hikaye`, `rüzgar`, `şikayet`, `dükkan`, …) — вернуть частые в ответы через allowlist? Пока исключены.
4. Турецкие догадки — оставить словоформы из wordfreq или только леммы? Пока оставил словоформы.
5. Размер пула — 500 ответов на длину или больше? Пока 500.
