package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.catalog.levelNumberOrNull
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGuessRejection
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.ui.components.CompletionActions
import com.stanisryz.logica.ui.components.CompletionCard
import com.stanisryz.logica.ui.components.EconomyResultFeedback
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LeaveLevelGuard
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.word.WordGameContent
import com.stanisryz.logica.word.WordGameError
import com.stanisryz.logica.word.WordGameUiState
import com.stanisryz.logica.word.WordGameViewModel
import com.stanisryz.logica.word.WordGameViewModelFactory

@Composable
internal fun WordGameRoute(
    launch: GameAttemptLaunch,
    attemptFactory: GameAttemptFactory,
    completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    exitGuard: GameplayExitGuard,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
    onTerminalAction: (() -> Unit) -> Unit = { it() },
) {
    val factory =
        remember(launch, attemptFactory, completionRepository, economyRepository) {
            WordGameViewModelFactory(launch, attemptFactory, completionRepository, economyRepository)
        }
    val gameViewModel: WordGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()
    LeaveLevelGuard(exitGuard, (uiState as? WordGameUiState.Ready)?.hasMeaningfulProgress == true)

    WordGameScreen(
        uiState = uiState,
        economy = economy,
        levelNumber = launch.levelNumberOrNull(),
        onLetter = gameViewModel::setLetter,
        onClearLetter = gameViewModel::clearLetter,
        onSubmit = gameViewModel::submit,
        onDismissRejection = gameViewModel::dismissRejection,
        onRetryCompletion = gameViewModel::retryCompletion,
        onRetryLevel = { onTerminalAction(gameViewModel::retry) },
        onRestoreLife = onRestoreLife,
        hapticsEnabled = hapticsEnabled,
        onBack = onBack,
        onNextLevel = { onTerminalAction(onNextLevel) },
        onGameHub = { onTerminalAction(onGameHub) },
        isDaily = launch is GameAttemptLaunch.Daily,
        modifier = modifier,
    )
}

@Composable
private fun WordGameScreen(
    uiState: WordGameUiState,
    economy: PlayerEconomy,
    levelNumber: Int?,
    onLetter: (Int, Char) -> Unit,
    onClearLetter: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismissRejection: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRetryLevel: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    when (uiState) {
        WordGameUiState.Loading -> LoadingState(modifier, stringResource(R.string.creating_puzzle))
        is WordGameUiState.Error ->
            RetryableErrorState(
                message =
                    stringResource(
                        when (uiState.reason) {
                            WordGameError.LEVEL_UNAVAILABLE -> R.string.level_content_error
                            WordGameError.GENERATION -> R.string.puzzle_generation_error
                        },
                    ),
                retryLabel = stringResource(R.string.to_games),
                onRetry = onGameHub,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.back),
                onSecondary = onBack,
            )
        is WordGameUiState.Ready ->
            WordReadyState(
                puzzle = uiState.puzzle,
                game = uiState.game,
                levelNumber = levelNumber,
                rejection = uiState.rejection,
                rejectionRevision = uiState.rejectionRevision,
                acceptedAttemptRevision = uiState.acceptedAttemptRevision,
                completionPersistence = uiState.completionPersistence,
                economy = economy,
                onLetter = onLetter,
                onClearLetter = onClearLetter,
                onSubmit = onSubmit,
                onDismissRejection = onDismissRejection,
                onRetryCompletion = onRetryCompletion,
                onRetryLevel = onRetryLevel,
                onRestoreLife = onRestoreLife,
                hapticsEnabled = hapticsEnabled,
                onNextLevel = onNextLevel,
                onGameHub = onGameHub,
                isDaily = isDaily,
                modifier = modifier,
            )
    }
}

@Composable
private fun WordReadyState(
    puzzle: WordPuzzle,
    game: WordGameState,
    levelNumber: Int?,
    rejection: WordGuessRejection?,
    rejectionRevision: Int,
    acceptedAttemptRevision: Int,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onLetter: (Int, Char) -> Unit,
    onClearLetter: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismissRejection: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRetryLevel: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    val view = LocalView.current
    LaunchedEffect(game.status) {
        if (!hapticsEnabled) return@LaunchedEffect
        when (game.status) {
            WordGameStatus.SOLVED -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            WordGameStatus.FAILED -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            WordGameStatus.IN_PROGRESS -> Unit
        }
    }

    WordGameContent(
        puzzle = puzzle,
        game = game,
        levelNumber = levelNumber,
        rejection = rejection,
        rejectionRevision = rejectionRevision,
        acceptedAttemptRevision = acceptedAttemptRevision,
        gameplayEnabled = economy.isGameplayAllowed,
        onLetter = onLetter,
        onClearLetter = onClearLetter,
        onSubmit = onSubmit,
        onDismissRejection = onDismissRejection,
        modifier = modifier,
        onInputInteraction = {
            if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        },
        onRejectionPresented = {
            if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        },
        hostStatusContent = {
            ZeroLivesCard(economy, onRestoreLife)
        },
        terminalContent = {
            WordTerminalCard(
                puzzle = puzzle,
                game = game,
                completionPersistence = completionPersistence,
                economy = economy,
                onRetryCompletion = onRetryCompletion,
                onRetryLevel = onRetryLevel,
                onNextLevel = onNextLevel,
                onGameHub = onGameHub,
                isDaily = isDaily,
            )
        },
    )
}

@Composable
private fun WordTerminalCard(
    puzzle: WordPuzzle,
    game: WordGameState,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onRetryCompletion: () -> Unit,
    onRetryLevel: () -> Unit,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    isDaily: Boolean,
) {
    val isSolved = game.status == WordGameStatus.SOLVED
    val palette = LocalLogicaPalette.current
    CompletionCard(
        icon = if (isSolved) Icons.Filled.CheckCircle else Icons.Filled.HighlightOff,
        title = stringResource(if (isSolved) R.string.word_solved else R.string.word_failed),
        containerColor = if (isSolved) palette.successContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (isSolved) palette.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer,
    ) {
        if (isSolved) {
            Text(
                stringResource(R.string.word_attempts_used, game.attempts.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                stringResource(R.string.word_answer_was, puzzle.answer.uppercase()),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        when (completionPersistence) {
            CompletionPersistence.Error -> {
                Text(
                    stringResource(R.string.completion_save_error_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetryCompletion) { Text(stringResource(R.string.retry)) }
            }
            CompletionPersistence.Saving ->
                Text(
                    stringResource(R.string.saving_completion),
                    style = MaterialTheme.typography.bodyMedium,
                )
            CompletionPersistence.Saved ->
                EconomyResultFeedback(
                    isSolved = isSolved,
                    lives = economy.lives,
                    difficulty = puzzle.id.difficulty,
                )
            CompletionPersistence.NotRequired -> Unit
        }

        CompletionActions {
            when (completionPersistence) {
                CompletionPersistence.Saved -> {
                    if (!isSolved) {
                        Button(
                            onClick = onRetryLevel,
                            enabled = economy.isGameplayAllowed,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.retry_puzzle))
                        }
                        TextButton(onClick = onGameHub) { Text(stringResource(R.string.to_games)) }
                    } else if (isDaily) {
                        Button(onClick = onGameHub, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.to_games))
                        }
                    } else {
                        Button(onClick = onNextLevel, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.next_level))
                        }
                        TextButton(onClick = onGameHub) { Text(stringResource(R.string.to_games)) }
                    }
                }
                CompletionPersistence.Saving -> {
                    TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.saving)) }
                }
                CompletionPersistence.Error,
                CompletionPersistence.NotRequired,
                -> Unit
            }
        }
    }
}
