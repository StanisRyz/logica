package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyChallengePolicyV5Test {
    private val date: LocalDate = LocalDate.of(2026, 8, 11)

    @Test
    fun v5DefinesFiveUniqueMediumEntriesWithTheirOwnContentVersions() {
        val definition = DailyChallengePolicyV5.definitionFor(date)

        assertEquals(DailyChallengePolicyV5.VERSION, definition.policyVersion)
        assertEquals(date, definition.challengeDate)
        assertEquals(
            listOf(
                PuzzleType.BALANCE,
                PuzzleType.CROWNS,
                PuzzleType.WORD,
                PuzzleType.SUDOKU,
                PuzzleType.GAME_2048,
            ),
            definition.entries.map { it.puzzleType },
        )
        definition.entries.forEach { entry -> assertEquals(Difficulty.MEDIUM, entry.difficulty) }
        assertEquals(
            mapOf(
                PuzzleType.BALANCE to GeneratorVersion(1),
                PuzzleType.CROWNS to GeneratorVersion(1),
                PuzzleType.WORD to GeneratorVersion(2),
                PuzzleType.SUDOKU to GeneratorVersion(1),
                PuzzleType.GAME_2048 to GeneratorVersion(2),
            ),
            definition.entries.associate { it.puzzleType to it.generatorVersion },
        )
        // Five different puzzles means five different identities on the same date.
        val seeds = definition.entries.mapTo(mutableSetOf()) { it.seed }
        assertEquals(5, seeds.size)
    }

    @Test
    fun v5IdentitiesAreDeterministicPerDateAndResolvedLikeEveryOtherPolicy() {
        val definition = DailyChallengePolicyV5.definitionFor(date)

        assertEquals(definition, DailyChallengePolicyV5.definitionFor(date))
        assertEquals(definition, DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV5.VERSION))
        val nextDay = DailyChallengePolicyV5.definitionFor(date.plusDays(1))
        definition.entries.zip(nextDay.entries).forEach { (today, tomorrow) ->
            assertEquals(today.puzzleType, tomorrow.puzzleType)
            assertNotEquals(today.seed, tomorrow.seed)
        }
    }

    @Test
    fun v1ThroughV4StayUnchangedAndKeepTheirFullCompletionStreakRule() {
        val v4 = DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV4.VERSION)

        assertEquals(DailyChallengePolicyV4.definitionFor(date), v4)
        assertEquals(listOf(PuzzleType.BALANCE, PuzzleType.CROWNS, PuzzleType.WORD), v4.entries.map { it.puzzleType })
        assertEquals(1, DailyChallengePolicyV1.definitionFor(date).entries.size)
        assertEquals(2, DailyChallengePolicyV2.definitionFor(date).entries.size)
        assertEquals(3, DailyChallengePolicyV3.definitionFor(date).entries.size)
        // The shared entries keep the exact seeds they had before V5 existed.
        listOf(PuzzleType.BALANCE, PuzzleType.CROWNS).forEach { puzzleType ->
            assertEquals(
                DailyChallengePolicyV2.definitionFor(date).entries.single { it.puzzleType == puzzleType },
                v4.entries.single { it.puzzleType == puzzleType },
            )
        }
        listOf(
            DailyChallengePolicyV1.VERSION,
            DailyChallengePolicyV2.VERSION,
            DailyChallengePolicyV3.VERSION,
            DailyChallengePolicyV4.VERSION,
        ).forEach { version ->
            assertFalse(DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(version))
        }
        assertTrue(DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(DailyChallengePolicyV5.VERSION))
    }
}

class DailyChallengePolicyV6Test {
    @org.junit.Test
    fun v6IsV5PlusARealPictureNonogramSeededByTheEpochDay() {
        val date = java.time.LocalDate.of(2026, 10, 1)
        val v5 = DailyChallengePolicyV5.definitionFor(date)
        val v6 = DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV6.VERSION)
        org.junit.Assert.assertEquals(v5.entries, v6.entries.dropLast(1))
        val nonogram = v6.entries.last()
        org.junit.Assert.assertEquals(com.stanisryz.logica.puzzle.core.model.PuzzleType.NONOGRAM, nonogram.puzzleType)
        org.junit.Assert.assertEquals(date.toEpochDay(), nonogram.seed.value)
        org.junit.Assert.assertEquals(DailyChallengePolicyV6.NONOGRAM_GENERATOR_VERSION, nonogram.generatorVersion)
        org.junit.Assert.assertTrue(DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(DailyChallengePolicyV6.VERSION))
    }
}

class DailyChallengePolicyV7Test {
    @org.junit.Test
    fun v7IsV6PlusAMediumBlockSudokuOnTheDailySeed() {
        val date = java.time.LocalDate.of(2026, 10, 1)
        val v6 = DailyChallengePolicyV6.definitionFor(date)
        val v7 = DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV7.VERSION)
        org.junit.Assert.assertEquals(v6.entries, v7.entries.dropLast(1))
        val blockSudoku = v7.entries.last()
        org.junit.Assert.assertEquals(com.stanisryz.logica.puzzle.core.model.PuzzleType.BLOCK_SUDOKU, blockSudoku.puzzleType)
        org.junit.Assert.assertEquals(com.stanisryz.logica.puzzle.core.model.Difficulty.MEDIUM, blockSudoku.difficulty)
        org.junit.Assert.assertEquals(DailyChallengePolicyV7.BLOCK_SUDOKU_RULES_VERSION, blockSudoku.generatorVersion)
        org.junit.Assert.assertTrue(DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(DailyChallengePolicyV7.VERSION))
    }
}
