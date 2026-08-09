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
    fun resolvesStableV1AndTwoEntryV2WithoutReinterpretingVersions() {
        val date = LocalDate.of(2026, 8, 8)
        val v1 = DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV1.VERSION)
        val v2 = DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV2.VERSION)

        assertEquals(DailyChallengePolicyV1.definitionFor(date), v1)
        assertEquals(v2, DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV2.VERSION))
        assertEquals(DailyChallengePolicyV2.VERSION, v2.policyVersion)
        assertEquals(listOf(PuzzleType.BALANCE, PuzzleType.CROWNS), v2.entries.map { it.puzzleType })
        v2.entries.forEach { entry ->
            assertEquals(Difficulty.MEDIUM, entry.difficulty)
            assertEquals(GeneratorVersion(1), entry.generatorVersion)
        }
        assertNotEquals(v2.entries[0].seed, v2.entries[1].seed)
    }
}
