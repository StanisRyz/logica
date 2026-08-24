package com.stanisryz.logica.daily

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.ui.daily.DailyHubCompletion
import com.stanisryz.logica.ui.daily.DailyHubEntry
import com.stanisryz.logica.ui.daily.DailyHubEntryState
import com.stanisryz.logica.ui.daily.DailyHubResultRow
import com.stanisryz.logica.ui.daily.DailyHubStreak
import com.stanisryz.logica.ui.daily.DailyHubUiState
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The thin Android adapter between the Room-backed [TodayUiState] and the shared Daily Hub
 * presentation. Lifecycle, refresh, run creation, streak derivation, and sharing stay exactly
 * where they were; only presentation moves behind the neutral model.
 */
@Composable
internal fun TodayUiState.toDailyHubUiState(): DailyHubUiState =
    when (this) {
        TodayUiState.Loading -> DailyHubUiState.Loading
        is TodayUiState.Error ->
            DailyHubUiState.Error(
                detailLabel =
                    stringResource(
                        when (reason) {
                            TodayError.LOAD -> R.string.daily_load_error
                            TodayError.START -> R.string.daily_start_error
                        },
                    ),
            )
        is TodayUiState.Content -> toDailyHubContent()
    }

@Composable
private fun TodayUiState.Content.toDailyHubContent(): DailyHubUiState.Content {
    val context = LocalContext.current
    val dateLabel = formattedDateLabel(definition.challengeDate)
    return DailyHubUiState.Content(
        dateLabel = dateLabel,
        entries =
            entries.map { entry ->
                DailyHubEntry(
                    puzzleType = entry.puzzleType,
                    difficulty = entry.difficulty,
                    state = entry.state.toHubState(),
                )
            },
        streak =
            DailyHubStreak(
                anySolvedQualifies = streak.anySolvedQualifies,
                qualifiedToday = streak.qualifiedToday,
                current = streak.current,
                best = streak.best,
            ),
        completion =
            completion?.let { value ->
                DailyHubCompletion(
                    currentStreak = value.currentStreak,
                    bestStreak = value.bestStreak,
                    hintsUsed = value.hintsUsed,
                    resultRows =
                        value.resultSummary?.entries?.map { row ->
                            DailyHubResultRow(
                                puzzleType = row.puzzleType,
                                solved = row.outcome == GameOutcome.SOLVED,
                                wordAttemptsUsed = row.attemptsUsed,
                            )
                        },
                    onShare =
                        value.resultSummary?.let { summary ->
                            { context.shareDailyResult(summary) }
                        },
                )
            },
    )
}

@Composable
private fun formattedDateLabel(challengeDate: java.time.LocalDate): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    return formatter.format(challengeDate)
}

private fun DailyEntryState.toHubState(): DailyHubEntryState =
    when (this) {
        DailyEntryState.AVAILABLE -> DailyHubEntryState.AVAILABLE
        DailyEntryState.RETRY -> DailyHubEntryState.RETRY
        DailyEntryState.COMPLETED -> DailyHubEntryState.COMPLETED
    }

/** The only place an Android [Intent] gets built; [DailyShareFormatter] itself stays plain Kotlin. */
internal fun Context.shareDailyResult(summary: DailyResultSummary) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, DailyShareFormatter.format(summary))
        }
    startActivity(Intent.createChooser(sendIntent, null))
}
