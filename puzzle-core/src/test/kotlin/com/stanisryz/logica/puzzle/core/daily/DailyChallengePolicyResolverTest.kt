package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class DailyChallengePolicyResolverTest {
    @Test
    fun resolvesImmutablePoliciesThroughV4WithoutReinterpretingGeneratorVersions() {
        val date = LocalDate.of(2026, 8, 8)
        val v1 = DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV1.VERSION)
        val v2 = DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV2.VERSION)
        val v3 = DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV3.VERSION)
        val v4 = DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV4.VERSION)

        assertEquals(DailyChallengePolicyV1.definitionFor(date), v1)
        assertEquals(v2, DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV2.VERSION))
        assertEquals(DailyChallengePolicyV2.VERSION, v2.policyVersion)
        assertEquals(listOf(PuzzleType.BALANCE, PuzzleType.CROWNS), v2.entries.map { it.puzzleType })
        v2.entries.forEach { entry ->
            assertEquals(Difficulty.MEDIUM, entry.difficulty)
            assertEquals(GeneratorVersion(1), entry.generatorVersion)
        }
        assertNotEquals(v2.entries[0].seed, v2.entries[1].seed)
        assertEquals(GeneratorVersion(1), v3.entries.single { it.puzzleType == PuzzleType.WORD }.generatorVersion)
        assertEquals(GeneratorVersion(2), v4.entries.single { it.puzzleType == PuzzleType.WORD }.generatorVersion)
        assertEquals(
            listOf(GeneratorVersion(1), GeneratorVersion(1), GeneratorVersion(2)),
            v4.entries.map { it.generatorVersion },
        )
    }
}
