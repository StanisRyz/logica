package com.stanisryz.logica.puzzle.core.catalog

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType

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

    /** The Catalog games that own a frozen level pack. */
    val PUZZLE_TYPES: List<PuzzleType> =
        listOf(
            PuzzleType.BALANCE,
            PuzzleType.CROWNS,
            PuzzleType.WORD,
            PuzzleType.SUDOKU,
            PuzzleType.GAME_2048,
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
