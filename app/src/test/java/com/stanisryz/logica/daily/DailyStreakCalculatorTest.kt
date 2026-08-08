package com.stanisryz.logica.daily

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailyStreakCalculatorTest {
    @Test
    fun calculatesCurrentAndBestStreakAcrossDailySemantics() {
        val today = LocalDate.of(2026, 8, 8)
        val cases =
            listOf(
                Case(
                    completed = listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1), today),
                    expected = DailyStreak(current = 4, best = 4),
                ),
                Case(
                    completed = listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1)),
                    expected = DailyStreak(current = 3, best = 3),
                ),
                Case(
                    completed =
                        listOf(
                            today.minusDays(7),
                            today.minusDays(6),
                            today.minusDays(5),
                            today.minusDays(2),
                            today.minusDays(2),
                        ),
                    expected = DailyStreak(current = 0, best = 3),
                ),
            )

        cases.forEach { case ->
            assertEquals(case.expected, DailyStreakCalculator.calculate(today, case.completed))
        }
    }

    private data class Case(
        val completed: List<LocalDate>,
        val expected: DailyStreak,
    )
}
