package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyChallengePolicyV8Test {
    @Test
    fun v8IsV7WithoutWordInV7Order() {
        val definition = DailyChallengePolicyResolver.definitionFor(LocalDate.of(2026, 10, 12), DailyChallengePolicyV8.VERSION)

        assertEquals(DailyChallengePolicyV8.VERSION, definition.policyVersion)
        assertEquals(
            listOf(
                PuzzleType.BALANCE,
                PuzzleType.CROWNS,
                PuzzleType.SUDOKU,
                PuzzleType.GAME_2048,
                PuzzleType.NONOGRAM,
                PuzzleType.BLOCK_SUDOKU,
            ),
            definition.entries.map { it.puzzleType },
        )
    }

    @Test
    fun everyV8EntryIsTheSameDayV7Entry() {
        // Seeds and definitions must match, so the switch day shows the same six puzzles.
        listOf(
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 12),
            LocalDate.of(2027, 2, 28),
            LocalDate.of(2028, 2, 29),
            LocalDate.of(2030, 12, 31),
        ).forEach { date ->
            val v7 = DailyChallengePolicyV7.definitionFor(date).entries.associateBy { it.puzzleType }
            DailyChallengePolicyV8.definitionFor(date).entries.forEach { entry ->
                assertEquals(v7.getValue(entry.puzzleType), entry)
            }
        }
    }

    @Test
    fun theResolverKnowsV1ThroughV8AndNewRunsUseV8() {
        val date = LocalDate.of(2026, 10, 12)
        (1..8).forEach { version ->
            assertEquals(
                DailyPolicyVersion(version),
                DailyChallengePolicyResolver.definitionFor(date, DailyPolicyVersion(version)).policyVersion,
            )
        }
        assertEquals(DailyChallengePolicyV8.VERSION, DailyChallengePolicyResolver.NEW_RUN_VERSION)
        // A persisted V7 run keeps its seven entries, Word included.
        assertTrue(
            DailyChallengePolicyResolver.definitionFor(date, DailyChallengePolicyV7.VERSION).entries.any {
                it.puzzleType ==
                    PuzzleType.WORD
            },
        )
    }

    @Test
    fun v8StreaksFollowTheV7Rule() {
        assertEquals(
            DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(DailyChallengePolicyV7.VERSION),
            DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(DailyChallengePolicyV8.VERSION),
        )
        assertTrue(DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(DailyChallengePolicyV8.VERSION))
        // A V7 day followed by V8 days is one streak: the calculator reads qualified dates only.
        val streak =
            DailyStreakCalculator.calculate(
                currentDate = LocalDate.of(2026, 10, 13),
                qualifiedDates = listOf(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13)),
            )
        assertEquals(DailyStreak(current = 3, best = 3), streak)
    }
}
