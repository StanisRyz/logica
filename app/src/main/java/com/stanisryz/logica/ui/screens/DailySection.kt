package com.stanisryz.logica.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.daily.DailyEntryState
import com.stanisryz.logica.daily.DailyPuzzleResultSummary
import com.stanisryz.logica.daily.DailyResultSummary
import com.stanisryz.logica.daily.DailyShareFormatter
import com.stanisryz.logica.daily.TodayCompletionUiState
import com.stanisryz.logica.daily.TodayEntryUiState
import com.stanisryz.logica.daily.TodayError
import com.stanisryz.logica.daily.TodayStreakUiState
import com.stanisryz.logica.daily.TodayUiState
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.ui.components.CompletionActions
import com.stanisryz.logica.ui.components.CompletionCard
import com.stanisryz.logica.ui.components.LabelledValue
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.MetricValue
import com.stanisryz.logica.ui.components.PuzzleArtwork
import com.stanisryz.logica.ui.components.ScreenTitle
import com.stanisryz.logica.ui.components.StatusChip
import com.stanisryz.logica.ui.components.SupportingText
import com.stanisryz.logica.ui.components.difficultyLabel
import com.stanisryz.logica.ui.components.progressFraction
import com.stanisryz.logica.ui.components.titleResource
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaMotion
import com.stanisryz.logica.ui.theme.LogicaSpacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Daily half of the Game hub: a compact header carrying the whole run's progress and one small
 * card per required puzzle, scrolled horizontally so a fourth Daily puzzle costs nothing but another
 * card. The regular catalog below it stays the dominant part of the screen.
 */
@Composable
internal fun DailySection(
    uiState: TodayUiState,
    gameplayAllowed: Boolean,
    onStart: (PuzzleType) -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
        AnimatedContent(
            targetState = uiState,
            contentKey = { state -> state.presentationKey() },
            transitionSpec = {
                fadeIn(tween(LogicaMotion.SCREEN_MILLIS)) togetherWith
                    fadeOut(tween(LogicaMotion.SHORT_MILLIS))
            },
            label = "dailyState",
        ) { state ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            if (state.presentationKey() != uiState.presentationKey()) hideFromAccessibility()
                        },
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            ) {
                when (state) {
                    TodayUiState.Loading -> {
                        ScreenTitle(stringResource(R.string.daily_challenge))
                        DailyLoading()
                    }
                    is TodayUiState.Error -> {
                        ScreenTitle(stringResource(R.string.daily_challenge))
                        DailyError(state.reason, onRetryLoad)
                    }
                    is TodayUiState.Content -> {
                        DailyHeader(state)
                        // V5 secures the streak on the first solve; full completion stays separate.
                        if (state.streak.anySolvedQualifies) DailyStreakChip(state.streak)
                        DailyEntryRow(
                            entries = state.entries,
                            gameplayAllowed = gameplayAllowed,
                            onStart = onStart,
                        )
                        AnimatedVisibility(
                            visible = state.completion != null,
                            enter = fadeIn(tween(LogicaMotion.SCREEN_MILLIS)),
                            exit = fadeOut(tween(LogicaMotion.SHORT_MILLIS)),
                        ) {
                            state.completion?.let { completion -> DailyCompletionCard(completion) }
                        }
                    }
                }
            }
        }
    }
}

private fun TodayUiState.presentationKey(): String =
    when (this) {
        TodayUiState.Loading -> "loading"
        is TodayUiState.Error -> "error"
        is TodayUiState.Content -> "content"
    }

@Composable
private fun DailyLoading() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(INDICATOR_SIZE))
        SupportingText(stringResource(R.string.daily_loading))
    }
}

@Composable
private fun DailyError(
    reason: TodayError,
    onRetryLoad: () -> Unit,
) {
    LogicaCard(verticalSpacing = LogicaSpacing.text) {
        SupportingText(
            stringResource(
                when (reason) {
                    TodayError.LOAD -> R.string.daily_load_error
                    TodayError.START -> R.string.daily_start_error
                },
            ),
        )
        TextButton(onClick = onRetryLoad) { Text(stringResource(R.string.retry)) }
    }
}

/** Title, date, and the whole run's progress on two lines, so the cards start high on the screen. */
@Composable
private fun DailyHeader(uiState: TodayUiState.Content) {
    val completed = uiState.completedCount
    val total = uiState.totalCount
    val fraction by animateFloatAsState(progressFraction(completed, total), label = "dailyProgress")
    val description = stringResource(R.string.daily_progress_description, completed, total)
    Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                ScreenTitle(stringResource(R.string.daily_challenge))
                SupportingText(
                    uiState.definition.challengeDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                )
            }
            MetricValue(
                text = stringResource(R.string.daily_progress_short, completed, total),
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(PROGRESS_HEIGHT).clearAndSetSemantics {},
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/**
 * One line of streak semantics, deliberately separate from the progress bar above it: before the
 * first solved game it says what keeps the streak, afterwards it says the day is already secured —
 * without waiting for 5/5 and without hiding the four entries that are still playable.
 */
@Composable
private fun DailyStreakChip(streak: TodayStreakUiState) {
    val palette = LocalLogicaPalette.current
    StatusChip(
        icon = if (streak.qualifiedToday) Icons.Filled.LocalFireDepartment else Icons.Filled.Bolt,
        label =
            stringResource(
                if (streak.qualifiedToday) R.string.daily_streak_secured else R.string.daily_streak_hint,
            ),
        containerColor =
            if (streak.qualifiedToday) palette.successContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor =
            if (streak.qualifiedToday) palette.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animateLabel = true,
    )
}

@Composable
private fun DailyEntryRow(
    entries: List<TodayEntryUiState>,
    gameplayAllowed: Boolean,
    onStart: (PuzzleType) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        contentPadding = PaddingValues(vertical = LogicaSpacing.text),
    ) {
        items(entries, key = { it.puzzleType }) { entry ->
            DailyEntryCard(
                entry = entry,
                gameplayAllowed = gameplayAllowed,
                onStart = { onStart(entry.puzzleType) },
            )
        }
    }
}

/**
 * One Daily puzzle as a compact rectangle. The card itself is the action — Start or Retry, whichever
 * its state calls for — so nothing but the state chip competes for the tap, and a finished entry
 * simply stops being actionable. An unfinished attempt is transient, so there is no Continue.
 */
@Composable
private fun DailyEntryCard(
    entry: TodayEntryUiState,
    gameplayAllowed: Boolean,
    onStart: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalLogicaPalette.current
    val isCompleted = entry.state == DailyEntryState.COMPLETED
    val actionable = entry.state.isActionable(gameplayAllowed)
    val actionLabel = entry.state.primaryActionResource()?.let { stringResource(it) }
    LogicaCard(
        modifier = Modifier.width(DAILY_CARD_WIDTH).heightIn(min = DAILY_CARD_MIN_HEIGHT),
        containerColor = if (isCompleted) colors.surfaceContainer else colors.surfaceContainerLow,
        // Non-color cue: an entry waiting for another attempt is the only outlined card, and every
        // state also carries its own status chip icon and label.
        border = if (entry.state == DailyEntryState.RETRY) BorderStroke(1.dp, colors.primary) else null,
        verticalSpacing = LogicaSpacing.text,
        contentPadding = LogicaSpacing.cardContent,
        onClick = if (actionable) onStart else null,
        onClickLabel = actionLabel,
    ) {
        PuzzleArtwork(entry.puzzleType)
        Text(
            text = stringResource(entry.puzzleType.titleResource()),
            style = MaterialTheme.typography.titleMedium,
        )
        SupportingText(difficultyLabel(entry.puzzleType, entry.difficulty))
        EntryStatusChip(entry.state, isCompleted, palette.successContainer, palette.onSuccessContainer)
    }
}

@Composable
private fun EntryStatusChip(
    state: DailyEntryState,
    isCompleted: Boolean,
    successContainer: Color,
    onSuccessContainer: Color,
) {
    val icon: ImageVector
    val labelResource: Int
    when (state) {
        DailyEntryState.AVAILABLE -> {
            icon = Icons.Filled.PlayCircleOutline
            labelResource = R.string.daily_available
        }
        DailyEntryState.RETRY -> {
            icon = Icons.Filled.HighlightOff
            labelResource = R.string.daily_entry_failed
        }
        DailyEntryState.COMPLETED -> {
            icon = Icons.Filled.CheckCircle
            labelResource = R.string.daily_entry_solved
        }
    }
    StatusChip(
        icon = icon,
        label = stringResource(labelResource),
        containerColor =
            if (isCompleted) successContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor =
            if (isCompleted) onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animateLabel = true,
    )
}

/** The completed Daily, kept to its results, the streak, and the unchanged spoiler-free Share. */
@Composable
private fun DailyCompletionCard(completion: TodayCompletionUiState) {
    val context = LocalContext.current
    CompletionCard(
        icon = Icons.Filled.CheckCircle,
        title = stringResource(R.string.daily_completed),
    ) {
        completion.resultSummary?.entries?.forEach { entry -> DailyResultRow(entry) }
        LabelledValue(stringResource(R.string.current_daily_streak), completion.currentStreak.toString())
        LabelledValue(stringResource(R.string.best_daily_streak), completion.bestStreak.toString())
        completion.hintsUsed?.let { hintsUsed ->
            LabelledValue(stringResource(R.string.total_hints_used), hintsUsed.toString())
        }
        // Sharing is only offered once a full, policy-matched result set is confirmed available;
        // a completed run with a missing/mismatched result keeps the rest of this card as-is.
        completion.resultSummary?.let { summary ->
            val shareDescription = stringResource(R.string.share_daily_result_description)
            CompletionActions {
                Button(
                    onClick = { context.shareDailyResult(summary) },
                    modifier = Modifier.semantics { contentDescription = shareDescription },
                ) {
                    Text(stringResource(R.string.share_daily_result))
                }
            }
        }
    }
}

@Composable
private fun DailyResultRow(entry: DailyPuzzleResultSummary) {
    val label = stringResource(entry.puzzleType.titleResource())
    val valueText: String
    val description: String
    if (entry.puzzleType == PuzzleType.WORD) {
        if (entry.outcome == GameOutcome.SOLVED) {
            val attemptsUsed = entry.attemptsUsed ?: 0
            valueText = stringResource(R.string.daily_result_word_attempts, attemptsUsed, WordRules.MAXIMUM_ATTEMPTS)
            description = stringResource(R.string.daily_result_word_solved_description, label, attemptsUsed)
        } else {
            valueText = stringResource(R.string.daily_result_word_failed)
            description = stringResource(R.string.daily_result_word_failed_description, label)
        }
    } else {
        valueText = stringResource(R.string.daily_result_solved)
        description = stringResource(R.string.daily_result_solved_description, label)
    }
    LabelledValue(
        label = label,
        value = valueText,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    )
}

/** The only place an Android [Intent] gets built; [DailyShareFormatter] itself stays plain Kotlin. */
private fun Context.shareDailyResult(summary: DailyResultSummary) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, DailyShareFormatter.format(summary))
        }
    startActivity(Intent.createChooser(sendIntent, null))
}

private val PROGRESS_HEIGHT = 6.dp
private val INDICATOR_SIZE = 18.dp
private val DAILY_CARD_WIDTH = 156.dp

/** A minimum, never a fixed height: the card grows with a larger font scale instead of clipping. */
private val DAILY_CARD_MIN_HEIGHT = 168.dp
