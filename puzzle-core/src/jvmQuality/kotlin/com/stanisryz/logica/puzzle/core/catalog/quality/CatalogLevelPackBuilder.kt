package com.stanisryz.logica.puzzle.core.catalog.quality

import com.stanisryz.logica.puzzle.core.balance.BalanceGeneratorV1
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuEngine
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuRules
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogContentVariant
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackSource
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.crowns.CrownsGeneratorV1
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV1
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV3
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV4
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPictureLibraryV3
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1
import com.stanisryz.logica.puzzle.core.sudoku.BinarySudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuSelectorV1
import com.stanisryz.logica.puzzle.core.word.WordCatalogContent
import com.stanisryz.logica.puzzle.core.word.WordLanguage
import com.stanisryz.logica.puzzle.core.word.WordLexiconV2
import com.stanisryz.logica.puzzle.core.word.WordLexiconV3
import com.stanisryz.logica.puzzle.core.word.WordLexiconV4
import com.stanisryz.logica.puzzle.core.word.WordLexiconV5
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.stream.Collectors
import kotlin.system.exitProcess

/**
 * Developer-only offline builder for the frozen Catalog Level Packs. It never runs on a device: it
 * reuses the shipped generators, solvers, datasets, and lexicons to freeze one accepted seed per
 * content slot and verifies separately generated candidate bytes against the read-only release.
 *
 * Usage: `./gradlew :puzzle-core:buildCatalogLevelPacks [-PlevelPackGames=balance,crowns]
 * [-PlevelPackSlots=10000] [-PlevelPackCreate=true]`. `levelPackCreate` freezes the buckets of a
 * newly added game once: it writes only buckets that do not exist yet and records their checksums;
 * every existing bucket is still only verified.
 */
object CatalogLevelPackBuilder {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Expected <puzzle-data-dir> <games> [slots]." }
        val puzzleDataDirectory = File(args[0])
        require(puzzleDataDirectory.isDirectory) {
            "Puzzle data directory ${puzzleDataDirectory.path} does not exist."
        }
        val requestedGames = parseTargets(args[1])
        val slots = args.getOrNull(2)?.toIntOrNull() ?: CatalogLevelPacks.SLOTS_PER_BUCKET
        require(slots in 1..CatalogLevelPacks.SLOTS_PER_BUCKET) { "Slot count must be within 1..10000." }
        createMissing = args.getOrNull(3).toBoolean()
        if (!createMissing) CatalogLevelPackIntegrity.verify(puzzleDataDirectory)

        println("Building Catalog Level Packs: $slots slots per bucket, games=${requestedGames.joinToString { it.label }}")
        val startedAt = System.nanoTime()
        var failures = 0
        requestedGames.forEach { target ->
            Difficulty.entries.forEach { difficulty ->
                val bucketStartedAt = System.nanoTime()
                val outcome =
                    runCatching { buildBucket(puzzleDataDirectory, target, difficulty, slots) }
                outcome
                    .onSuccess { file ->
                        println(
                            "  ${target.label}/${difficulty.name}: ${file.name} " +
                                "(${file.length()} bytes, ${elapsedSeconds(bucketStartedAt)}s)",
                        )
                    }.onFailure { error ->
                        failures++
                        System.err.println("  ${target.label}/${difficulty.name} FAILED: ${error.message}")
                    }
            }
        }
        println("Finished in ${elapsedSeconds(startedAt)}s.")
        if (failures > 0) exitProcess(1)
        if (createMissing) CatalogLevelPackIntegrity.verify(puzzleDataDirectory)
    }

    /** Set by `levelPackCreate`: a missing bucket of a new game may be written once. */
    private var createMissing = false

    /**
     * One frozen bucket family: a game in one pack version, or one of its content variants (Word's
     * `word_ru`, `word_en`, `word_tr`).
     */
    internal data class BucketTarget(
        val puzzleType: PuzzleType,
        val variant: CatalogContentVariant? = null,
        val packVersion: CatalogLevelPackVersion = CatalogLevelPackVersion.V1,
    ) {
        val label: String
            get() =
                puzzleType.name + variant?.let { "_${it.key.uppercase()}" }.orEmpty() +
                    (if (packVersion == CatalogLevelPackVersion.V1) "" else "_V${packVersion.value}")
    }

    /** Every bucket family of Level Pack V1: each game plus Word's language variants. */
    internal val V1_TARGETS: List<BucketTarget> =
        CatalogLevelPacks.PUZZLE_TYPES.map { BucketTarget(it) } +
            listOf(
                BucketTarget(PuzzleType.WORD, WordCatalogContent.RUSSIAN_VARIANT),
                BucketTarget(PuzzleType.WORD, WordCatalogContent.ENGLISH_VARIANT),
                BucketTarget(PuzzleType.WORD, WordCatalogContent.TURKISH_VARIANT),
            )

    /** Level Pack V2 holds the Nonogram alone (label `nonogram_v2`). */
    internal val V2_TARGETS: List<BucketTarget> = listOf(BucketTarget(PuzzleType.NONOGRAM, packVersion = CatalogLevelPackVersion.V2))

    internal val ALL_TARGETS: List<BucketTarget> = V1_TARGETS + V2_TARGETS

    private fun parseTargets(raw: String): List<BucketTarget> =
        if (raw.equals("all", ignoreCase = true)) {
            ALL_TARGETS
        } else {
            raw
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { name ->
                    ALL_TARGETS.firstOrNull { it.label.equals(name, ignoreCase = true) }
                        ?: error("Unknown Catalog bucket family '$name'.")
                }
        }

    private fun buildBucket(
        puzzleDataDirectory: File,
        target: BucketTarget,
        difficulty: Difficulty,
        slots: Int,
    ): File {
        val puzzleType = target.puzzleType
        val bucket =
            when (puzzleType) {
                PuzzleType.BALANCE -> balanceBucket(difficulty, slots)
                PuzzleType.CROWNS -> crownsBucket(difficulty, slots)
                PuzzleType.WORD -> wordBucket(difficulty, slots, target.variant)
                PuzzleType.SUDOKU -> sudokuBucket(puzzleDataDirectory, difficulty, slots)
                PuzzleType.GAME_2048 -> game2048Bucket(difficulty, slots)
                PuzzleType.NONOGRAM ->
                    if (target.packVersion ==
                        CatalogLevelPackVersion.V2
                    ) {
                        nonogramV2Bucket(difficulty, slots)
                    } else {
                        nonogramBucket(difficulty, slots)
                    }
                PuzzleType.BLOCK_SUDOKU -> blockSudokuBucket(difficulty, slots)
                else -> error("$puzzleType has no Catalog level pack.")
            }
        check(bucket.seeds.size == slots) { "Expected $slots accepted seeds, found ${bucket.seeds.size}." }
        bucket.uniqueness?.let { summary ->
            println(
                "    uniqueness: total=${summary.totalSlots}, unique=${summary.uniqueContent}, " +
                    "repeated=${summary.repeatedSlots} (${summary.repeatedRatioPercent()}%), " +
                    "first repeated slot=${summary.firstRepeatedSlot ?: "none"}",
            )
        }
        return write(puzzleDataDirectory, target, difficulty, bucket)
    }

    private fun write(
        puzzleDataDirectory: File,
        bucketTarget: BucketTarget,
        difficulty: Difficulty,
        bucket: Bucket,
    ): File {
        val puzzleType = bucketTarget.puzzleType
        val variant = bucketTarget.variant
        val packVersion = bucketTarget.packVersion
        val target =
            File(
                puzzleDataDirectory,
                CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty, variant),
            )
        val output = ByteArrayOutputStream(CatalogLevelPackFormat.HEADER_SIZE + bucket.seeds.size * CatalogLevelPackFormat.RECORD_SIZE)
        output.use {
            output.write(
                CatalogLevelPackFormat.header(
                    packVersion = packVersion,
                    puzzleType = puzzleType,
                    difficulty = difficulty,
                    recordCount = bucket.seeds.size,
                    generatorVersion = bucket.generatorVersion,
                ),
            )
            bucket.seeds.forEach { seed -> output.write(CatalogLevelPackFormat.record(PuzzleSeed(seed))) }
        }
        val candidate = output.toByteArray()
        verify(candidate, bucketTarget, difficulty, bucket)
        if (createMissing && !target.exists()) {
            target.parentFile.mkdirs()
            target.writeBytes(candidate)
            val manifest = File(puzzleDataDirectory, CatalogLevelPackIntegrity.manifestPath(packVersion))
            val relativePath =
                CatalogLevelPackFormat
                    .assetPath(
                        packVersion,
                        puzzleType,
                        difficulty,
                        variant,
                    ).removePrefix("levels/v${packVersion.value}/")
            manifest.appendText("${CatalogLevelPackIntegrity.sha256(candidate)}  $relativePath\n")
            return target
        }
        require(target.isFile) {
            "Frozen Level Pack V${packVersion.value} bucket is missing: ${target.path}. Restore the released asset instead of recreating it."
        }
        require(target.readBytes().contentEquals(candidate)) {
            "Generated content differs from frozen Level Pack V${packVersion.value} ${target.path}. Create a new pack version instead of mutating it."
        }
        return target
    }

    /** The builder validates what it produced; the runtime never repeats this work. */
    private fun verify(
        candidate: ByteArray,
        target: BucketTarget,
        difficulty: Difficulty,
        bucket: Bucket,
    ) {
        val pack =
            BinaryCatalogLevelPack(
                source =
                    object : CatalogLevelPackSource {
                        override fun open(
                            packVersion: CatalogLevelPackVersion,
                            puzzleType: PuzzleType,
                            difficulty: Difficulty,
                        ) = candidate.inputStream().takeIf { target.variant == null }

                        override fun openVariant(
                            packVersion: CatalogLevelPackVersion,
                            puzzleType: PuzzleType,
                            difficulty: Difficulty,
                            variant: CatalogContentVariant,
                        ) = candidate.inputStream().takeIf { variant == target.variant }
                    },
                expectedRecordCount = bucket.seeds.size,
            )
        val checkedSlots = listOf(1, 2, (bucket.seeds.size + 1) / 2, bucket.seeds.size).filter { it <= bucket.seeds.size }.distinct()
        checkedSlots.forEach { slot ->
            val levelId = CatalogLevelId(target.puzzleType, difficulty, CatalogLevelNumber(slot), target.packVersion)
            when (val resolved = pack.resolve(levelId, target.variant)) {
                is CatalogLevelPackResult.Failure -> error("Written bucket is unreadable: ${resolved.detail}")
                is CatalogLevelPackResult.Success -> {
                    check(resolved.value.seed.value == bucket.seeds[slot - 1]) {
                        "Slot $slot resolved to the wrong seed."
                    }
                    check(resolved.value.generatorVersion == CatalogLevelPacks.generatorVersionFor(levelId, bucket.generatorVersion)) {
                        "Slot $slot resolved to the wrong generator version."
                    }
                }
            }
        }
    }

    // ---- Per-game frozen content -------------------------------------------------------------

    /** Balance reuses Generator V1 unchanged: only seeds it already accepts enter the pack. */
    private fun balanceBucket(
        difficulty: Difficulty,
        slots: Int,
    ): Bucket {
        val result =
            searchAcceptedSeeds(slots) { seed ->
                runCatching {
                    val puzzle = BalanceGeneratorV1().generate(PuzzleSeed(seed), difficulty)
                    puzzle.size.toString() +
                        puzzle.fixedClues.entries
                            .sortedWith(compareBy({ it.key.row }, { it.key.column }))
                            .joinToString(",") { "${it.key.row}:${it.key.column}=${it.value}" }
                }.getOrNull()
            }
        return Bucket(
            seeds = result.seeds,
            generatorVersion = BalanceGeneratorV1().version,
            uniqueness = result.uniqueness,
        )
    }

    /** Crowns reuses Generator V1, its solver, and its validator; rejects never reach the asset. */
    private fun crownsBucket(
        difficulty: Difficulty,
        slots: Int,
    ): Bucket {
        val result =
            searchAcceptedSeeds(slots) { seed ->
                runCatching {
                    val puzzle = CrownsGeneratorV1().generate(PuzzleSeed(seed), difficulty)
                    puzzle.regionAssignments.entries
                        .sortedWith(compareBy({ it.key.row }, { it.key.column }))
                        .joinToString(",") { it.value.value.toString() }
                }.getOrNull()
            }
        return Bucket(
            seeds = result.seeds,
            generatorVersion = CrownsGeneratorV1().version,
            uniqueness = result.uniqueness,
        )
    }

    /**
     * Word keeps each bucket's frozen answer pool, the same draw as its generator: the default `word/`
     * buckets V2 (Russian before the family filter), and each language variant its language's version
     * (V5 Russian, V3 English, V4 Turkish). A seed is accepted only when it selects an answer this
     * cycle has not used yet, so every available word appears before any repetition, and repeats
     * afterwards are a deterministic continuation of the same scan.
     */
    private fun wordBucket(
        difficulty: Difficulty,
        slots: Int,
        variant: CatalogContentVariant?,
    ): Bucket {
        val language = variant?.let { key -> WordLanguage.entries.single { WordCatalogContent.variant(it) == key } }
        val generatorVersion = language?.let(WordCatalogContent::generatorVersion) ?: GeneratorVersion(2)
        val answers =
            when (language) {
                null -> WordLexiconV2.possibleAnswers
                WordLanguage.RUSSIAN -> WordLexiconV5.possibleAnswers
                WordLanguage.ENGLISH -> WordLexiconV3.possibleAnswers
                WordLanguage.TURKISH -> WordLexiconV4.possibleAnswers
            }
        val poolSize = answers.answers(difficulty).size
        check(poolSize > 0) { "The ${difficulty.name} Word V${generatorVersion.value} answer pool is empty." }
        val used = HashSet<Int>(poolSize * 2)
        val seeds = ArrayList<Long>(slots)
        var candidate = FIRST_SEED
        while (seeds.size < slots) {
            val index = PuzzleRandomV1(PuzzleSeed(candidate)).nextInt(poolSize)
            if (used.add(index)) {
                seeds += candidate
                if (used.size == poolSize) used.clear()
            }
            candidate++
        }
        return Bucket(seeds, generatorVersion)
    }

    /**
     * Sudoku reuses Dataset V1 unchanged. The pack is a frozen permutation of the frozen selector,
     * so public level order is independent of the dataset's technical fingerprint ordering while a
     * level slot always picks exactly the same record.
     */
    private fun sudokuBucket(
        puzzleDataDirectory: File,
        difficulty: Difficulty,
        slots: Int,
    ): Bucket {
        val sudokuDifficulty = SudokuDifficulty.valueOf(difficulty.name)
        val dataset =
            BinarySudokuDataset { version, bucketDifficulty ->
                File(puzzleDataDirectory, "sudoku/v${version.value}/${bucketDifficulty.name.lowercase()}.sdk")
                    .takeIf(File::isFile)
                    ?.readBytes()
            }
        val recordCount =
            when (val count = dataset.availableCount(SudokuDatasetVersion.V1, sudokuDifficulty)) {
                is SudokuDatasetResult.Failure -> error("Sudoku dataset unavailable: ${count.detail}")
                is SudokuDatasetResult.Success -> count.value
            }
        check(recordCount >= slots) { "Sudoku ${difficulty.name} has $recordCount records for $slots slots." }

        val used = HashSet<Int>(recordCount * 2)
        val seeds = ArrayList<Long>(slots)
        var candidate = FIRST_SEED
        while (seeds.size < slots) {
            val index = SudokuSelectorV1.index(SudokuDatasetVersion.V1, sudokuDifficulty, candidate, recordCount)
            if (used.add(index)) {
                seeds += candidate
                if (used.size == recordCount) used.clear()
            }
            candidate++
        }
        // Spot-check that the frozen seeds really do reselect distinct dataset records.
        listOf(0, slots / 2, slots - 1).distinct().forEach { slot ->
            val selected = dataset.selectPuzzle(SudokuDatasetVersion.V1, sudokuDifficulty, seeds[slot])
            check(selected is SudokuDatasetResult.Success) { "Frozen Sudoku slot ${slot + 1} does not resolve." }
        }
        return Bucket(seeds, GeneratorVersion(1))
    }

    /**
     * 2048 needs no search — every seed is playable — but its slots are still frozen so a level's
     * initial state and its whole deterministic spawn sequence are fixed forever. A 4x4 board only
     * has a few hundred possible openings, so the distinct thing about a level is its seed: two
     * levels sharing an opening still diverge immediately afterwards.
     */
    private fun game2048Bucket(
        difficulty: Difficulty,
        slots: Int,
    ): Bucket {
        val stream =
            PuzzleRandomV1(
                PuzzleSeed(GAME_2048_STREAM_SEED + CatalogLevelPackFormat.difficultyCode(difficulty) - 1L),
            )
        val seeds = LinkedHashSet<Long>(slots * 2)
        while (seeds.size < slots) seeds += stream.nextLong()
        // The frozen seeds have to produce a playable opening under the shipped engine.
        val sample = seeds.first()
        check(Game2048Engine(Game2048PuzzleId(PuzzleSeed(sample), difficulty, Game2048GeneratorVersion.V2)).start().score == 0L) {
            "The frozen 2048 opening is not a fresh board."
        }
        return Bucket(seeds.toList(), GeneratorVersion(Game2048GeneratorVersion.V2.value))
    }

    /**
     * Block Sudoku, like 2048, needs no search: every seed deals a playable game. Its slots freeze the
     * seed so a level's whole deal sequence is fixed forever under Block Sudoku Rules V1.
     */
    private fun blockSudokuBucket(
        difficulty: Difficulty,
        slots: Int,
    ): Bucket {
        val stream = PuzzleRandomV1(PuzzleSeed(BLOCK_SUDOKU_STREAM_SEED + CatalogLevelPackFormat.difficultyCode(difficulty) - 1L))
        val seeds = LinkedHashSet<Long>(slots * 2)
        while (seeds.size < slots) seeds += stream.nextLong()
        check(BlockSudokuEngine(PuzzleSeed(seeds.first()), difficulty).start().tray.all { it != null }) {
            "The frozen Block Sudoku opening has no full tray."
        }
        return Bucket(seeds.toList(), BlockSudokuRules.VERSION)
    }

    /** Nonogram reuses Generator V1: only seeds whose picture line logic completes enter the pack. */
    private fun nonogramBucket(
        difficulty: Difficulty,
        slots: Int,
    ): Bucket {
        val result =
            searchAcceptedSeeds(slots) { seed ->
                runCatching {
                    val puzzle = NonogramGeneratorV1().generate(PuzzleSeed(seed), difficulty)
                    puzzle.size.toString() + ":" + puzzle.solution.joinToString("") { if (it) "1" else "0" }
                }.getOrNull()
            }
        return Bucket(
            seeds = result.seeds,
            generatorVersion = NonogramGeneratorV1().version,
            uniqueness = result.uniqueness,
        )
    }

    /**
     * Nonogram Level Pack V2 alternates two kinds of level by slot parity. An odd slot `s` holds a
     * picture index of Generator V3: `order[((s + 1) / 2 - 1) mod N]`, where `order` is one shuffle of
     * the difficulty's N library pictures by the project random stream, repeated unchanged, so any N
     * consecutive picture levels show every picture once. An even slot `2k` holds the k-th seed
     * Generator V4 accepts when seeds are tried upwards from 1. The bucket header names Generator V3.
     */
    private fun nonogramV2Bucket(
        difficulty: Difficulty,
        slots: Int,
    ): Bucket {
        val pictures = NonogramPictureLibraryV3.count(difficulty)
        val order = IntArray(pictures) { it }
        val random = PuzzleRandomV1(PuzzleSeed(NONOGRAM_V2_ORDER_SEED + CatalogLevelPackFormat.difficultyCode(difficulty) - 1L))
        for (last in order.lastIndex downTo 1) {
            val pick = random.nextInt(last + 1)
            val kept = order[last]
            order[last] = order[pick]
            order[pick] = kept
        }
        val symmetric = NonogramGeneratorV4()
        var nextSymmetricSeed = FIRST_SEED
        var rejected = 0
        val seeds =
            (1..slots).map { slot ->
                if (slot % 2 == 1) {
                    order[((slot + 1) / 2 - 1) % pictures].toLong()
                } else {
                    while (runCatching { symmetric.generate(PuzzleSeed(nextSymmetricSeed), difficulty) }.isFailure) {
                        rejected++
                        nextSymmetricSeed++
                    }
                    nextSymmetricSeed++.also { check(it > 0) }
                }
            }
        // Every picture index has to build under the shipped Generator V3.
        order.forEach { index -> NonogramGeneratorV3().generate(PuzzleSeed(index.toLong()), difficulty) }
        println(
            "    pictures: $pictures (one cycle every ${pictures * 2} levels), symmetric seeds 1..${nextSymmetricSeed - 1}, " +
                "rejected $rejected",
        )
        return Bucket(seeds, CatalogLevelPacks.NONOGRAM_V2_PICTURES)
    }

    // ---- Shared deterministic seed search ------------------------------------------------------

    /**
     * Scans ascending seeds and keeps the ones the generator accepts, skipping failures and content
     * the bucket already contains. Batches are evaluated in parallel but always merged in seed order,
     * so the produced pack is a pure function of the generators.
     *
     * Uniqueness is best effort: a small board such as EASY Balance simply has fewer than 10 000
     * distinct puzzles, so once [DUPLICATE_TOLERANCE] candidates in a row are all repeats the scan
     * accepts repeats from there on instead of searching a content space that does not exist.
     */
    private fun searchAcceptedSeeds(
        slots: Int,
        fingerprint: (Long) -> String?,
    ): SeedSearchResult {
        val accepted = ArrayList<Long>(slots)
        val seen = HashSet<String>(slots * 2)
        var nextSeed = FIRST_SEED
        var consecutiveDuplicates = 0
        var contentExhausted = false
        var firstRepeatedSlot: Int? = null
        while (accepted.size < slots) {
            val batch = (0 until BATCH_SIZE).map { offset -> nextSeed + offset }
            nextSeed += BATCH_SIZE
            val evaluated =
                batch
                    .parallelStream()
                    .map { seed -> seed to fingerprint(seed) }
                    .collect(Collectors.toList())
            for ((seed, content) in evaluated) {
                if (accepted.size == slots) break
                if (content == null) continue
                val isNew = seen.add(content)
                when {
                    contentExhausted || isNew -> {
                        if (!isNew && firstRepeatedSlot == null) firstRepeatedSlot = accepted.size + 1
                        accepted += seed
                        consecutiveDuplicates = 0
                    }
                    ++consecutiveDuplicates >= DUPLICATE_TOLERANCE -> {
                        contentExhausted = true
                        if (firstRepeatedSlot == null) firstRepeatedSlot = accepted.size + 1
                        accepted += seed
                    }
                }
            }
        }
        if (contentExhausted) {
            println("    distinct content exhausted at ${seen.size} puzzles; later slots repeat content")
        }
        return SeedSearchResult(
            seeds = accepted,
            uniqueness =
                UniquenessSummary(
                    totalSlots = accepted.size,
                    uniqueContent = seen.size.coerceAtMost(accepted.size),
                    firstRepeatedSlot = firstRepeatedSlot,
                ),
        )
    }

    private fun elapsedSeconds(startedAt: Long): String = "%.1f".format((System.nanoTime() - startedAt) / 1_000_000_000.0)

    private data class Bucket(
        val seeds: List<Long>,
        val generatorVersion: GeneratorVersion,
        val uniqueness: UniquenessSummary? = null,
    )

    private data class SeedSearchResult(
        val seeds: List<Long>,
        val uniqueness: UniquenessSummary,
    )

    private data class UniquenessSummary(
        val totalSlots: Int,
        val uniqueContent: Int,
        val firstRepeatedSlot: Int?,
    ) {
        val repeatedSlots: Int get() = totalSlots - uniqueContent

        fun repeatedRatioPercent(): String = if (totalSlots == 0) "0.00" else "%.2f".format(repeatedSlots * 100.0 / totalSlots)
    }

    private const val FIRST_SEED = 1L
    private const val BATCH_SIZE = 512

    /** Consecutive repeats that mean a bucket's distinct content space is effectively used up. */
    private const val DUPLICATE_TOLERANCE = 24
    private const val GAME_2048_STREAM_SEED = 0x32303438L
    private const val BLOCK_SUDOKU_STREAM_SEED = 0x424c4f434bL
    private const val NONOGRAM_V2_ORDER_SEED = 0x4e4f4e4f56324cL
}
