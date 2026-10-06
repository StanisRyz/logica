# Этап 10.1a. Word EN/TR: решения владельца по словарям

## 1. SHA коммитов

- `4eeea74` — тематический фильтр, американское написание, турецкие слова с циркумфлексом: инструменты, ручные списки, пересобранные словари, тест.
- `d3cc9d3` — ручная вычитка обоих списков ответов целиком: добавлены слова, которые автоматический проход пропустил.
- Отчёт — следующим коммитом.

## 2. Что сделано

Паки не пишутся, интерфейс не менялся. Русские V1/V2 и замороженные паки не тронуты. Отфильтрованные слова остаются допустимыми догадками; ответов по-прежнему 500 на длину.

### Английский (V3): тематический фильтр по WordNet

- **Корни.** `lexicon/word/v3/topic_roots.txt` — 97 синсетов Open English WordNet 2023, сгруппированных по темам с комментариями (лемма и толкование). Темы: алкоголь, табак и наркотики, азартные игры, оружие, смерть, насилие и война, преступления с тюрьмой и судом, болезни, религия, политика, ругательства, секс.
- **Деревья.** Инструмент обходит всех потомков каждого корня, включая instance-гипонимы: это 4 850 синсетов. Корень, которого нет в закреплённой редакции WordNet, — ошибка.
- **Правило.** Слово убирается, если его первое существительное значение лежит в одном из деревьев. Сначала я пробовал «одно из первых двух значений», но оно убирало слишком много обычных слов: `ball`, `round`, `steel`, `rose`, `exit`, `attempt`, `hearing`. Учёт только первого значения убирает все десять контрольных слов архитектора.
- **Ручной список.** `topic_words.txt` — 47 слов, у которых тематическое значение частое, но не первое: `poker`, `lottery`, `trial`, `shot`, `vote`, `attack`, `gospel`, `verdict`, `bong`, `hooker` и др. В него вошли и находки ручной вычитки всех 2 000 ответов.

### Английский: американское написание

- `british_spellings.txt` — 79 пар «британское → американское» с комментариями по группам: `-our/-or`, `-re/-er`, `-ence/-ense`, `ae/oe → e`, прочие (`grey/gray`, `tyre/tire`, `cheque/check`, `mould/mold`, …).
- Британская форма никогда не становится ответом. Американская становится ответом по обычным правилам: сейчас в ответах `color`, `honor`, `center`, `theater`, `license`, `armor`, `flavor`, `labor`, `favor`, `humor` и ещё 10 (всего 20).
- Пары взяты из списка, а не выводятся по суффиксу: правило по суффиксу ошиблось бы на парах `four/for`, `tour/tor`, `dour/dor`. Обе формы пары инструмент проверяет по ENABLE.
- Правило `-ise/-ize` пар не дало: среди существительных 4–7 букв таких нет, только глаголы.

### Турецкий (V4)

**Тематический фильтр.** У Zemberek нет данных о значениях, поэтому `lexicon/word/v4/topic_words.txt` — ручной список из 205 слов по тем же темам.

**Переводной контроль.** Открытый путь нашёлся только частично. KeNet (Starlang Turkish WordNet) связывает турецкие синсеты с английскими (ENG31): 23 284 из 23 308 связей совпадают с id OEWN.
- Я один раз прогнал турецких кандидатов через эти связи и английские деревья корней.
- Каждое помеченное слово решал вручную:
  - убраны: `bira`, `fatiha`, `mücahit`, `sigara`, `kumar`, `mahkeme`, `parti`, …;
  - оставлены слова с редким тематическим значением: `sabah`, `akşam`, `cuma` (намаз), `oyun` (азартная игра), `kitap` (священная книга), `devlet`, `ülke`.
- Затем я прочитал все 2 000 ответов глазами и добавил пропущенное: `casino`, `bakara`, `rüşvet`, `şike`, `pusu`, `merhum`, `alevi`, `miting`, `meme`, `koma`, `kürtaj`, `pipo`, `yatağan`.
- KeNet распространяется по **GPL-3.0**, поэтому инструмент его не читает и в репозитории его нет: сохранены только мои решения. Это записано в `datasets/word/tr/PROVENANCE.md`.

**Циркумфлекс.** В `answer_allowlist.txt` добавлены 40 самых частых подходящих существительных из лексикона с циркумфлексом, в форме без него:
- ilan, adet, reklam, ilaç, hikaye, kanun, rüzgar, şikayet, kağıt, evlat;
- zeka, inkar, villa, mekan, şahıs, ilave, klavye, felaket, iflas, imkan;
- bekar, hilal, telafi, flaş, nikah, dükkan, imalat, kabus, hadise, bela;
- pilav, tabela, telaş, kase, şelale, tezgah, galaksi, klarnet, yadigar, tadilat.

Пропущены прилагательные (`tarihi`, `milli`), наречия (`hala`, `mesela`), религиозные слова (`ilahi`, `hadis`, `helal`), военные (`harekat`, `istila`) и судебные (`mahkum`, `ihlal`).

**Прочее.**
- Турецкие словоформы из wordfreq остаются догадками (решение 4), размер пула — 500 (решение 5).
- `çaykara` (название города) попало в ответы при доборе и ушло в блоклист имён и мест.

### Общее

- `lexicon_common.py`: чтение списков `<слово> <тема>` с проверкой тем по закрытому набору.
- В `import-report.json` появились образцы «убрано тематическим фильтром» и счёт по темам.
- Инструменты проверяют, что ручной список содержит только догадки и что allowlist не пересекается с темами.
- Оба инструмента детерминированы: повторный запуск даёт те же байты, это проверено дважды.
- `PROVENANCE.md` обоих языков описывают новые правила, счётчики и SHA-256 ресурсов.
- В `AGENTS.md` добавлена одна фраза в абзаце о Word V3/V4.

### Образцы

### Английский (V3)

**4 буквы.** 40 ответов: ally, beam, boat, bulb, cafe, care, cove, crew, crib, dean, dose, ease, echo, exit, flaw, flux, fold, gaze, gear, glow, golf, grin, hero, hint, jack, kiss, loft, mess, nest, oven, pill, plum, rock, saga, seat, size, slab, soda, stop, wing.

Убраны тематическим фильтром (20 самых частых): fuck (sexual), shot (weapons), vote (politics), hell (religion), pain (disease), holy (religion), wine (alcohol), beer (alcohol), jail (crime), bomb (weapons), gang (crime), jury (crime), harm (disease), sake (alcohol), rick (disease), bite (disease), weed (drugs), hood (crime), punk (crime), cult (religion).

**5 буквы.** 40 ответов: adult, alien, anime, arrow, asset, baron, cache, chaos, cloth, crest, daisy, depth, earth, genre, glass, globe, grain, grill, liver, match, metal, motto, mouth, orbit, print, promo, ridge, rover, scent, setup, shaft, sonny, steel, thing, title, trash, troop, uncle, water, yeast.

Убраны тематическим фильтром (20 самых частых): party (politics), court (crime), death (death), fight (violence), round (weapons), crime (crime), trial (crime), faith (religion), mayor (politics), drunk (alcohol), angel (religion), virus (disease), saint (religion), shell (weapons), fraud (crime), sword (weapons), devil (religion), wound (disease), fever (disease), theft (crime).

**6 буквы.** 40 ответов: accent, advice, avatar, bakery, beauty, binary, bottle, bureau, canvas, closet, danger, decade, defect, energy, finale, galaxy, horror, inning, laptop, mother, nation, nephew, number, office, offset, patrol, period, public, rapper, salary, second, slogan, spying, subway, tenant, tenure, threat, tomato, violin, wonder.

Убраны тематическим фильтром (20 самых частых): church (religion), attack (violence), battle (violence), prison (crime), injury (disease), combat (violence), weapon (weapons), killer (death), temple (religion), prayer (religion), bishop (religion), priest (religion), regime (politics), bullet (weapons), gospel (religion), runner (crime), casino (gambling), trauma (disease), parish (religion), ballot (politics).

**7 буквы.** 40 ответов: airport, attempt, battery, booking, bracket, capsule, ceramic, company, concept, cottage, crystal, daytime, evening, grandpa, housing, hygiene, interim, leather, monitor, nursing, offense, outlook, packing, panther, passing, patriot, payment, reality, refugee, renewal, replica, retreat, statute, stomach, support, tourism, tourist, turning, warming, warning.

Убраны тематическим фильтром (20 самых частых): defense (violence), disease (disease), killing (death), primary (politics), hearing (crime), suicide (death), alcohol (alcohol), senator (politics), smoking (tobacco), hanging (death), penalty (crime), assault (violence), beating (violence), illness (disease), funeral (death), arsenal (weapons), warrant (crime), custody (crime), tobacco (tobacco), vintage (alcohol).

Всего убрано темами: alcohol 74, crime 148, death 30, disease 213, drugs 18, gambling 14, politics 27, profanity 7, religion 115, sexual 15, tobacco 15, violence 21, weapons 61; британских написаний — 65.

Ручные списки: {"guess_blocklist": 0, "answer_blocklist": 240, "answer_allowlist": 0, "topic_roots": 97, "topic_words": 47, "topic_synsets": 4850, "british_spellings": 79}

### Турецкий (V4)

**4 буквы.** 40 ответов: adam, argo, ataç, azim, ağız, bant, dane, dart, data, dizi, etek, eşik, faiz, faul, foto, fuar, gale, halt, halı, heba, hile, hırs, imar, iris, kapı, kedi, köşk, maya, odak, otel, port, reji, solo, sual, sörf, tank, tarz, tost, uğur, ışık.

Убраны тематическим фильтром (20 самых частых): seks (sexual), ceza (crime), dava (crime), gazi (violence), imam (religion), vali (politics), ağrı (disease), idam (death), yara (disease), iman (religion), bira (alcohol), şeyh (religion), içki (alcohol), papa (religion), meme (sexual), füze (weapons), harp (violence), oruç (religion), felç (disease), peri (religion).

**5 буквы.** 40 ответов: ahlak, albüm, arazi, atlas, ayrım, banka, ceket, delta, duygu, espri, eylem, fikir, göbek, hücre, idare, iddia, ihmal, imkan, kimya, kredi, kural, kuşak, köpek, mayıs, panel, pasta, sabun, sakız, sebze, simge, sonuç, taviz, tıraş, yeşim, yüzde, çanta, çekim, çocuk, ıslah, şubat.

Убраны тематическим фильтром (20 самых частых): parti (politics), savaş (violence), seçim (politics), terör (violence), bakan (politics), darbe (politics), kavga (violence), tanrı (religion), bomba (weapons), işgal (violence), şükür (religion), kılıç (weapons), vefat (death), yargı (crime), alkol (alcohol), isyan (violence), tokat (violence), namaz (religion), melek (religion), şarap (alcohol).

**6 буквы.** 40 ответов: abartı, akasya, bahane, balina, başbuğ, başrol, benzin, burger, cevher, dizayn, ferman, fincan, formül, güçlük, hormon, itiraf, kasaba, kelime, konvoy, mektup, mercek, mesafe, metraj, meydan, meşale, olanak, otoyol, poster, rektör, sporcu, tablet, tampon, teftiş, teşhis, uçurum, vazife, vicdan, yağmur, çeviri, şirket.

Убраны тематическим фильтром (20 самых частых): sigara (tobacco), meclis (politics), şiddet (violence), ihanet (crime), devrim (politics), kanser (disease), cenaze (death), hırsız (crime), kongre (politics), kilise (religion), ibadet (religion), rüşvet (crime), seçmen (politics), vampir (religion), adliye (crime), tahrip (violence), merhum (death), beraat (crime), eziyet (violence), yargıç (crime).

**7 буквы.** 40 ответов: alabora, armağan, ağustos, bariyer, başkent, brifing, dağıtım, eklenti, ekvator, evlilik, felaket, filozof, hemşire, karagöz, kereste, kiremit, kontrat, kurbağa, midilli, muayene, muhabir, mülkiye, nakliye, nilüfer, ortalık, otostop, padişah, sağanak, segment, senaryo, sendika, sermaye, temenni, tencere, testere, veraset, vesayet, yönelim, öğretim, ıhlamur.

Убраны тематическим фильтром (20 самых частых): saldırı (violence), siyaset (politics), hükümet (politics), intihar (death), mahkeme (crime), iktidar (politics), intikam (violence), işkence (violence), cinayet (death), katliam (violence), cezaevi (crime), tabanca (weapons), ateşkes (violence), duruşma (crime), tarikat (religion), senatör (politics), tapınak (religion), sivilce (disease), piyango (gambling), taarruz (violence).

Всего убрано темами: alcohol 10, crime 32, death 14, disease 20, drugs 5, gambling 9, politics 17, profanity 2, religion 35, sexual 4, tobacco 5, violence 24, weapons 13.

Ручные списки: {"guess_blocklist": 0, "answer_blocklist": 326, "answer_allowlist": 126, "topic_words": 205}

Тематический фильтр иногда ловит и безобидные слова, если их первое значение в WordNet тематическое: `round` (патрон), `runner` (контрабандист), `primary` (праймериз), `hearing` (слушание дела), `pain` (симптом). Кандидатов хватает с запасом, поэтому я их не возвращал.

## 3. Новые и изменённые тесты

- `puzzle-core/src/jvmTest/.../word/WordLanguageLexiconTest.kt`:
  - **новый** `answersKeepTheFamilyFilterAndTheSpellingDecisions`. Контрольные слова архитектора (EN и TR) и британские формы из решения 1 не входят в ответы, британские формы остаются догадками, американские формы и турецкие `hikaye`, `rüzgar`, `şikayet`, `dükkan`, `kağıt` входят в ответы;
  - **изменён** `goldenAnswersForTheFirstSeeds`: перезаписана строка `GOLDEN_ANSWERS`. Причина — поменялись сами словари V3/V4: старый golden содержал `kokteyl` (коктейль), теперь он убран фильтром. V3/V4 ещё нигде не используются, менять их до заморозки допустимо.
- `GoldenDeterminismTest` (синтетический пул V3/V4) не менялся.

## 4. Команды проверки и итог

| Команда | Итог |
|---|---|
| `./gradlew ktlintCheck :puzzle-core:jvmTest :puzzle-core:verifyCatalogLevelPacks` | успешно, «Catalog Level Pack V1 integrity verified (28 buckets)» |
| `bash .claude/scripts/web-tests-node.sh` | passed=140, failed=0 |
| `python tools/word-lexicon/extract_english.py --sources …` и `extract_turkish.py`, по два прогона | одинаковые SHA-256 ресурсов и отчётов |

CI предыдущего пуша (`166a89a`, этап 10.5) — success.

## 5. Отклонения и вопросы

- **Турецкий переводной контроль — без зависимости от данных.** KeNet под GPL-3.0, поэтому я использовал его один раз как подсказку для ручного списка, а инструмент его не читает. Если владелец согласен с GPL-данными в офлайн-инструменте, проверку можно встроить автоматически.
- **Граница тем — моё решение.** Убраны партии, выборы, правительство, суд и присяжные, болезни, травмы, наркотики, ругательства и сексуальная лексика. Оставлены обычные профессии и институты: `police`/`polis`, `lawyer`/`avukat`, `doctor`, `hospital`, `soldier`, военные звания, `king`/`kral`, `ghost`, `witch`/`cadı`.
- **Английское `fairy`** убрано автоматически (WordNet относит его к духовным существам), поэтому в турецком ради единообразия убрана и `peri`.

### Вопросы владельцу

1. Встроить ли KeNet (GPL-3.0) в турецкий инструмент для автоматического переводного контроля или оставить ручной список?
2. Оставить ли в ответах военные слова (`soldier`, `tank`, звания `albay`, `yüzbaşı` и т. п.) и персонажей сказок (`ghost`, `witch`, `cadı`, `hayalet`)? Сейчас они оставлены.
3. Возвращать ли безобидные слова, которые фильтр убрал по первому значению (`round`, `runner`, `primary`, `hearing`, `pain`)? Сейчас они не возвращены.
