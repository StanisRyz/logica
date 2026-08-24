package com.stanisryz.logica.daily

import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.ui.daily.DailyShareEntry
import com.stanisryz.logica.ui.daily.DailyShareFormatter as SharedDailyShareFormatter
import com.stanisryz.logica.ui.daily.DailySharePayload
import java.time.LocalDate

/**
 * Thin Android adapter over the shared platform-neutral Daily share formatter: the payload is
 * mapped from the existing spoiler-free [DailyResultSummary] and the shared formatter produces
 * the same deterministic Russian text the Android-only implementation used to produce.
 */
internal object DailyShareFormatter {
    fun format(summary: DailyResultSummary): String =
        SharedDailyShareFormatter.format(
            DailySharePayload(
                dateLabel = summary.challengeDate.toRussianDisplay(),
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
        )
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

