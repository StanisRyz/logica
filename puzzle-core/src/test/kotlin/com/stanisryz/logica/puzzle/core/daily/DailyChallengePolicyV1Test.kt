package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class DailyChallengePolicyV1Test {
    @Test
    fun policyIsVersionedStableAndDateSensitive() {
        val date = LocalDate.of(2026, 8, 8)
        val first = DailyChallengePolicyV1.definitionFor(date)
        val repeated = DailyChallengePolicyV1.definitionFor(date)
        val nextDay = DailyChallengePolicyV1.definitionFor(date.plusDays(1))

        assertEquals(first, repeated)
        assertEquals(DailyPolicyVersion(1), first.policyVersion)
        assertEquals(1, first.entries.size)
        with(first.entries.single()) {
            assertEquals(PuzzleType.BALANCE, puzzleType)
            assertEquals(Difficulty.MEDIUM, difficulty)
            assertEquals(GeneratorVersion(1), generatorVersion)
            assertNotEquals(seed, nextDay.entries.single().seed)
        }
    }
}
