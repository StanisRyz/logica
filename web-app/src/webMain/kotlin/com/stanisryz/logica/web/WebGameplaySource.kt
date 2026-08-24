package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/**
 * The pre-playing launch identity of a controller lifecycle. Playing states carry the full
 * [WebGameplaySource]; Loading and Error keep only this small request so Back and Retry stay
 * source-aware before any puzzle exists. Runtime-only; never persisted.
 */
internal sealed interface WebGameLaunch {
    val puzzleType: PuzzleType

    val difficulty: Difficulty

    val isDaily: Boolean

    /** Repeats the Catalog flow for the same difficulty and its authoritative current level. */
    data class Catalog(
        override val puzzleType: PuzzleType,
        val requestedDifficulty: Difficulty,
    ) : WebGameLaunch {
        override val difficulty get() = requestedDifficulty
        override val isDaily get() = false
    }

    /** Repeats exactly this Daily attempt identity: date, policy, seed, generator, token. */
    data class Daily(
        val attempt: WebDailyAttempt,
    ) : WebGameLaunch {
        private val entry get() = attempt.entry
        override val puzzleType get() = entry.puzzleType
        override val difficulty get() = entry.difficulty
        override val isDaily get() = true
    }
}

/**
 * The one attempt-source seam shared by every Web gameplay controller. A fresh game originates
 * either from the Catalog — an authoritative frozen level bound to the current Player context —
 * or from the Daily challenge, whose deterministic identity comes straight from its policy entry.
 * The two sources keep completely separate lifecycle and persistence semantics downstream.
 */
internal sealed interface WebGameplaySource {
    val puzzleType: PuzzleType

    val difficulty: Difficulty

    val seed: PuzzleSeed

    val generatorVersion: GeneratorVersion

    val isDaily: Boolean

    /** Catalog identity; null for Daily attempts, which never resolve or fabricate a level number. */
    val catalogLevelNumberOrNull: Int?
        get() = null

    data class CatalogLevel(
        val attempt: WebCatalogAttempt,
        val definition: CatalogLevelDefinition,
    ) : WebGameplaySource {
        override val puzzleType get() = definition.puzzleType
        override val difficulty get() = definition.difficulty
        override val seed get() = definition.seed
        override val generatorVersion get() = definition.generatorVersion
        override val isDaily get() = false
        override val catalogLevelNumberOrNull: Int? get() = definition.levelNumber.value
    }

    data class DailyChallenge(
        val attempt: WebDailyAttempt,
    ) : WebGameplaySource {
        private val entry get() = attempt.entry
        override val puzzleType get() = entry.puzzleType
        override val difficulty get() = entry.difficulty
        override val seed get() = entry.seed
        override val generatorVersion get() = entry.generatorVersion
        override val isDaily get() = true
    }
}
