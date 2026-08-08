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
import com.stanisryz.logica.balance.BalanceGameLaunch
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.TodayError
import com.stanisryz.logica.daily.TodayUiState
import com.stanisryz.logica.daily.TodayViewModel
import com.stanisryz.logica.daily.TodayViewModelFactory
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.statistics.StatisticsRepository
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun TodayRoute(
    dailyChallengeRepository: DailyChallengeRepository,
    gameSessionRepository: GameSessionRepository,
    statisticsRepository: StatisticsRepository,
    tutorialCompleted: Boolean,
    onOpenDaily: (BalanceGameLaunch) -> Unit,
    onOpenTutorial: () -> Unit,
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
        tutorialCompleted = tutorialCompleted,
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
    tutorialCompleted: Boolean,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onOpenTutorial: () -> Unit,
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
        is TodayUiState.WithDefinition ->
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
    uiState: TodayUiState.WithDefinition,
    tutorialCompleted: Boolean,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    onOpenTutorial: () -> Unit,
    modifier: Modifier,
) {
    val definition = uiState.definition
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.daily_challenge), style = MaterialTheme.typography.headlineMedium)
        Text(definition.formattedDate(), style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.balance), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(
                        R.string.difficulty_value,
                        definition
                            .entries
                            .single()
                            .difficulty
                            .russianLabel(),
                    ),
                )
                when (uiState) {
                    is TodayUiState.Available -> {
                        Text(stringResource(R.string.daily_available))
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.start))
                        }
                    }
                    is TodayUiState.InProgress -> {
                        Text(stringResource(R.string.daily_in_progress))
                        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.continue_game))
                        }
                    }
                    is TodayUiState.Completed -> {
                        Text(
                            stringResource(R.string.daily_completed),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        uiState.hintsUsed?.let { hintsUsed ->
                            Text(stringResource(R.string.hints_used, hintsUsed))
                        }
                        Text(stringResource(R.string.current_daily_streak_value, uiState.currentStreak))
                        Text(stringResource(R.string.best_daily_streak_value, uiState.bestStreak))
                    }
                }
            }
        }

        if (!tutorialCompleted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.balance_tutorial_offer_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.daily_tutorial_recommendation))
                    TextButton(onClick = onOpenTutorial) { Text(stringResource(R.string.how_to_play)) }
                }
            }
        }
    }
}

private fun DailyChallengeDefinition.formattedDate(): String = challengeDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
