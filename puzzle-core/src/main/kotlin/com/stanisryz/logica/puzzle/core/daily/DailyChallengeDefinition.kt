package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.time.LocalDate

@JvmInline
value class DailyPolicyVersion(
    val value: Int,
) {
    init {
        require(value > 0) { "Daily policy version must be positive." }
    }
}

data class DailyPuzzleEntry(
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val seed: PuzzleSeed,
    val generatorVersion: GeneratorVersion,
) {
    val puzzleId: PuzzleId
        get() = PuzzleId(puzzleType, difficulty, seed, generatorVersion)
}

data class DailyChallengeDefinition(
    val challengeDate: LocalDate,
    val policyVersion: DailyPolicyVersion,
    val entries: List<DailyPuzzleEntry>,
) {
    init {
        require(entries.isNotEmpty()) { "A Daily challenge must contain at least one puzzle." }
        require(entries.map(DailyPuzzleEntry::puzzleType).distinct().size == entries.size) {
            "A Daily challenge cannot contain duplicate puzzle types."
        }
    }
}
