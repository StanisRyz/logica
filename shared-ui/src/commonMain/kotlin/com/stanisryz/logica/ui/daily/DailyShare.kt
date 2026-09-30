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

/** The languages the Daily share text is written in; hosts pass the one their interface uses. */
enum class DailyShareLanguage {
    RUSSIAN,
    ENGLISH,
    TURKISH,
    ;

    companion object {
        /** Russian for `ru`, Turkish for `tr`, English for anything else. */
        fun fromTag(tag: String?): DailyShareLanguage =
            when (tag?.lowercase()?.take(2)) {
                "ru" -> RUSSIAN
                "tr" -> TURKISH
                else -> ENGLISH
            }
    }
}

/**
 * Plain-text Daily share formatting shared by Android and Web: pure Kotlin, no Context/Intent/
 * string resources/browser APIs, so both hosts produce identical deterministic text for the same
 * language. That is also why the three languages' text lives here rather than in resources.
 *
 * The payload type only ever carries outcome/attempts per puzzle, so the output is spoiler-free
 * by construction.
 */
object DailyShareFormatter {
    fun format(
        payload: DailySharePayload,
        language: DailyShareLanguage = DailyShareLanguage.RUSSIAN,
    ): String {
        val lines = mutableListOf<String>()
        lines +=
            when (language) {
                DailyShareLanguage.RUSSIAN -> "Логика дня — ${payload.dateLabel}"
                DailyShareLanguage.ENGLISH -> "Logica daily — ${payload.dateLabel}"
                DailyShareLanguage.TURKISH -> "Logica günlük — ${payload.dateLabel}"
            }
        lines += ""
        payload.entries.forEach { entry -> lines += formatEntry(entry, language) }
        lines += ""
        lines +=
            when (language) {
                DailyShareLanguage.RUSSIAN -> "${payload.completedCount} из ${payload.totalCount}"
                DailyShareLanguage.ENGLISH -> "${payload.completedCount} of ${payload.totalCount}"
                DailyShareLanguage.TURKISH -> "${payload.completedCount} / ${payload.totalCount}"
            }
        val streak = payload.currentStreak
        lines +=
            when (language) {
                DailyShareLanguage.RUSSIAN -> "🔥 Серия: $streak ${russianDayWord(streak)}"
                DailyShareLanguage.ENGLISH -> "🔥 Streak: $streak ${if (streak == 1) "day" else "days"}"
                DailyShareLanguage.TURKISH -> "🔥 Seri: $streak gün"
            }
        return lines.joinToString(separator = "\n")
    }

    private fun formatEntry(
        entry: DailyShareEntry,
        language: DailyShareLanguage,
    ): String {
        val value =
            when {
                entry.puzzleType != PuzzleType.WORD -> "✓"
                entry.solved -> "${entry.wordAttemptsUsed ?: 0}/${WordRules.MAXIMUM_ATTEMPTS}"
                else ->
                    when (language) {
                        DailyShareLanguage.RUSSIAN -> "не угадано"
                        DailyShareLanguage.ENGLISH -> "not guessed"
                        DailyShareLanguage.TURKISH -> "bilinemedi"
                    }
            }
        // Names longer than the column still keep one space before the mark.
        return "${puzzleLabel(entry.puzzleType, language).padEnd(LABEL_WIDTH - 1)} $value"
    }

    private fun puzzleLabel(
        puzzleType: PuzzleType,
        language: DailyShareLanguage,
    ): String {
        val labels =
            when (language) {
                DailyShareLanguage.RUSSIAN -> listOf("Баланс", "Короны", "Слово", "Судоку", "Нонограмма", "Блок-судоку")
                DailyShareLanguage.ENGLISH -> listOf("Balance", "Crowns", "Word", "Sudoku", "Nonogram", "Block Sudoku")
                DailyShareLanguage.TURKISH -> listOf("Denge", "Taçlar", "Kelime", "Sudoku", "Nonogram", "Blok Sudoku")
            }
        return when (puzzleType) {
            PuzzleType.BALANCE -> labels[0]
            PuzzleType.CROWNS -> labels[1]
            PuzzleType.WORD -> labels[2]
            PuzzleType.SUDOKU -> labels[3]
            // The 2048 score is deliberately absent: a generic result does not carry that metric.
            PuzzleType.GAME_2048 -> "2048"
            PuzzleType.NONOGRAM -> labels[4]
            PuzzleType.BLOCK_SUDOKU -> labels[5]
            else -> error("Daily sharing does not support $puzzleType.")
        }
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
