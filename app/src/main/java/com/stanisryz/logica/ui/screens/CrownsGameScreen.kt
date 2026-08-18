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
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.catalog.levelNumberOrNull
import com.stanisryz.logica.crowns.CrownsGameError
import com.stanisryz.logica.crowns.CrownsGameUiState
import com.stanisryz.logica.crowns.CrownsGameViewModel
import com.stanisryz.logica.crowns.CrownsGameViewModelFactory
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LeaveLevelGuard
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.PuzzleTerminalDialog
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.crowns.CrownsGameContent

@Composable
internal fun CrownsGameRoute(
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
            CrownsGameViewModelFactory(launch, attemptFactory, completionRepository, economyRepository)
        }
    val gameViewModel: CrownsGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()
    LeaveLevelGuard(exitGuard, (uiState as? CrownsGameUiState.Ready)?.hasMeaningfulProgress == true)

    CrownsGameScreen(
        uiState = uiState,
        economy = economy,
        levelNumber = launch.levelNumberOrNull(),
        onCellTapped = gameViewModel::onCellTapped,
        onSelectValue = gameViewModel::selectValue,
        onTogglePencil = gameViewModel::togglePencilMode,
        onHint = gameViewModel::requestHint,
        onRetryLevel = { onTerminalAction(gameViewModel::retry) },
        onRetryCompletion = gameViewModel::retryCompletion,
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
private fun CrownsGameScreen(
    uiState: CrownsGameUiState,
    economy: PlayerEconomy,
    levelNumber: Int?,
    onCellTapped: (CrownsPosition) -> Unit,
    onSelectValue: (CrownsPlayerCell) -> Unit,
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
    modifier: Modifier,
) {
    when (uiState) {
        CrownsGameUiState.Loading -> LoadingState(modifier, stringResource(R.string.creating_puzzle))
        is CrownsGameUiState.Error ->
            RetryableErrorState(
                message =
                    stringResource(
                        when (uiState.reason) {
                            CrownsGameError.LEVEL_UNAVAILABLE -> R.string.level_content_error
                            CrownsGameError.GENERATION -> R.string.puzzle_generation_error
                        },
                    ),
                retryLabel = stringResource(R.string.to_games),
                onRetry = onGameHub,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.back),
                onSecondary = onBack,
            )
        is CrownsGameUiState.Ready ->
            CrownsReadyState(
                puzzle = uiState.puzzle,
                game = uiState.game,
                difficulty = uiState.puzzle.id.difficulty,
                levelNumber = levelNumber,
                selectedValue = uiState.selectedValue,
                isPencilMode = uiState.isPencilMode,
                isHintLoading = uiState.isHintLoading,
                completionPersistence = uiState.completionPersistence,
                economy = economy,
                onCellTapped = onCellTapped,
                onSelectValue = onSelectValue,
                onTogglePencil = onTogglePencil,
                onHint = onHint,
                onRetryLevel = onRetryLevel,
                onRetryCompletion = onRetryCompletion,
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
private fun CrownsReadyState(
    puzzle: CrownsPuzzle,
    game: CrownsGameState,
    difficulty: Difficulty,
    levelNumber: Int?,
    selectedValue: CrownsPlayerCell,
    isPencilMode: Boolean,
    isHintLoading: Boolean,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onCellTapped: (CrownsPosition) -> Unit,
    onSelectValue: (CrownsPlayerCell) -> Unit,
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
                CrownsGameStatus.SOLVED -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                CrownsGameStatus.FAILED -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                CrownsGameStatus.IN_PROGRESS -> Unit
            }
        }
        previousStatus = game.status
    }

    CrownsGameContent(
        puzzle = puzzle,
        game = game,
        difficulty = difficulty,
        levelNumber = levelNumber,
        selectedValue = selectedValue,
        isPencilMode = isPencilMode,
        isHintLoading = isHintLoading,
        gameplayEnabled = economy.isGameplayAllowed,
        onCellTapped = { position ->
            if (economy.isGameplayAllowed) {
                if (hapticsEnabled) {
                    val feedback =
                        if (isPencilMode) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP
                    view.performHapticFeedback(feedback)
                }
                onCellTapped(position)
            }
        },
        onSelectValue = onSelectValue,
        onTogglePencil = onTogglePencil,
        onHint = onHint,
        modifier = modifier,
        hostStatusContent = {
            // The saved puzzle stays visible and intact at zero lives; only the actions stop working.
            ZeroLivesCard(economy, onRestoreLife)
        },
    )

    if (game.status.isTerminal) {
        PuzzleTerminalDialog(
            isSolved = game.status == CrownsGameStatus.SOLVED,
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
