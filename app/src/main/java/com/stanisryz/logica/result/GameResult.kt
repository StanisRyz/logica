package com.stanisryz.logica.result

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.session.GameSessionScope
import java.time.Instant
import java.time.LocalDate

/**
 * How a terminal attempt ended. Every production puzzle has typed solved and failed outcomes; each
 * concrete engine owns the rule that reaches them.
 */
internal enum class GameOutcome {
    SOLVED,
    FAILED,
}

/** Shared validation for the typed outcome metadata of one terminal game. */
private fun requireOutcomeMetadata(
    puzzleType: PuzzleType,
    attemptsUsed: Int?,
) {
    if (puzzleType == PuzzleType.WORD) {
        requireNotNull(attemptsUsed) { "A Word result must record the attempts used." }
        require(attemptsUsed in 1..WordRules.MAXIMUM_ATTEMPTS) {
            "Word attempts used must be within 1..${WordRules.MAXIMUM_ATTEMPTS}."
        }
    } else {
        require(attemptsUsed == null) { "Only Word results record attempts used." }
    }
}

internal data class GameCompletion(
    val resultId: String,
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val puzzleSeed: PuzzleSeed,
    val generatorVersion: GeneratorVersion,
    val sessionScope: GameSessionScope,
    val hintsUsed: Int,
    val outcome: GameOutcome = GameOutcome.SOLVED,
    val attemptsUsed: Int? = null,
    val challengeDate: LocalDate? = null,
    val dailyPolicyVersion: DailyPolicyVersion? = null,
    /**
     * The public Catalog level this attempt belongs to. Present for Catalog results only: a Daily
     * result stays a Daily result and never pretends to be a Catalog level.
     */
    val catalogLevel: CatalogLevelId? = null,
) {
    init {
        require(resultId.isNotBlank()) { "Result ID must not be blank." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
        require(
            (sessionScope == GameSessionScope.DAILY) ==
                (challengeDate != null && dailyPolicyVersion != null),
        ) { "Daily identity must be present only for Daily results." }
        require(catalogLevel == null || sessionScope == GameSessionScope.CATALOG) {
            "Only a Catalog result may carry Catalog level identity."
        }
        require(catalogLevel == null || (catalogLevel.puzzleType == puzzleType && catalogLevel.difficulty == difficulty)) {
            "The Catalog level identity does not match the completed puzzle."
        }
        requireOutcomeMetadata(puzzleType, attemptsUsed)
    }
}

internal data class GameResult(
    val resultId: String,
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val puzzleSeed: PuzzleSeed,
    val generatorVersion: GeneratorVersion,
    val sessionScope: GameSessionScope,
    val hintsUsed: Int,
    val completedAt: Instant,
    val outcome: GameOutcome = GameOutcome.SOLVED,
    val attemptsUsed: Int? = null,
    val challengeDate: LocalDate? = null,
    val dailyPolicyVersion: DailyPolicyVersion? = null,
    /** Null for Daily results and for Catalog results recorded before the frozen level system. */
    val catalogLevel: CatalogLevelId? = null,
) {
    init {
        require(resultId.isNotBlank()) { "Result ID must not be blank." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
        require(
            (sessionScope == GameSessionScope.DAILY) ==
                (challengeDate != null && dailyPolicyVersion != null),
        ) { "Daily identity must be present only for Daily results." }
        require(catalogLevel == null || sessionScope == GameSessionScope.CATALOG) {
            "Only a Catalog result may carry Catalog level identity."
        }
        requireOutcomeMetadata(puzzleType, attemptsUsed)
    }
}

internal interface GameCompletionRepository {
    suspend fun complete(completion: GameCompletion): GameResult
}
