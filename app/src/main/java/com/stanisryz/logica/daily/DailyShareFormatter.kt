package com.stanisryz.logica.daily

import com.stanisryz.logica.AppLanguage
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.ui.daily.DailyShareEntry
import com.stanisryz.logica.ui.daily.DailyShareLanguage
import com.stanisryz.logica.ui.daily.DailySharePayload
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.stanisryz.logica.ui.daily.DailyShareFormatter as SharedDailyShareFormatter

/**
 * Thin Android adapter over the shared platform-neutral Daily share formatter: the payload is
 * mapped from the existing spoiler-free [DailyResultSummary] and the shared formatter produces
 * the same deterministic text as Web, in the interface language.
 */
internal object DailyShareFormatter {
    fun format(
        summary: DailyResultSummary,
        languageTag: String = AppLanguage.tag,
    ): String =
        SharedDailyShareFormatter.format(
            DailySharePayload(
                dateLabel = summary.challengeDate.toShortDisplay(languageTag),
                entries =
                    summary.entries.map { entry ->
                        DailyShareEntry(
                            puzzleType = entry.puzzleType,
                            solved = entry.outcome == GameOutcome.SOLVED,
                            wordAttemptsUsed = entry.attemptsUsed,
                        )
                    },
                completedCount = summary.completedCount,
                totalCount = summary.totalCount,
                currentStreak = summary.currentStreak,
            ),
            DailyShareLanguage.fromTag(languageTag),
        )
}

/** "28 сентября", "September 28", or "28 Eylül": day and month in the interface language. */
internal fun LocalDate.toShortDisplay(languageTag: String = AppLanguage.tag): String {
    val pattern = if (DailyShareLanguage.fromTag(languageTag) == DailyShareLanguage.ENGLISH) "MMMM d" else "d MMMM"
    return format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(languageTag)))
}
