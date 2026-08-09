package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyEntryState
import com.stanisryz.logica.daily.DailyGameLaunch
import com.stanisryz.logica.daily.TodayEntryUiState
import com.stanisryz.logica.daily.TodayError
import com.stanisryz.logica.daily.TodayUiState
import com.stanisryz.logica.daily.TodayViewModel
import com.stanisryz.logica.daily.TodayViewModelFactory
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.statistics.StatisticsRepository
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun TodayRoute(
    dailyChallengeRepository: DailyChallengeRepository,
    gameSessionRepository: GameSessionRepository,
    statisticsRepository: StatisticsRepository,
    balanceTutorialCompleted: Boolean,
    crownsTutorialCompleted: Boolean,
    wordTutorialCompleted: Boolean,
    onOpenDaily: (DailyGameLaunch) -> Unit,
    onOpenTutorial: (PuzzleType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(dailyChallengeRepository, gameSessionRepository, statisticsRepository) {
            TodayViewModelFactory(dailyChallengeRepository, gameSessionRepository, statisticsRepository)
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
        TodayUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        is TodayUiState.Error ->
            Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(
                            when (uiState.reason) {
                                TodayError.LOAD -> R.string.daily_load_error
                                TodayError.START -> R.string.daily_start_error
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
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
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.daily_challenge), style = MaterialTheme.typography.headlineMedium)
        Text(
            uiState.definition.challengeDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.daily_progress, uiState.completedCount, uiState.totalCount))

        uiState.entries.forEach { entry ->
            TodayEntryCard(
                entry = entry,
                tutorialCompleted = tutorialCompleted(entry.puzzleType),
                onStart = { onStart(entry.puzzleType) },
                onContinue = { onContinue(entry.puzzleType) },
                onOpenTutorial = { onOpenTutorial(entry.puzzleType) },
            )
        }

        uiState.completion?.let { completion ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.daily_completed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    completion.hintsUsed?.let { hintsUsed -> Text(stringResource(R.string.hints_used, hintsUsed)) }
                    Text(stringResource(R.string.current_daily_streak_value, completion.currentStreak))
                    Text(stringResource(R.string.best_daily_streak_value, completion.bestStreak))
                }
            }
        }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(entry.puzzleType.titleResource()), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.difficulty_value, entry.difficulty.russianLabel()))
            when (entry.state) {
                DailyEntryState.AVAILABLE -> {
                    Text(stringResource(R.string.daily_available))
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.start))
                    }
                }
                DailyEntryState.IN_PROGRESS -> {
                    Text(stringResource(R.string.daily_in_progress))
                    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.continue_game))
                    }
                }
                DailyEntryState.COMPLETED ->
                    // A Word entry completes on SOLVED or FAILED, so its label stays outcome-neutral.
                    Text(
                        stringResource(
                            if (entry.puzzleType == PuzzleType.WORD) R.string.word_daily_entry_done else R.string.puzzle_solved,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
            }
            if (!tutorialCompleted && entry.state != DailyEntryState.COMPLETED) {
                Text(
                    stringResource(R.string.daily_tutorial_recommendation),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onOpenTutorial) { Text(stringResource(R.string.how_to_play)) }
            }
        }
    }
}

private fun PuzzleType.titleResource(): Int =
    when (this) {
        PuzzleType.BALANCE -> R.string.balance
        PuzzleType.CROWNS -> R.string.crowns
        PuzzleType.WORD -> R.string.word
        else -> error("Daily does not support $this yet.")
    }
