# Тексты и настройки для консоли Яндекс Игр

Тексты готовы к вставке. В них нет внешних ссылок и упоминаний других платформ. Название везде совпадает с тем, что игрок видит в игре (загрузчик, `<title>`, шеринг).

## RU

**Название:** Головоломки

**Короткое описание** (75 символов):
Семь головоломок в одной игре: судоку, нонограммы, 2048, слова и задачи дня

**Об игре:**
Семь логических игр в одном месте — для коротких перерывов и долгих вечеров.

• Баланс — расставьте чёрные и белые фишки поровну, без трёх одинаковых подряд.
• Короны — по одной короне в каждой строке, столбце и цветной области.
• Слова — угадайте слово за шесть попыток.
• Судоку — классика на четырёх уровнях сложности.
• 2048 — соединяйте плитки и наберите нужный счёт.
• Нонограмма — откройте по цифрам скрытую картинку.
• Блок-судоку — заполняйте строки, столбцы и квадраты фигурами.

В каждой игре четыре сложности и тысячи уровней. Каждый день — новые задачи дня: держите серию, а пропущенные дни можно пройти в архиве. Звёзды за уровни «Эксперт» идут в турнир недели: лучшие игроки получают призовые кристаллы. Кристаллы, жизни и подсказки — только внутриигровые. Ежедневные задания, подарок за вход, достижения, тёмная тема. Прогресс сохраняется в облаке Яндекса.

**Как играть:**
Выберите игру и сложность — уровень откроется сразу. В каждой игре есть «Как играть?» с коротким обучением и «?» с правилами прямо во время партии. В Балансе, Коронах, Судоку и Нонограмме ошибки помечаются сразу, три ошибки заканчивают попытку, а неудачная попытка стоит одну жизнь. Жизни восстанавливаются со временем. Подсказка открывает одну верную клетку или букву. На компьютере 2048, Судоку и Слова управляются и с клавиатуры.

**Ключевые слова:** головоломки, логические игры, судоку, нонограмма, японский кроссворд, 2048, слова, блок-судоку, задача дня, мозг

## EN

**Name:** Puzzles

**Short description** (78 characters):
Seven puzzles in one game: Sudoku, nonograms, 2048, words and daily challenges

**About the game:**
Seven logic games in one place — for a short break or a long evening.

• Balance — place black and white pieces evenly, never three of a kind in a row.
• Crowns — one crown in every row, column and coloured region.
• Words — guess the word in six tries.
• Sudoku — the classic, at four difficulty levels.
• 2048 — merge tiles and reach the target score.
• Nonogram — reveal the hidden picture from the number clues.
• Block Sudoku — fill rows, columns and squares with shapes.

Every game has four difficulties and thousands of levels. Each day brings new daily puzzles: keep your streak going, and play missed days in the archive. Stars from Expert levels count towards the weekly tournament, where the best players win prize gems. Gems, lives and hints are in-game only. Daily quests, a login gift, achievements and a dark theme. Progress is saved in the Yandex cloud.

**How to play:**
Pick a game and a difficulty — the level opens at once. Every game has "How to play?" with a short tutorial and a "?" with the rules during play. In Balance, Crowns, Sudoku and Nonogram mistakes show up right away, three mistakes end the attempt, and a failed attempt costs one life. Lives come back over time. A hint opens one correct cell or letter. On a computer, 2048, Sudoku and Words also work with the keyboard.

**Keywords:** puzzles, logic games, sudoku, nonogram, picross, 2048, word game, block sudoku, daily puzzle, brain games

## TR

**Ad:** Bulmacalar

**Kısa açıklama** (74 karakter):
Tek oyunda yedi bulmaca: Sudoku, nonogram, 2048, kelime ve günlük görevler

**Oyun hakkında:**
Yedi mantık oyunu tek yerde — kısa molalar ve uzun akşamlar için.

• Denge — siyah ve beyaz taşları eşit yerleştir, aynı renkten üçü yan yana gelmesin.
• Taçlar — her satırda, sütunda ve renkli bölgede bir taç.
• Kelime — kelimeyi altı denemede bul.
• Sudoku — dört zorluk seviyesinde klasik.
• 2048 — karoları birleştir ve hedef puana ulaş.
• Nonogram — sayılardan gizli resmi ortaya çıkar.
• Blok Sudoku — satırları, sütunları ve kareleri şekillerle doldur.

Her oyunda dört zorluk ve binlerce seviye var. Her gün yeni günün bulmacaları: serini koru, kaçırdığın günleri arşivde oyna. Uzman seviyelerinden gelen yıldızlar haftalık turnuvaya sayılır; en iyi oyuncular ödül elması kazanır. Elmaslar, canlar ve ipuçları yalnızca oyun içidir. Günlük görevler, giriş hediyesi, başarılar ve koyu tema. İlerleme Yandex bulutunda saklanır.

**Nasıl oynanır:**
Bir oyun ve zorluk seç — seviye hemen açılır. Her oyunda kısa bir öğreticiyle «Nasıl oynanır?» ve oyun sırasında kuralları gösteren «?» var. Denge, Taçlar, Sudoku ve Nonogram'da hatalar hemen işaretlenir, üç hata denemeyi bitirir ve başarısız deneme bir cana mal olur. Canlar zamanla yenilenir. İpucu doğru bir hücreyi ya da harfi açar. Bilgisayarda 2048, Sudoku ve Kelime klavyeyle de oynanır.

**Anahtar kelimeler:** bulmaca, mantık oyunları, sudoku, nonogram, 2048, kelime oyunu, blok sudoku, günün bulmacası, zeka oyunları

## Что владелец делает в консоли

### Покупки (раздел «Покупки» / каталог товаров)

Id в консоли должны совпадать с кодом (`WebPaidProduct`, `web-app/.../WebPayments.kt`). Цены задаются в консоли; игра берёт их из `payments.getCatalog()` и сама цен не знает.

| Id | Тип | Что даёт | Как игра с ним обходится |
|---|---|---|---|
| `gems_50` | расходуемый | 50 кристаллов | начисляет и вызывает `consumePurchase` |
| `gems_150` | расходуемый | 150 кристаллов | то же |
| `gems_500` | расходуемый | 500 кристаллов | то же |
| `starter_pack` | расходуемый, разовый по смыслу | 100 кристаллов, 5 подсказок и все недостающие жизни | начисляет один раз; после покупки карточка пропадает |
| `no_ads` | постоянный (не расходуется) | отключает межстраничную рекламу и стики-баннер навсегда; реклама за награду остаётся по желанию игрока | никогда не вызывает `consumePurchase`; каждый запуск видит покупку в `getPurchases()` |

Названия товаров (ru / en / tr): «50 кристаллов» / «50 gems» / «50 elmas», «Стартовый набор» / «Starter pack» / «Başlangıç paketi», «Без рекламы» / «No ads» / «Reklamsız».

### Лидерборды

Все — числовые, целые, по убыванию (больше — лучше). Технические имена — только латиница и цифры, как в коде.

| Техническое имя | Что в нём | Название (ru / en / tr) |
|---|---|---|
| `solved` (основной) | всего решённых задач | «Решено задач» / «Puzzles solved» / «Çözülen bulmacalar» |
| `ratingBalance` | рейтинг Баланса | «Баланс» / «Balance» / «Denge» |
| `ratingCrowns` | рейтинг Корон | «Короны» / «Crowns» / «Taçlar» |
| `ratingWord` | рейтинг Слов | «Слова» / «Words» / «Kelime» |
| `ratingSudoku` | рейтинг Судоку | «Судоку» / «Sudoku» / «Sudoku» |
| `ratingNonogram` | рейтинг Нонограммы | «Нонограмма» / «Nonogram» / «Nonogram» |
| `ratingBlockSudoku` | рейтинг Блок-судоку | «Блок-судоку» / «Block Sudoku» / «Blok Sudoku» |
| `best2048` | лучший счёт одной партии 2048 | «2048: лучший счёт» / «2048: best score» / «2048: en iyi skor» |
| `weeklyStarsA` | турнир чётных недель | «Турнир недели» / «Weekly tournament» / «Haftalık turnuva» |
| `weeklyStarsB` | турнир нечётных недель | то же |

У `weeklyStarsA/B` в таблице будут большие числа (`неделя × 1 000 000 + звёзды`) — так задумано: игра сама показывает игроку только звёзды. Подробно — в отчёте 11.5, раздел 5.

### Облачные сохранения

**Требование 1.11: в черновике обязательно отметить, что игра использует облачные сохранения** (архитектор, финальная проверка). Сохранения идут через `player.getData` / `setData`. Игра пишет один ключ `logica_unified_save_v1` и читает старые ключи для миграции. Гостевой игрок играет с локальным сохранением.

### Реклама

- **За награду** (rewarded): +1 кристалл и +1 жизнь в магазине, +1 жизнь в окне «нет жизней» и на карточке результата, второй шанс, отмена хода 2048, сохранение серии, открытие дня архива. Каждая кнопка подписана «за рекламу» и говорит, что даёт.
- **Межстраничная** (interstitial): только по нажатию игрока при выходе из игры (следующий уровень, назад, заново, к сложности, к играм), не чаще раза в 90 секунд и не в первые 2 минуты.
- **Стики-баннер:** включить в консоли (раздел «Реклама» → sticky-баннер). Игра управляет его показом через SDK и прячет его после покупки «Без рекламы».

### Возрастной рейтинг

Предлагаю **0+**: насилия, страшных сцен, азарта на реальные деньги и чатов нет. Покупки и реклама на рейтинг не влияют, но анкета консоли может спросить о них — ответ «есть внутриигровые покупки» и «есть реклама».

### Категории

Основная — «Головоломки». Дополнительные — из списка консоли, что ближе: «Логические», «Слова», «Настольные» (если такие есть в списке на момент отправки).

### Графика от владельца

- **Иконка** 512×512 PNG — можно взять `art/app_icon/source.png` (тот же кристалл, что на иконке Android и в фавиконке), увеличив поле вокруг.
- **Обложка** 800×470 — нужна новая; подойдут сцены сложностей (`art/difficulty/*.png`) с названием «Головоломки» поверх.
- **Скриншоты** — из `tasksandreports/reports/img/stage-11-6/` (сводные листы; для консоли снять отдельные кадры тем же скриптом) и `img/stage-11-5/` (турнир). Для каждого языка нужны свои: в игре текст на языке игрока.
