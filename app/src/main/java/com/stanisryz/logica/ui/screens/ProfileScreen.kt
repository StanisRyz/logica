package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.statistics.GameStatistics
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.statistics.StatisticsUiState
import com.stanisryz.logica.statistics.StatisticsViewModel
import com.stanisryz.logica.statistics.StatisticsViewModelFactory
import com.stanisryz.logica.statistics.WordStatistics
import com.stanisryz.logica.ui.components.AttemptDistributionBars
import com.stanisryz.logica.ui.components.EmptyState
import com.stanisryz.logica.ui.components.LabelledValue
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.Metric
import com.stanisryz.logica.ui.components.MetricGrid
import com.stanisryz.logica.ui.components.PuzzleTitle
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.ScreenSection
import com.stanisryz.logica.ui.components.SectionTitle
import com.stanisryz.logica.ui.components.difficultyResource
import com.stanisryz.logica.ui.components.titleResource
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The Profile tab: a local gameplay profile and nothing more. It is the existing statistics, read
 * through the existing [StatisticsViewModel], presented as a short summary with the detailed
 * per-puzzle breakdown below it. There is no account, no name, and no cloud.
 */
@Composable
internal fun ProfileRoute(
    repository: StatisticsRepository,
    modifier: Modifier = Modifier,
) {
    val factory = remember(repository) { StatisticsViewModelFactory(repository) }
    val statisticsViewModel: StatisticsViewModel = viewModel(factory = factory)
    val uiState by statisticsViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, statisticsViewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) statisticsViewModel.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ProfileScreen(uiState, statisticsViewModel::refresh, modifier)
}

@Composable
private fun ProfileScreen(
    uiState: StatisticsUiState,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    when (uiState) {
        StatisticsUiState.Loading -> LoadingState(modifier)
        StatisticsUiState.Error ->
            RetryableErrorState(
                message = stringResource(R.string.statistics_load_error),
                retryLabel = stringResource(R.string.retry),
                onRetry = onRetry,
                modifier = modifier,
            )
        is StatisticsUiState.Ready ->
            if (uiState.statistics.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.statistics_empty_title),
                    body = stringResource(R.string.statistics_empty_body),
                    modifier = modifier,
                )
            } else {
                ProfileContent(uiState.statistics, modifier)
            }
    }
}

/** Nothing has been completed yet, so every metric would read zero. */
private fun GameStatistics.isEmpty(): Boolean = totalCompletedResults == 0 && completedDailyCount == 0

@Composable
private fun ProfileContent(
    statistics: GameStatistics,
    modifier: Modifier,
) {
    ScreenColumn(modifier) {
        ScreenSection(title = stringResource(R.string.statistics_overall)) {
            MetricGrid(
                listOf(
                    Metric(stringResource(R.string.total_solved), statistics.totalCompletedResults.toString()),
                    Metric(stringResource(R.string.daily_completed_count), statistics.completedDailyCount.toString()),
                    Metric(stringResource(R.string.current_daily_streak), statistics.currentDailyStreak.toString()),
                    Metric(stringResource(R.string.best_daily_streak), statistics.bestDailyStreak.toString()),
                    Metric(stringResource(R.string.total_hints_used), statistics.totalHintsUsed.toString()),
                ),
            )
        }
        ScreenSection(title = stringResource(R.string.statistics)) {
            PuzzleStatisticsCard(PuzzleType.BALANCE, statistics)
            PuzzleStatisticsCard(PuzzleType.CROWNS, statistics)
            WordStatisticsCard(statistics.word)
        }
    }
}

@Composable
private fun PuzzleStatisticsCard(
    puzzleType: PuzzleType,
    statistics: GameStatistics,
) {
    val puzzleStatistics = requireNotNull(statistics.byPuzzleType[puzzleType])
    LogicaCard {
        PuzzleTitle(stringResource(puzzleType.titleResource()), puzzleType = puzzleType)
        LabelledValue(stringResource(R.string.total_solved), puzzleStatistics.totalCompleted.toString())
        Difficulty.entries.forEach { difficulty ->
            LabelledValue(
                label = stringResource(difficulty.difficultyResource()),
                value = puzzleStatistics.countsByDifficulty.getValue(difficulty).toString(),
            )
        }
    }
}

@Composable
private fun WordStatisticsCard(statistics: WordStatistics) {
    LogicaCard {
        PuzzleTitle(stringResource(PuzzleType.WORD.titleResource()), puzzleType = PuzzleType.WORD)
        LabelledValue(stringResource(R.string.word_played), statistics.played.toString())
        LabelledValue(stringResource(R.string.word_solved_count), statistics.solved.toString())
        LabelledValue(stringResource(R.string.word_failed_count), statistics.failed.toString())
        LabelledValue(
            label = stringResource(R.string.word_win_rate),
            value = stringResource(R.string.word_percent_value, statistics.winRatePercent),
        )
        Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
            SectionTitle(stringResource(R.string.word_attempt_distribution))
            AttemptDistributionBars(statistics.solvedAttemptCounts)
        }
    }
}
