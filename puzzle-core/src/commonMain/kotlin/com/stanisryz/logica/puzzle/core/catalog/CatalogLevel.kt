package com.stanisryz.logica.puzzle.core.catalog

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.jvm.JvmInline

/**
 * A frozen Catalog content pack. Once released, a version's `(game, difficulty, slot) -> content`
 * mapping never changes: incompatible curation needs a new version rather than an edited V1.
 */
@JvmInline
value class CatalogLevelPackVersion(
    val value: Int,
) {
    init {
        require(value > 0) { "Level pack version must be positive." }
    }

    companion object {
        val V1 = CatalogLevelPackVersion(1)

        /** The Nonogram's second pack: real pictures and symmetric levels in turn (see [CatalogLevelPacks]). */
        val V2 = CatalogLevelPackVersion(2)
    }
}

/** The level the player sees. Numbering starts at 1 and is unbounded from the app's perspective. */
@JvmInline
value class CatalogLevelNumber(
    val value: Int,
) {
    init {
        require(value >= 1) { "Catalog level numbers start at 1." }
    }

    val next: CatalogLevelNumber get() = CatalogLevelNumber(value + 1)
}

/** The frozen content bucket entry a displayed level resolves to. */
@JvmInline
value class CatalogContentSlot(
    val value: Int,
) {
    init {
        require(value in 1..CatalogLevelPacks.SLOTS_PER_BUCKET) {
            "Content slot must be within 1..${CatalogLevelPacks.SLOTS_PER_BUCKET}."
        }
    }

    /** Zero-based position of this slot inside its bucket. */
    val index: Int get() = value - 1
}

object CatalogLevelPacks {
    /** Frozen content entries per game/difficulty bucket in every pack version. */
    const val SLOTS_PER_BUCKET = 10_000

    val FIRST_LEVEL = CatalogLevelNumber(1)

    /**
     * Content cycles once a bucket is exhausted while the displayed progression keeps growing, so
     * level 10 001 replays the content of slot 1 and still stays a distinct completion.
     */
    fun contentSlotFor(levelNumber: CatalogLevelNumber): CatalogContentSlot =
        CatalogContentSlot(((levelNumber.value - 1) % SLOTS_PER_BUCKET) + 1)

    /**
     * The packs a game's levels come from, oldest first. Only the Nonogram has a second one: its
     * progression moved to V2, while the levels a player cleared in V1 keep resolving from V1.
     */
    fun packVersionsFor(puzzleType: PuzzleType): List<CatalogLevelPackVersion> =
        if (puzzleType ==
            PuzzleType.NONOGRAM
        ) {
            listOf(CatalogLevelPackVersion.V1, CatalogLevelPackVersion.V2)
        } else {
            listOf(CatalogLevelPackVersion.V1)
        }

    /** The pack new levels of [puzzleType] are played from. */
    fun activePackVersion(puzzleType: PuzzleType): CatalogLevelPackVersion = packVersionsFor(puzzleType).last()

    /**
     * Nonogram Level Pack V2 alternates by slot, and so by level number (a bucket's 10 000 slots are
     * even): an odd slot is a real picture of Generator V3, an even one a symmetric level of V4. Its
     * bucket header records [NONOGRAM_V2_PICTURES].
     */
    fun alternatesBySlot(
        puzzleType: PuzzleType,
        packVersion: CatalogLevelPackVersion,
    ): Boolean = puzzleType == PuzzleType.NONOGRAM && packVersion == CatalogLevelPackVersion.V2

    /** The generator a level is built with: its bucket's, except where [alternatesBySlot] decides by slot. */
    fun generatorVersionFor(
        levelId: CatalogLevelId,
        bucketGeneratorVersion: GeneratorVersion,
    ): GeneratorVersion =
        when {
            !alternatesBySlot(levelId.puzzleType, levelId.packVersion) -> bucketGeneratorVersion
            levelId.contentSlot.value % 2 == 1 -> NONOGRAM_V2_PICTURES
            else -> NONOGRAM_V2_SYMMETRIC
        }

    /** Nonogram V2 odd slots: a picture index of Generator V3; also the generator its bucket header names. */
    val NONOGRAM_V2_PICTURES = GeneratorVersion(3)

    /** Nonogram V2 even slots: an accepted seed of Generator V4. */
    val NONOGRAM_V2_SYMMETRIC = GeneratorVersion(4)

    /** The Catalog games that own a frozen level pack. */
    val PUZZLE_TYPES: List<PuzzleType> =
        listOf(
            PuzzleType.BALANCE,
            PuzzleType.CROWNS,
            PuzzleType.WORD,
            PuzzleType.SUDOKU,
            PuzzleType.GAME_2048,
            PuzzleType.NONOGRAM,
            PuzzleType.BLOCK_SUDOKU,
        )
}

/**
 * The public identity of one Catalog level: which game, at which difficulty, which displayed level,
 * and which frozen pack that level was resolved from.
 */
data class CatalogLevelId(
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val levelNumber: CatalogLevelNumber,
    val packVersion: CatalogLevelPackVersion = CatalogLevelPackVersion.V1,
) {
    init {
        require(puzzleType in CatalogLevelPacks.PUZZLE_TYPES) { "$puzzleType has no Catalog level pack." }
    }

    val contentSlot: CatalogContentSlot get() = CatalogLevelPacks.contentSlotFor(levelNumber)
}

/**
 * A resolved level: the public identity plus the frozen seed the game's existing generator or
 * content provider is asked for. Runtime never searches for another seed.
 */
data class CatalogLevelDefinition(
    val levelId: CatalogLevelId,
    val seed: PuzzleSeed,
    val generatorVersion: GeneratorVersion,
) {
    val puzzleType: PuzzleType get() = levelId.puzzleType
    val difficulty: Difficulty get() = levelId.difficulty
    val levelNumber: CatalogLevelNumber get() = levelId.levelNumber
}
