package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.result.GameOutcome
import java.time.LocalDate

/**
 * Plain-text Daily share formatter: pure Kotlin, no Context/Intent/string resources, so it stays a
 * simple function the Sharesheet-triggering UI can call and tests can exercise without Robolectric.
 * That is also why the Russian text below is hardcoded rather than pulled from strings.xml.
 *
 * [DailyResultSummary] only ever carries outcome/attempts per puzzle, never the Word answer, guesses,
 * letter feedback, boards, seeds, or IDs, so the output is spoiler-free by construction.
 */
internal object DailyShareFormatter {
    fun format(summary: DailyResultSummary): String {
        val lines = mutableListOf<String>()
        lines += "Логика дня — ${summary.challengeDate.toRussianDisplay()}"
        lines += ""
        summary.entries.forEach { entry -> lines += formatEntry(entry) }
        lines += ""
        lines += "${summary.completedCount} из ${summary.totalCount}"
        lines += "🔥 Серия: ${summary.currentStreak} ${russianDayWord(summary.currentStreak)}"
        return lines.joinToString(separator = "\n")
    }

    private fun formatEntry(entry: DailyPuzzleResultSummary): String {
        val value =
            when {
                entry.puzzleType != PuzzleType.WORD -> "✓"
                entry.outcome == GameOutcome.SOLVED -> "${entry.attemptsUsed}/${WordRules.MAXIMUM_ATTEMPTS}"
                else -> "не угадано"
            }
        return "${puzzleLabel(entry.puzzleType).padEnd(LABEL_WIDTH)}$value"
    }

    private fun puzzleLabel(puzzleType: PuzzleType): String =
        when (puzzleType) {
            PuzzleType.BALANCE -> "Баланс"
            PuzzleType.CROWNS -> "Короны"
            PuzzleType.WORD -> "Слово"
            PuzzleType.SUDOKU -> "Судоку"
            // The 2048 score is deliberately absent: a generic result does not carry that metric.
            PuzzleType.GAME_2048 -> "2048"
            else -> error("Daily sharing does not support $puzzleType.")
        }

    private fun russianDayWord(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> "дней"
            mod10 == 1 -> "день"
            mod10 in 2..4 -> "дня"
            else -> "дней"
        }
    }

    private const val LABEL_WIDTH = 8
}

private val RUSSIAN_GENITIVE_MONTHS =
    listOf(
        "января",
        "февраля",
        "марта",
        "апреля",
        "мая",
        "июня",
        "июля",
        "августа",
        "сентября",
        "октября",
        "ноября",
        "декабря",
    )

internal fun LocalDate.toRussianDisplay(): String = "$dayOfMonth ${RUSSIAN_GENITIVE_MONTHS[monthValue - 1]}"
