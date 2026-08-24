package com.stanisryz.logica.ui.daily

import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules

/**
 * One platform-neutral, spoiler-free per-entry Daily share fact. It never carries answers,
 * guesses, boards, seeds, generator identities, or internal IDs — by construction.
 */
data class DailyShareEntry(
    val puzzleType: PuzzleType,
    val solved: Boolean,
    val wordAttemptsUsed: Int? = null,
)

/**
 * The complete share payload for one fully completed Daily challenge. [dateLabel] arrives
 * pre-formatted so the formatter stays independent from `java.time` and browser date APIs.
 */
data class DailySharePayload(
    val dateLabel: String,
    val entries: List<DailyShareEntry>,
    val completedCount: Int,
    val totalCount: Int,
    val currentStreak: Int,
)

/**
 * Plain-text Daily share formatting shared by Android and Web: pure Kotlin, no Context/Intent/
 * string resources/browser APIs, so both hosts produce identical deterministic text. That is also
 * why the Russian text is hardcoded rather than pulled from resources.
 *
 * The payload type only ever carries outcome/attempts per puzzle, so the output is spoiler-free
 * by construction.
 */
object DailyShareFormatter {
    fun format(payload: DailySharePayload): String {
        val lines = mutableListOf<String>()
        lines += "Логика дня — ${payload.dateLabel}"
        lines += ""
        payload.entries.forEach { entry -> lines += formatEntry(entry) }
        lines += ""
        lines += "${payload.completedCount} из ${payload.totalCount}"
        lines += "🔥 Серия: ${payload.currentStreak} ${russianDayWord(payload.currentStreak)}"
        return lines.joinToString(separator = "\n")
    }

    private fun formatEntry(entry: DailyShareEntry): String {
        val value =
            when {
                entry.puzzleType != PuzzleType.WORD -> "✓"
                entry.solved -> "${entry.wordAttemptsUsed ?: 0}/${WordRules.MAXIMUM_ATTEMPTS}"
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
