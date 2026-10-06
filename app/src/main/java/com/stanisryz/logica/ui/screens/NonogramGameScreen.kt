package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.nonogram.NonogramGameUiState
import com.stanisryz.logica.nonogram.NonogramGameViewModel
import com.stanisryz.logica.nonogram.NonogramGameViewModelFactory
import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameStatus
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.HintsExhaustedDialog
import com.stanisryz.logica.ui.components.LeaveLevelGuard
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.LocalSecondChancePending
import com.stanisryz.logica.ui.components.PuzzleTerminalDialog
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.SecondChanceDialog
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.nonogram.NonogramGameContent
import com.stanisryz.logica.ui.nonogram.nonogramResultArtwork

/** The Android host of the shared Nonogram presentation: ViewModel, haptics, economy, and terminal policy. */
@Composable
internal fun NonogramGameRoute(
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
    onOpenStore: () -> Unit = {},
) {
    val factory =
        remember(launch, attemptFactory, completionRepository, economyRepository) {
            NonogramGameViewModelFactory(launch, attemptFactory, completionRepository, economyRepository)
        }
    val gameViewModel: NonogramGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    // The third mistake first offers the one ad-paid second chance; the result waits for the answer.
    val secondChance = (uiState as? NonogramGameUiState.Ready)?.continueOffered == true
    if (secondChance) {
        SecondChanceDialog(onContinue = gameViewModel::continueAfterAd, onDecline = gameViewModel::declineContinue)
    }
    CompositionLocalProvider(LocalSecondChancePending provides secondChance) {
        val economy by gameViewModel.economy.collectAsStateWithLifecycle()
        // Unfinished levels are not saved, so the shell confirms before a live board is thrown away.
        LeaveLevelGuard(exitGuard, (uiState as? NonogramGameUiState.Ready)?.hasMeaningfulProgress == true)
        val levelNumber = launch.levelNumberOrNull()
        val view = LocalView.current

        when (val state = uiState) {
            NonogramGameUiState.Loading -> LoadingState(modifier, stringResource(R.string.creating_puzzle))
            NonogramGameUiState.Error ->
                RetryableErrorState(
                    message = stringResource(R.string.level_content_error),
                    retryLabel = stringResource(R.string.to_games),
                    onRetry = onGameHub,
                    modifier = modifier,
                    secondaryLabel = stringResource(R.string.back),
                    onSecondary = onBack,
                )
            is NonogramGameUiState.Ready -> {
                val game = state.game
                var previousStatus by remember { mutableStateOf(game.status) }
                var previousMistakes by remember { mutableIntStateOf(game.mistakesUsed) }
                LaunchedEffect(game.status, game.mistakesUsed) {
                    if (hapticsEnabled) {
                        when {
                            previousStatus != game.status && game.status == NonogramGameStatus.SOLVED ->
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            previousStatus != game.status && game.status == NonogramGameStatus.FAILED ->
                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                            game.mistakesUsed > previousMistakes -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                    previousStatus = game.status
                    previousMistakes = game.mistakesUsed
                }
                NonogramGameContent(
                    puzzle = state.puzzle,
                    game = game,
                    difficulty = state.puzzle.id.difficulty,
                    levelNumber = levelNumber,
                    selectedTool = state.selectedTool,
                    gameplayEnabled = economy.isGameplayAllowed,
                    onCell = { position ->
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        gameViewModel.onCell(position)
                    },
                    onSelectTool = gameViewModel::selectTool,
                    onHint = gameViewModel::requestHint,
                    modifier = modifier,
                    hintCount = economy.hints,
                    hostStatusContent = {
                        // The board stays visible at zero lives; Android economy policy only disables actions.
                        ZeroLivesCard(economy, onRestoreLife)
                    },
                )
                if (game.status.isTerminal) {
                    PuzzleTerminalDialog(
                        isSolved = game.status == NonogramGameStatus.SOLVED,
                        completionPersistence = state.completionPersistence,
                        gemsEarned = state.gemsEarned,
                        levelNumber = levelNumber,
                        mistakesUsed = game.mistakesUsed,
                        hintsUsed = game.hintsUsed,
                        maxMistakes = PuzzleMistakes.MAX_MISTAKES,
                        difficulty = state.puzzle.id.difficulty,
                        isRetryAllowed = economy.isGameplayAllowed,
                        isDaily = launch is GameAttemptLaunch.Daily,
                        onRetryCompletion = gameViewModel::retryCompletion,
                        onRetryLevel = { onTerminalAction(gameViewModel::retry) },
                        onNextLevel = { onTerminalAction(onNextLevel) },
                        onGameHub = { onTerminalAction(onGameHub) },
                        artwork = nonogramResultArtwork(state.puzzle),
                    )
                }
                if (state.hintsExhausted) {
                    HintsExhaustedDialog(
                        economy = economy,
                        onBuy = gameViewModel::buyHints,
                        onDismiss = gameViewModel::dismissHintsExhausted,
                        onOpenStore = onOpenStore,
                    )
                }
            }
        }
    }
}
