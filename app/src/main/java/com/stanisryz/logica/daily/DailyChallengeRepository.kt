package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.time.LocalDate

internal enum class DailyChallengeStatus {
    IN_PROGRESS,
    COMPLETED,
}

internal data class SavedDailyChallenge(
    val challengeDate: LocalDate,
    val puzzleType: PuzzleType,
    val policyVersion: DailyPolicyVersion,
    val difficulty: Difficulty,
    val seed: PuzzleSeed,
    val generatorVersion: GeneratorVersion,
    val status: DailyChallengeStatus,
) {
    fun matches(
        definition: DailyChallengeDefinition,
        entry: DailyPuzzleEntry,
    ): Boolean =
        challengeDate == definition.challengeDate &&
            puzzleType == entry.puzzleType &&
            policyVersion == definition.policyVersion &&
            difficulty == entry.difficulty &&
            seed == entry.seed &&
            generatorVersion == entry.generatorVersion
}

internal interface DailyChallengeRepository {
    suspend fun read(
        challengeDate: LocalDate,
        puzzleType: PuzzleType,
    ): SavedDailyChallenge?

    suspend fun save(challenge: SavedDailyChallenge)
}
