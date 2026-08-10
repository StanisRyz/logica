package com.stanisryz.logica.result

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
 * How a terminal attempt ended. Every puzzle can now fail: Balance and Crowns after their third
 * committed mistake, Word after its sixth valid incorrect attempt.
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
) {
    init {
        require(resultId.isNotBlank()) { "Result ID must not be blank." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
        require(
            (sessionScope == GameSessionScope.DAILY) ==
                (challengeDate != null && dailyPolicyVersion != null),
        ) { "Daily identity must be present only for Daily results." }
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
) {
    init {
        require(resultId.isNotBlank()) { "Result ID must not be blank." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
        require(
            (sessionScope == GameSessionScope.DAILY) ==
                (challengeDate != null && dailyPolicyVersion != null),
        ) { "Daily identity must be present only for Daily results." }
        requireOutcomeMetadata(puzzleType, attemptsUsed)
    }
}

internal interface GameCompletionRepository {
    suspend fun complete(completion: GameCompletion): GameResult
}
