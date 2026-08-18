package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.balance.BalanceGameError
import com.stanisryz.logica.balance.BalanceGameUiState
import com.stanisryz.logica.balance.BalanceGameViewModel
import com.stanisryz.logica.balance.BalanceGameViewModelFactory
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.catalog.levelNumberOrNull
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.ui.balance.BalanceGameContent
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LeaveLevelGuard
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.PuzzleTerminalDialog
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ZeroLivesCard

@Composable
internal fun BalanceGameRoute(
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
            BalanceGameViewModelFactory(launch, attemptFactory, completionRepository, economyRepository)
        }
    val gameViewModel: BalanceGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()
    // Unfinished levels are not saved, so the shell confirms before a live board is thrown away.
    LeaveLevelGuard(exitGuard, (uiState as? BalanceGameUiState.Ready)?.hasMeaningfulProgress == true)
    // Every way out of a finished attempt goes through the shell's terminal gate, which is where an
    // optional interstitial fits between the tap and the action itself.
    BalanceGameScreen(
        uiState,
        economy,
        launch.levelNumberOrNull(),
        gameViewModel::onCellTapped,
        gameViewModel::selectValue,
        gameViewModel::togglePencilMode,
        gameViewModel::requestHint,
        { onTerminalAction(gameViewModel::retry) },
        gameViewModel::retryCompletion,
        onRestoreLife,
        hapticsEnabled,
        onBack,
        { onTerminalAction(onNextLevel) },
        { onTerminalAction(onGameHub) },
        launch is GameAttemptLaunch.Daily,
        modifier,
    )
}

@Composable
private fun BalanceGameScreen(
    uiState: BalanceGameUiState,
    economy: PlayerEconomy,
    levelNumber: Int?,
    onCellTapped: (BalancePosition) -> Unit,
    onSelectValue: (BalanceCell) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetryLevel: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        BalanceGameUiState.Loading -> LoadingState(modifier, stringResource(R.string.creating_puzzle))
        is BalanceGameUiState.Error ->
            RetryableErrorState(
                message =
                    stringResource(
                        when (uiState.reason) {
                            BalanceGameError.LEVEL_UNAVAILABLE -> R.string.level_content_error
                            BalanceGameError.GENERATION -> R.string.puzzle_generation_error
                        },
                    ),
                retryLabel = stringResource(R.string.to_games),
                onRetry = onGameHub,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.back),
                onSecondary = onBack,
            )
        is BalanceGameUiState.Ready ->
            ReadyState(
                uiState.puzzle,
                uiState.game,
                uiState.puzzle.id.difficulty,
                levelNumber,
                uiState.selectedValue,
                uiState.isPencilMode,
                uiState.isHintLoading,
                uiState.completionPersistence,
                economy,
                onCellTapped,
                onSelectValue,
                onTogglePencil,
                onHint,
                onRetryLevel,
                onRetryCompletion,
                onRestoreLife,
                hapticsEnabled,
                onNextLevel,
                onGameHub,
                isDaily,
                modifier,
            )
    }
}

@Composable
private fun ReadyState(
    puzzle: BalancePuzzle,
    game: BalanceGameState,
    difficulty: Difficulty,
    levelNumber: Int?,
    selectedValue: BalanceCell,
    isPencilMode: Boolean,
    isHintLoading: Boolean,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onCellTapped: (BalancePosition) -> Unit,
    onSelectValue: (BalanceCell) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetryLevel: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    val view = LocalView.current
    var previouslyConflicted by remember { mutableStateOf(game.violations.isNotEmpty()) }
    var previousStatus by remember { mutableStateOf(game.status) }
    LaunchedEffect(game.violations) {
        if (hapticsEnabled && !previouslyConflicted && game.violations.isNotEmpty()) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        previouslyConflicted = game.violations.isNotEmpty()
    }
    LaunchedEffect(game.status) {
        if (hapticsEnabled && previousStatus != game.status) {
            when (game.status) {
                BalanceGameStatus.SOLVED -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                BalanceGameStatus.FAILED -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                BalanceGameStatus.IN_PROGRESS -> Unit
            }
        }
        previousStatus = game.status
    }

    BalanceGameContent(
        puzzle = puzzle,
        game = game,
        difficulty = difficulty,
        levelNumber = levelNumber,
        selectedValue = selectedValue,
        isPencilMode = isPencilMode,
        isHintLoading = isHintLoading,
        gameplayEnabled = economy.isGameplayAllowed,
        onCellTapped = {
            if (economy.isGameplayAllowed) {
                if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCellTapped(it)
            }
        },
        onSelectValue = onSelectValue,
        onTogglePencil = onTogglePencil,
        onHint = onHint,
        modifier = modifier,
        hostStatusContent = {
            // The board stays visible at zero lives; Android economy policy only disables actions.
            ZeroLivesCard(economy, onRestoreLife)
        },
    )
    if (game.status.isTerminal) {
        PuzzleTerminalDialog(
            isSolved = game.status == BalanceGameStatus.SOLVED,
            completionPersistence = completionPersistence,
            hintsUsed = game.hintsUsed,
            maxMistakes = PuzzleMistakes.MAX_MISTAKES,
            lives = economy.lives,
            difficulty = difficulty,
            isRetryAllowed = economy.isGameplayAllowed,
            isDaily = isDaily,
            onRetryCompletion = onRetryCompletion,
            onRetryLevel = onRetryLevel,
            onNextLevel = onNextLevel,
            onGameHub = onGameHub,
        )
    }
}
