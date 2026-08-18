package com.stanisryz.logica.puzzle.core.catalog.quality

import com.stanisryz.logica.puzzle.core.balance.BalanceGeneratorV1
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
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
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1
import com.stanisryz.logica.puzzle.core.sudoku.BinarySudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuSelectorV1
import com.stanisryz.logica.puzzle.core.word.WordLexiconV2
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
 * [-PlevelPackSlots=10000]`.
 */
object CatalogLevelPackBuilder {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Expected <assets-dir> <games> [slots]." }
        val assetsDirectory = File(args[0])
        require(assetsDirectory.isDirectory) { "Assets directory ${assetsDirectory.path} does not exist." }
        val requestedGames = parseGames(args[1])
        val slots = args.getOrNull(2)?.toIntOrNull() ?: CatalogLevelPacks.SLOTS_PER_BUCKET
        require(slots in 1..CatalogLevelPacks.SLOTS_PER_BUCKET) { "Slot count must be within 1..10000." }
        CatalogLevelPackIntegrity.verify(assetsDirectory)

        println("Building Catalog Level Pack V1: $slots slots per bucket, games=${requestedGames.joinToString()}")
        val startedAt = System.nanoTime()
        var failures = 0
        requestedGames.forEach { puzzleType ->
            Difficulty.entries.forEach { difficulty ->
                val bucketStartedAt = System.nanoTime()
                val outcome =
                    runCatching { buildBucket(assetsDirectory, puzzleType, difficulty, slots) }
                outcome
                    .onSuccess { file ->
                        println(
                            "  ${puzzleType.name}/${difficulty.name}: ${file.name} " +
                                "(${file.length()} bytes, ${elapsedSeconds(bucketStartedAt)}s)",
                        )
                    }.onFailure { error ->
                        failures++
                        System.err.println("  ${puzzleType.name}/${difficulty.name} FAILED: ${error.message}")
                    }
            }
        }
        println("Finished in ${elapsedSeconds(startedAt)}s.")
        if (failures > 0) exitProcess(1)
    }

    private fun parseGames(raw: String): List<PuzzleType> =
        if (raw.equals("all", ignoreCase = true)) {
            CatalogLevelPacks.PUZZLE_TYPES
        } else {
            raw
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { name ->
                    CatalogLevelPacks.PUZZLE_TYPES.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        ?: error("Unknown Catalog game '$name'.")
                }
        }

    private fun buildBucket(
        assetsDirectory: File,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        slots: Int,
    ): File {
        val bucket =
            when (puzzleType) {
                PuzzleType.BALANCE -> balanceBucket(difficulty, slots)
                PuzzleType.CROWNS -> crownsBucket(difficulty, slots)
                PuzzleType.WORD -> wordBucket(difficulty, slots)
                PuzzleType.SUDOKU -> sudokuBucket(assetsDirectory, difficulty, slots)
                PuzzleType.GAME_2048 -> game2048Bucket(difficulty, slots)
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
        return write(assetsDirectory, puzzleType, difficulty, bucket)
    }

    private fun write(
        assetsDirectory: File,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        bucket: Bucket,
    ): File {
        val target =
            File(
                assetsDirectory,
                CatalogLevelPackFormat.assetPath(CatalogLevelPackVersion.V1, puzzleType, difficulty),
            )
        val output = ByteArrayOutputStream(CatalogLevelPackFormat.HEADER_SIZE + bucket.seeds.size * CatalogLevelPackFormat.RECORD_SIZE)
        output.use {
            output.write(
                CatalogLevelPackFormat.header(
                    packVersion = CatalogLevelPackVersion.V1,
                    puzzleType = puzzleType,
                    difficulty = difficulty,
                    recordCount = bucket.seeds.size,
                    generatorVersion = bucket.generatorVersion,
                ),
            )
            bucket.seeds.forEach { seed -> output.write(CatalogLevelPackFormat.record(PuzzleSeed(seed))) }
        }
        val candidate = output.toByteArray()
        verify(candidate, puzzleType, difficulty, bucket)
        require(target.isFile) {
            "Frozen Level Pack V1 bucket is missing: ${target.path}. Restore the released asset instead of recreating V1."
        }
        require(target.readBytes().contentEquals(candidate)) {
            "Generated content differs from frozen Level Pack V1 ${target.path}. Create a new pack version instead of mutating V1."
        }
        return target
    }

    /** The builder validates what it produced; the runtime never repeats this work. */
    private fun verify(
        candidate: ByteArray,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        bucket: Bucket,
    ) {
        val pack =
            BinaryCatalogLevelPack(
                source = { _, _, _ -> candidate.inputStream() },
                expectedRecordCount = bucket.seeds.size,
            )
        val checkedSlots = listOf(1, (bucket.seeds.size + 1) / 2, bucket.seeds.size).distinct()
        checkedSlots.forEach { slot ->
            val levelId = CatalogLevelId(puzzleType, difficulty, CatalogLevelNumber(slot))
            when (val resolved = pack.resolve(levelId)) {
                is CatalogLevelPackResult.Failure -> error("Written bucket is unreadable: ${resolved.detail}")
                is CatalogLevelPackResult.Success -> {
                    check(resolved.value.seed.value == bucket.seeds[slot - 1]) {
                        "Slot $slot resolved to the wrong seed."
                    }
                    check(resolved.value.generatorVersion == bucket.generatorVersion) {
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
     * Word V2 keeps its frozen answer pool: a seed is accepted only when it selects an answer this
     * cycle has not used yet, so every available word appears before any repetition, and repeats
     * afterwards are a deterministic continuation of the same scan.
     */
    private fun wordBucket(
        difficulty: Difficulty,
        slots: Int,
    ): Bucket {
        val poolSize = WordLexiconV2.possibleAnswers.answers(difficulty).size
        check(poolSize > 0) { "The ${difficulty.name} Word V2 answer pool is empty." }
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
        return Bucket(seeds, GeneratorVersion(2))
    }

    /**
     * Sudoku reuses Dataset V1 unchanged. The pack is a frozen permutation of the frozen selector,
     * so public level order is independent of the dataset's technical fingerprint ordering while a
     * level slot always picks exactly the same record.
     */
    private fun sudokuBucket(
        assetsDirectory: File,
        difficulty: Difficulty,
        slots: Int,
    ): Bucket {
        val sudokuDifficulty = SudokuDifficulty.valueOf(difficulty.name)
        val dataset =
            BinarySudokuDataset { version, bucketDifficulty ->
                File(assetsDirectory, "sudoku/v${version.value}/${bucketDifficulty.name.lowercase()}.sdk")
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
}
