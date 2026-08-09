package com.stanisryz.logica.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyEntryState
import com.stanisryz.logica.daily.DailyGameLaunch
import com.stanisryz.logica.daily.DailyPuzzleResultSummary
import com.stanisryz.logica.daily.DailyResultRepository
import com.stanisryz.logica.daily.DailyResultSummary
import com.stanisryz.logica.daily.DailyShareFormatter
import com.stanisryz.logica.daily.TodayCompletionUiState
import com.stanisryz.logica.daily.TodayEntryUiState
import com.stanisryz.logica.daily.TodayError
import com.stanisryz.logica.daily.TodayUiState
import com.stanisryz.logica.daily.TodayViewModel
import com.stanisryz.logica.daily.TodayViewModelFactory
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.ui.components.CompletionActions
import com.stanisryz.logica.ui.components.CompletionCard
import com.stanisryz.logica.ui.components.LabelledValue
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.MetricValue
import com.stanisryz.logica.ui.components.PuzzleTitle
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.ScreenTitle
import com.stanisryz.logica.ui.components.StatusChip
import com.stanisryz.logica.ui.components.SupportingText
import com.stanisryz.logica.ui.components.difficultyLabel
import com.stanisryz.logica.ui.components.progressFraction
import com.stanisryz.logica.ui.components.titleResource
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun TodayRoute(
    dailyChallengeRepository: DailyChallengeRepository,
    gameSessionRepository: GameSessionRepository,
    statisticsRepository: StatisticsRepository,
    dailyResultRepository: DailyResultRepository,
    balanceTutorialCompleted: Boolean,
    crownsTutorialCompleted: Boolean,
    wordTutorialCompleted: Boolean,
    onOpenDaily: (DailyGameLaunch) -> Unit,
    onOpenTutorial: (PuzzleType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(dailyChallengeRepository, gameSessionRepository, statisticsRepository, dailyResultRepository) {
            TodayViewModelFactory(
                dailyChallengeRepository,
                gameSessionRepository,
                statisticsRepository,
                dailyResultRepository,
            )
        }
    val todayViewModel: TodayViewModel = viewModel(factory = factory)
    val uiState by todayViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(todayViewModel, onOpenDaily) {
        todayViewModel.refresh()
        todayViewModel.launches.collect(onOpenDaily)
    }
    DisposableEffect(lifecycleOwner, todayViewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) todayViewModel.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TodayScreen(
        uiState = uiState,
        tutorialCompleted = { puzzleType ->
            when (puzzleType) {
                PuzzleType.BALANCE -> balanceTutorialCompleted
                PuzzleType.CROWNS -> crownsTutorialCompleted
                PuzzleType.WORD -> wordTutorialCompleted
                else -> true
            }
        },
        onStart = todayViewModel::start,
        onContinue = todayViewModel::continueGame,
        onRetry = todayViewModel::refresh,
        onOpenTutorial = onOpenTutorial,
        modifier = modifier,
    )
}

@Composable
private fun TodayScreen(
    uiState: TodayUiState,
    tutorialCompleted: (PuzzleType) -> Boolean,
    onStart: (PuzzleType) -> Unit,
    onContinue: (PuzzleType) -> Unit,
    onRetry: () -> Unit,
    onOpenTutorial: (PuzzleType) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        TodayUiState.Loading -> LoadingState(modifier)
        is TodayUiState.Error ->
            RetryableErrorState(
                message =
                    stringResource(
                        when (uiState.reason) {
                            TodayError.LOAD -> R.string.daily_load_error
                            TodayError.START -> R.string.daily_start_error
                        },
                    ),
                retryLabel = stringResource(R.string.retry),
                onRetry = onRetry,
                modifier = modifier,
            )
        is TodayUiState.Content ->
            TodayContent(
                uiState = uiState,
                tutorialCompleted = tutorialCompleted,
                onStart = onStart,
                onContinue = onContinue,
                onOpenTutorial = onOpenTutorial,
                modifier = modifier,
            )
    }
}

@Composable
private fun TodayContent(
    uiState: TodayUiState.Content,
    tutorialCompleted: (PuzzleType) -> Boolean,
    onStart: (PuzzleType) -> Unit,
    onContinue: (PuzzleType) -> Unit,
    onOpenTutorial: (PuzzleType) -> Unit,
    modifier: Modifier,
) {
    ScreenColumn(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
            ScreenTitle(stringResource(R.string.daily_challenge))
            SupportingText(
                uiState.definition.challengeDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
            )
        }
        DailyProgress(completed = uiState.completedCount, total = uiState.totalCount)

        Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
            uiState.entries.forEach { entry ->
                TodayEntryCard(
                    entry = entry,
                    tutorialCompleted = tutorialCompleted(entry.puzzleType),
                    onStart = { onStart(entry.puzzleType) },
                    onContinue = { onContinue(entry.puzzleType) },
                    onOpenTutorial = { onOpenTutorial(entry.puzzleType) },
                )
            }
        }

        uiState.completion?.let { completion -> DailyCompletionCard(completion) }
    }
}

/** The numeric count stays the headline; the bar is only a compact reinforcement of it. */
@Composable
private fun DailyProgress(
    completed: Int,
    total: Int,
) {
    val fraction by animateFloatAsState(progressFraction(completed, total), label = "dailyProgress")
    val description = stringResource(R.string.daily_progress_description, completed, total)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
    ) {
        MetricValue(stringResource(R.string.daily_progress_short, completed, total))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.weight(1f).height(PROGRESS_HEIGHT),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun TodayEntryCard(
    entry: TodayEntryUiState,
    tutorialCompleted: Boolean,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    onOpenTutorial: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalLogicaPalette.current
    val isCompleted = entry.state == DailyEntryState.COMPLETED
    LogicaCard(
        containerColor = if (isCompleted) colors.surfaceContainer else colors.surfaceContainerLow,
        // Non-color cue: an unfinished-but-started entry is the only outlined card, and every
        // state also carries its own status chip icon and label.
        border =
            if (entry.state == DailyEntryState.IN_PROGRESS) BorderStroke(1.dp, colors.primary) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
            ) {
                PuzzleTitle(stringResource(entry.puzzleType.titleResource()), puzzleType = entry.puzzleType)
                SupportingText(difficultyLabel(entry.puzzleType, entry.difficulty))
            }
            EntryStatusChip(entry.state, isCompleted, palette.successContainer, palette.onSuccessContainer)
        }

        when (entry.state) {
            DailyEntryState.AVAILABLE ->
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.start))
                }
            DailyEntryState.IN_PROGRESS ->
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.continue_game))
                }
            // A Word entry completes on SOLVED or FAILED, so its label stays outcome-neutral.
            DailyEntryState.COMPLETED ->
                if (entry.puzzleType == PuzzleType.WORD) {
                    SupportingText(stringResource(R.string.word_daily_entry_done))
                }
        }

        // Compact and per-entry: the tutorial is offered next to the puzzle it belongs to and
        // disappears for good once it has been completed.
        if (!tutorialCompleted && !isCompleted) {
            TextButton(onClick = onOpenTutorial) { Text(stringResource(R.string.how_to_play)) }
        }
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
        DailyEntryState.IN_PROGRESS -> {
            icon = Icons.Filled.Timelapse
            labelResource = R.string.daily_in_progress
        }
        DailyEntryState.COMPLETED -> {
            icon = Icons.Filled.CheckCircle
            labelResource = R.string.daily_entry_completed
        }
    }
    StatusChip(
        icon = icon,
        label = stringResource(labelResource),
        containerColor =
            if (isCompleted) successContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor =
            if (isCompleted) onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The completed Daily is deliberately unlike an entry card, reusing [CompletionActions] for Share. */
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

private val PROGRESS_HEIGHT = 8.dp
