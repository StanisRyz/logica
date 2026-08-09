package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.time.LocalDate

/** New runs use Word V2 while the immutable V1/V2/V3 definitions remain resolvable unchanged. */
object DailyChallengePolicyV4 {
    val VERSION = DailyPolicyVersion(4)

    fun definitionFor(date: LocalDate): DailyChallengeDefinition =
        DailyChallengeDefinition(
            challengeDate = date,
            policyVersion = VERSION,
            entries =
                listOf(PuzzleType.BALANCE, PuzzleType.CROWNS, PuzzleType.WORD).map { puzzleType ->
                    val generatorVersion =
                        when (puzzleType) {
                            PuzzleType.WORD -> GeneratorVersion(2)
                            else -> GeneratorVersion(1)
                        }
                    DailyPuzzleEntry(
                        puzzleType = puzzleType,
                        difficulty = Difficulty.MEDIUM,
                        seed = DailyPuzzleSeedV1.derive(date, puzzleType, generatorVersion),
                        generatorVersion = generatorVersion,
                    )
                },
        )
}
