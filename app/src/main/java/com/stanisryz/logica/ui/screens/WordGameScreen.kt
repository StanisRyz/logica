package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.stanisryz.logica.ui.components.GameResultCard
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LeaveLevelGuard
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.resultEconomy
import com.stanisryz.logica.ui.components.russianLabel
import com.stanisryz.logica.ui.components.starsForWordAttempts
import com.stanisryz.logica.ui.components.toResultSaveState
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
                levelNumber = levelNumber,
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
    levelNumber: Int?,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onRetryCompletion: () -> Unit,
    onRetryLevel: () -> Unit,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    isDaily: Boolean,
) {
    val isSolved = game.status == WordGameStatus.SOLVED
    val difficulty = puzzle.id.difficulty
    GameResultCard(
        solved = isSolved,
        levelNumber = levelNumber,
        isDaily = isDaily,
        difficultyLabel = difficulty.russianLabel(),
        saveState = completionPersistence.toResultSaveState(),
        onNextLevel = onNextLevel,
        onRetry = onRetryLevel,
        onRetrySave = onRetryCompletion,
        onExit = onGameHub,
        modifier = Modifier.fillMaxWidth(),
        detail =
            if (isSolved) {
                stringResource(R.string.word_attempts_used, game.attempts.size)
            } else {
                stringResource(R.string.word_answer_was, puzzle.answer.uppercase())
            },
        saveErrorDetail = stringResource(R.string.completion_save_error_body),
        economy = resultEconomy(isSolved, difficulty),
        retryAllowed = economy.isGameplayAllowed,
        stars = if (isSolved) starsForWordAttempts(game.attempts.size) else null,
    )
}
