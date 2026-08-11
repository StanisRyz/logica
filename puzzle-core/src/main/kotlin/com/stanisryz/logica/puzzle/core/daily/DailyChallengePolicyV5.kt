package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.time.LocalDate

/**
 * All five production games every day, each at Medium. New runs use V5 while the immutable V1–V4
 * definitions stay resolvable exactly as they were, so a persisted V4 run keeps its three entries.
 *
 * V5 also changes streak qualification: one solved entry is enough for the date, while the run still
 * completes only at 5/5. See [DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry].
 */
object DailyChallengePolicyV5 {
    val VERSION = DailyPolicyVersion(5)

    fun definitionFor(date: LocalDate): DailyChallengeDefinition =
        DailyChallengeDefinition(
            challengeDate = date,
            policyVersion = VERSION,
            entries =
                ENTRY_VERSIONS.map { (puzzleType, generatorVersion) ->
                    DailyPuzzleEntry(
                        puzzleType = puzzleType,
                        difficulty = Difficulty.MEDIUM,
                        seed = DailyPuzzleSeedV1.derive(date, puzzleType, generatorVersion),
                        generatorVersion = generatorVersion,
                    )
                },
        )

    /**
     * The stable policy order and the content/rules version each entry is built from: Balance and
     * Crowns generator V1, Word generator V2, the Sudoku Dataset V1 provider, and 2048 rules V2.
     */
    private val ENTRY_VERSIONS =
        listOf(
            PuzzleType.BALANCE to GeneratorVersion(1),
            PuzzleType.CROWNS to GeneratorVersion(1),
            PuzzleType.WORD to GeneratorVersion(2),
            PuzzleType.SUDOKU to GeneratorVersion(1),
            PuzzleType.GAME_2048 to GeneratorVersion(2),
        )
}
