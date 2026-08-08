package com.stanisryz.logica.result

import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.GameSessionScope
import java.time.Instant
import java.time.LocalDate

internal data class GameCompletion(
    val resultId: String,
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val puzzleSeed: PuzzleSeed,
    val generatorVersion: GeneratorVersion,
    val sessionScope: GameSessionScope,
    val hintsUsed: Int,
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
    }
}

internal interface GameCompletionRepository {
    suspend fun complete(completion: GameCompletion): GameResult
}
