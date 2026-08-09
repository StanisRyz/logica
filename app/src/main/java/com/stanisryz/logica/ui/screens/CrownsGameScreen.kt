package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.crowns.CrownsGameContext
import com.stanisryz.logica.crowns.CrownsGameError
import com.stanisryz.logica.crowns.CrownsGameLaunch
import com.stanisryz.logica.crowns.CrownsGameUiState
import com.stanisryz.logica.crowns.CrownsGameViewModel
import com.stanisryz.logica.crowns.CrownsGameViewModelFactory
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsHint
import com.stanisryz.logica.puzzle.core.crowns.CrownsHintAction
import com.stanisryz.logica.puzzle.core.crowns.CrownsLogicTechnique
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.crowns.CrownsViolationType
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.ui.components.BodyText
import com.stanisryz.logica.ui.components.DifficultyBadge
import com.stanisryz.logica.ui.components.GameAction
import com.stanisryz.logica.ui.components.GameActionBar
import com.stanisryz.logica.ui.components.GameMessage
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.PuzzleSolvedDialog
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.SupportingText
import com.stanisryz.logica.ui.components.difficultyLabel
import com.stanisryz.logica.ui.crowns.CrownsBoard
import com.stanisryz.logica.ui.theme.LogicaSpacing

@Composable
internal fun CrownsGameRoute(
    launch: CrownsGameLaunch,
    sessionRepository: GameSessionRepository,
    completionRepository: GameCompletionRepository,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(launch, sessionRepository, completionRepository) {
            CrownsGameViewModelFactory(launch, sessionRepository, completionRepository)
        }
    val gameViewModel: CrownsGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()

    CrownsGameScreen(
        uiState = uiState,
        onCellTapped = gameViewModel::onCellTapped,
        onUndo = gameViewModel::undo,
        onHint = gameViewModel::requestHint,
        onReset = gameViewModel::reset,
        onRetryCompletion = gameViewModel::retryCompletion,
        hapticsEnabled = hapticsEnabled,
        onBack = onBack,
        onNewPuzzle = onNewPuzzle,
        onStartNew = onStartNew,
        onCatalog = onCatalog,
        onToday = onToday,
        isDaily = launch.context is CrownsGameContext.Daily,
        modifier = modifier,
    )
}

@Composable
private fun CrownsGameScreen(
    uiState: CrownsGameUiState,
    onCellTapped: (CrownsPosition) -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onReset: () -> Unit,
    onRetryCompletion: () -> Unit,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
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
                            CrownsGameError.MISSING_SAVED_SESSION -> R.string.missing_saved_game
                            CrownsGameError.INVALID_SAVED_SESSION -> R.string.invalid_saved_game
                            CrownsGameError.GENERATION -> R.string.puzzle_generation_error
                        },
                    ),
                retryLabel = stringResource(if (isDaily) R.string.to_today else R.string.try_another),
                onRetry = if (isDaily) onToday else onStartNew,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.back),
                onSecondary = onBack,
            )
        is CrownsGameUiState.Ready ->
            CrownsReadyState(
                puzzle = uiState.puzzle,
                game = uiState.game,
                difficulty = uiState.puzzle.id.difficulty,
                isHintLoading = uiState.isHintLoading,
                completionPersistence = uiState.completionPersistence,
                onCellTapped = onCellTapped,
                onUndo = onUndo,
                onHint = onHint,
                onReset = onReset,
                onRetryCompletion = onRetryCompletion,
                hapticsEnabled = hapticsEnabled,
                onNewPuzzle = { onNewPuzzle(uiState.puzzle.id.difficulty) },
                onCatalog = onCatalog,
                onToday = onToday,
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
    isHintLoading: Boolean,
    completionPersistence: CompletionPersistence,
    onCellTapped: (CrownsPosition) -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onReset: () -> Unit,
    onRetryCompletion: () -> Unit,
    hapticsEnabled: Boolean,
    onNewPuzzle: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }
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
        if (hapticsEnabled && previousStatus != CrownsGameStatus.SOLVED && game.status == CrownsGameStatus.SOLVED) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        previousStatus = game.status
    }

    ScreenColumn(
        modifier = modifier,
        verticalSpacing = LogicaSpacing.item,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DifficultyBadge(difficultyLabel(PuzzleType.CROWNS, difficulty))
        CrownsBoard(
            puzzle = puzzle,
            game = game,
            onCellTapped = { position ->
                if (hapticsEnabled) {
                    val feedback =
                        when (game.cellAt(position)) {
                            CrownsPlayerCell.EMPTY -> HapticFeedbackConstants.KEYBOARD_TAP
                            CrownsPlayerCell.MARKED -> HapticFeedbackConstants.CLOCK_TICK
                            CrownsPlayerCell.CROWN -> HapticFeedbackConstants.KEYBOARD_TAP
                        }
                    view.performHapticFeedback(feedback)
                }
                onCellTapped(position)
            },
        )
        SupportingText(stringResource(R.string.crowns_tap_policy), modifier = Modifier.fillMaxWidth())
        game.currentHint?.let { hint -> CrownsHintCard(hint) }
        GameMessage(game.violations.firstOrNull()?.let { crownsViolationText(it.type) })
        GameActionBar(
            listOf(
                GameAction(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = stringResource(R.string.undo),
                    enabled = game.moveHistory.isNotEmpty(),
                    onClick = onUndo,
                ),
                GameAction(
                    icon = Icons.Filled.Lightbulb,
                    label = stringResource(R.string.hint),
                    enabled = !isHintLoading && game.status == CrownsGameStatus.IN_PROGRESS,
                    onClick = onHint,
                ),
                GameAction(
                    icon = Icons.Filled.Refresh,
                    label = stringResource(R.string.reset),
                    enabled = true,
                    onClick = { if (game.moveHistory.isEmpty()) onReset() else showResetConfirmation = true },
                ),
            ),
        )
        if (isHintLoading) SupportingText(stringResource(R.string.searching_hint))
    }

    if (showResetConfirmation) {
        ResetConfirmationDialog(
            onConfirm = {
                showResetConfirmation = false
                onReset()
            },
            onDismiss = { showResetConfirmation = false },
        )
    }
    if (game.status == CrownsGameStatus.SOLVED) {
        PuzzleSolvedDialog(
            completionPersistence = completionPersistence,
            hintsUsed = game.hintsUsed,
            isDaily = isDaily,
            onRetryCompletion = onRetryCompletion,
            onNewPuzzle = onNewPuzzle,
            onCatalog = onCatalog,
            onToday = onToday,
        )
    }
}

@Composable
private fun CrownsHintCard(hint: CrownsHint) {
    LogicaCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        verticalSpacing = LogicaSpacing.text,
    ) {
        BodyText(hint.crownsPresentationText())
        SupportingText(stringResource(R.string.crowns_hint_legend))
    }
}

@Composable
private fun CrownsHint.crownsPresentationText(): String {
    val firstTarget = targetPositions.sortedWith(compareBy(CrownsPosition::row, CrownsPosition::column)).first()
    return when (action) {
        CrownsHintAction.CLEAR_CROWN ->
            stringResource(R.string.crowns_hint_incorrect_crown, firstTarget.row + 1, firstTarget.column + 1)
        CrownsHintAction.CLEAR_MARK ->
            stringResource(R.string.crowns_hint_incorrect_mark, firstTarget.row + 1, firstTarget.column + 1)
        CrownsHintAction.PLACE_CROWN ->
            stringResource(
                R.string.crowns_hint_place,
                firstTarget.row + 1,
                firstTarget.column + 1,
                technique.crownsPresentationText(),
            )
        CrownsHintAction.MARK_POSITIONS ->
            stringResource(
                R.string.crowns_hint_mark,
                targetPositions.size,
                technique.crownsPresentationText(),
            )
    }
}

@Composable
private fun CrownsLogicTechnique?.crownsPresentationText(): String =
    stringResource(
        when (this) {
            CrownsLogicTechnique.SINGLE_CANDIDATE_ROW -> R.string.crowns_technique_single_row
            CrownsLogicTechnique.SINGLE_CANDIDATE_COLUMN -> R.string.crowns_technique_single_column
            CrownsLogicTechnique.SINGLE_CANDIDATE_REGION -> R.string.crowns_technique_single_region
            CrownsLogicTechnique.REGION_LOCKED_TO_ROW -> R.string.crowns_technique_region_row
            CrownsLogicTechnique.REGION_LOCKED_TO_COLUMN -> R.string.crowns_technique_region_column
            null -> R.string.empty
        },
    )

@Composable
private fun crownsViolationText(type: CrownsViolationType): String =
    stringResource(
        when (type) {
            CrownsViolationType.POSITION_OUTSIDE_BOARD -> R.string.crowns_violation_position
            CrownsViolationType.ROW_CONFLICT -> R.string.crowns_violation_row
            CrownsViolationType.COLUMN_CONFLICT -> R.string.crowns_violation_column
            CrownsViolationType.REGION_CONFLICT -> R.string.crowns_violation_region
            CrownsViolationType.DIAGONAL_ADJACENCY_CONFLICT -> R.string.crowns_violation_diagonal
        },
    )
