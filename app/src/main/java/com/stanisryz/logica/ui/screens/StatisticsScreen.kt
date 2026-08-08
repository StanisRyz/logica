package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.statistics.GameStatistics
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.statistics.StatisticsUiState
import com.stanisryz.logica.statistics.StatisticsViewModel
import com.stanisryz.logica.statistics.StatisticsViewModelFactory

@Composable
internal fun StatisticsRoute(
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

    StatisticsScreen(uiState, statisticsViewModel::refresh, modifier)
}

@Composable
private fun StatisticsScreen(
    uiState: StatisticsUiState,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    when (uiState) {
        StatisticsUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        StatisticsUiState.Error ->
            Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.statistics_load_error))
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        is StatisticsUiState.Ready -> StatisticsContent(uiState.statistics, modifier)
    }
}

@Composable
private fun StatisticsContent(
    statistics: GameStatistics,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.statistics), style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatisticRow(R.string.total_solved, statistics.totalCompletedResults)
                StatisticRow(R.string.daily_completed_count, statistics.completedDailyCount)
                StatisticRow(R.string.current_daily_streak, statistics.currentDailyStreak)
                StatisticRow(R.string.best_daily_streak, statistics.bestDailyStreak)
                StatisticRow(R.string.total_hints_used, statistics.totalHintsUsed)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.balance), style = MaterialTheme.typography.titleLarge)
                StatisticRow(R.string.total_solved, statistics.totalCompletedBalance)
                Difficulty.entries.forEach { difficulty ->
                    StatisticRow(
                        difficulty.stringResourceId,
                        statistics.balanceCountsByDifficulty.getValue(difficulty),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticRow(
    labelResource: Int,
    value: Int,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(labelResource))
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

private val Difficulty.stringResourceId: Int
    get() =
        when (this) {
            Difficulty.EASY -> R.string.difficulty_easy
            Difficulty.MEDIUM -> R.string.difficulty_medium
            Difficulty.HARD -> R.string.difficulty_hard
            Difficulty.EXPERT -> R.string.difficulty_expert
        }
