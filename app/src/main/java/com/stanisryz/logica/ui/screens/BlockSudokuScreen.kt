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
import com.stanisryz.logica.blocksudoku.BlockSudokuUiState
import com.stanisryz.logica.blocksudoku.BlockSudokuViewModel
import com.stanisryz.logica.blocksudoku.BlockSudokuViewModelFactory
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.catalog.levelNumberOrNull
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuStatus
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.ui.blocksudoku.BlockSudokuContent
import com.stanisryz.logica.ui.components.GameResultDialog
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LeaveLevelGuard
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.resultEconomy
import com.stanisryz.logica.ui.components.russianLabel
import com.stanisryz.logica.ui.components.toResultSaveState

/** The Android host of the shared Block Sudoku presentation: ViewModel, haptics, economy, and terminal policy. */
@Composable
internal fun BlockSudokuRoute(
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
            BlockSudokuViewModelFactory(launch, attemptFactory, completionRepository, economyRepository)
        }
    val gameViewModel: BlockSudokuViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()
    // Unfinished levels are not saved, so the shell confirms before a live board is thrown away.
    LeaveLevelGuard(exitGuard, (uiState as? BlockSudokuUiState.Ready)?.hasMeaningfulProgress == true)
    val levelNumber = launch.levelNumberOrNull()
    val view = LocalView.current

    when (val state = uiState) {
        BlockSudokuUiState.Loading -> LoadingState(modifier, stringResource(R.string.creating_puzzle))
        BlockSudokuUiState.Error ->
            RetryableErrorState(
                message = stringResource(R.string.level_content_error),
                retryLabel = stringResource(R.string.to_games),
                onRetry = onGameHub,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.back),
                onSecondary = onBack,
            )
        is BlockSudokuUiState.Ready -> {
            val game = state.game
            var previousPlacements by remember { mutableStateOf(game.placements) }
            LaunchedEffect(game.placements, game.status) {
                if (hapticsEnabled && game.placements != previousPlacements) {
                    view.performHapticFeedback(
                        when {
                            game.status == BlockSudokuStatus.SOLVED -> HapticFeedbackConstants.CONFIRM
                            game.status == BlockSudokuStatus.FAILED -> HapticFeedbackConstants.REJECT
                            game.lastCleared.isNotEmpty() -> HapticFeedbackConstants.LONG_PRESS
                            else -> HapticFeedbackConstants.KEYBOARD_TAP
                        },
                    )
                }
                previousPlacements = game.placements
            }
            BlockSudokuContent(
                state = game,
                difficulty = launch.difficulty(),
                levelNumber = levelNumber,
                gameplayEnabled = economy.isGameplayAllowed && game.status == BlockSudokuStatus.IN_PROGRESS,
                onPlace = gameViewModel::place,
                modifier = modifier,
                hostStatusContent = {
                    // The board stays visible at zero lives; Android economy policy only disables actions.
                    ZeroLivesCard(economy, onRestoreLife)
                },
            )
            if (game.status.isTerminal) {
                val solved = game.status == BlockSudokuStatus.SOLVED
                GameResultDialog(
                    solved = solved,
                    levelNumber = levelNumber,
                    isDaily = launch is GameAttemptLaunch.Daily,
                    difficultyLabel = launch.difficulty().russianLabel(),
                    saveState = state.completionPersistence.toResultSaveState(),
                    onNextLevel = { onTerminalAction(onNextLevel) },
                    onRetry = { onTerminalAction(gameViewModel::retry) },
                    onRetrySave = gameViewModel::retryCompletion,
                    onExit = { onTerminalAction(onGameHub) },
                    detail = stringResource(R.string.block_sudoku_result_score, game.score),
                    saveErrorDetail = stringResource(R.string.completion_save_error_body),
                    economy = resultEconomy(solved, PuzzleType.BLOCK_SUDOKU, launch.difficulty()),
                    retryAllowed = economy.isGameplayAllowed,
                )
            }
        }
    }
}

private fun GameAttemptLaunch.difficulty() =
    when (this) {
        is GameAttemptLaunch.Level -> levelId.difficulty
        is GameAttemptLaunch.Daily -> difficulty
    }
