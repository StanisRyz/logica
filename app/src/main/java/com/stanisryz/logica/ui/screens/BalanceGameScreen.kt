package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.stanisryz.logica.balance.BalanceGameContext
import com.stanisryz.logica.balance.BalanceGameError
import com.stanisryz.logica.balance.BalanceGameLaunch
import com.stanisryz.logica.balance.BalanceGameUiState
import com.stanisryz.logica.balance.BalanceGameViewModel
import com.stanisryz.logica.balance.BalanceGameViewModelFactory
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceHint
import com.stanisryz.logica.puzzle.core.balance.BalanceHintKind
import com.stanisryz.logica.puzzle.core.balance.BalanceLogicTechnique
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.balance.BalanceViolationType
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.ui.balance.BalanceBoard
import com.stanisryz.logica.ui.balance.symbol
import com.stanisryz.logica.ui.components.BodyText
import com.stanisryz.logica.ui.components.DifficultyBadge
import com.stanisryz.logica.ui.components.GameAction
import com.stanisryz.logica.ui.components.GameActionBar
import com.stanisryz.logica.ui.components.GameMessage
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.MistakeIndicator
import com.stanisryz.logica.ui.components.PuzzleTerminalDialog
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.SupportingText
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.difficultyLabel
import com.stanisryz.logica.ui.theme.LogicaSpacing

@Composable
internal fun BalanceGameRoute(
    launch: BalanceGameLaunch,
    sessionRepository: GameSessionRepository,
    completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onGameHub: () -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(launch, sessionRepository, completionRepository, economyRepository) {
            BalanceGameViewModelFactory(launch, sessionRepository, completionRepository, economyRepository)
        }
    val gameViewModel: BalanceGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()
    BalanceGameScreen(
        uiState,
        economy,
        gameViewModel::onCellTapped,
        gameViewModel::selectValue,
        gameViewModel::togglePencilMode,
        gameViewModel::requestHint,
        gameViewModel::retry,
        gameViewModel::retryCompletion,
        onRestoreLife,
        hapticsEnabled,
        onBack,
        onNewPuzzle,
        onStartNew,
        onGameHub,
        launch.context is BalanceGameContext.Daily,
        modifier,
    )
}

@Composable
private fun BalanceGameScreen(
    uiState: BalanceGameUiState,
    economy: PlayerEconomy,
    onCellTapped: (BalancePosition) -> Unit,
    onSelectValue: (BalanceCell) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetryPuzzle: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
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
                            BalanceGameError.MISSING_SAVED_SESSION -> R.string.missing_saved_game
                            BalanceGameError.INVALID_SAVED_SESSION -> R.string.invalid_saved_game
                            BalanceGameError.GENERATION -> R.string.puzzle_generation_error
                        },
                    ),
                retryLabel = stringResource(if (isDaily) R.string.to_games else R.string.try_another),
                onRetry = if (isDaily) onGameHub else onStartNew,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.back),
                onSecondary = onBack,
            )
        is BalanceGameUiState.Ready ->
            ReadyState(
                uiState.puzzle,
                uiState.game,
                uiState.puzzle.id.difficulty,
                uiState.selectedValue,
                uiState.isPencilMode,
                uiState.isHintLoading,
                uiState.completionPersistence,
                economy,
                onCellTapped,
                onSelectValue,
                onTogglePencil,
                onHint,
                onRetryPuzzle,
                onRetryCompletion,
                onRestoreLife,
                hapticsEnabled,
                { onNewPuzzle(uiState.puzzle.id.difficulty) },
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
    selectedValue: BalanceCell,
    isPencilMode: Boolean,
    isHintLoading: Boolean,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onCellTapped: (BalancePosition) -> Unit,
    onSelectValue: (BalanceCell) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetryPuzzle: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onNewPuzzle: () -> Unit,
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

    ScreenColumn(
        modifier = modifier,
        verticalSpacing = LogicaSpacing.item,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DifficultyBadge(difficultyLabel(PuzzleType.BALANCE, difficulty))
        MistakeIndicator(game.mistakesUsed, PuzzleMistakes.MAX_MISTAKES)
        // The saved puzzle stays visible and intact at zero lives; only the actions stop working.
        ZeroLivesCard(economy, onRestoreLife)
        BalanceBoard(
            puzzle = puzzle,
            game = game,
            onCellTapped = {
                if (!economy.isGameplayAllowed) return@BalanceBoard
                if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCellTapped(it)
            },
        )
        BalanceToolBar(selectedValue, isPencilMode, onSelectValue, onTogglePencil)
        game.currentHint?.let { hint -> HintCard(hint) }
        GameMessage(game.violations.firstOrNull()?.let { violationText(it.type) })
        GameActionBar(
            listOf(
                GameAction(
                    icon = Icons.Filled.Lightbulb,
                    label = stringResource(R.string.hint),
                    enabled =
                        !isHintLoading &&
                            game.status == BalanceGameStatus.IN_PROGRESS &&
                            economy.isGameplayAllowed,
                    onClick = onHint,
                ),
            ),
        )
        if (isHintLoading) SupportingText(stringResource(R.string.searching_hint))
    }
    if (game.status.isTerminal) {
        PuzzleTerminalDialog(
            isSolved = game.status == BalanceGameStatus.SOLVED,
            completionPersistence = completionPersistence,
            hintsUsed = game.hintsUsed,
            maxMistakes = PuzzleMistakes.MAX_MISTAKES,
            lives = economy.lives,
            isRetryAllowed = economy.isGameplayAllowed,
            isDaily = isDaily,
            onRetryCompletion = onRetryCompletion,
            onRetryPuzzle = onRetryPuzzle,
            onNewPuzzle = onNewPuzzle,
            onGameHub = onGameHub,
        )
    }
}

/** Explicit input: pick the value to place, and optionally switch to unvalidated pencil notes. */
@Composable
internal fun BalanceToolBar(
    selectedValue: BalanceCell,
    isPencilMode: Boolean,
    onSelectValue: (BalanceCell) -> Unit,
    onTogglePencil: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuzzleToolBar(
        tools =
            listOf(
                balanceValueTool(BalanceCell.ONE, R.string.balance_tool_black, selectedValue, onSelectValue),
                balanceValueTool(BalanceCell.ZERO, R.string.balance_tool_white, selectedValue, onSelectValue),
                PuzzleTool(
                    label = stringResource(R.string.tool_pencil),
                    stateDescription = if (isPencilMode) stringResource(R.string.tool_on) else stringResource(R.string.tool_off),
                    selected = isPencilMode,
                    onClick = onTogglePencil,
                    symbol = { Icon(Icons.Filled.Edit, contentDescription = null) },
                ),
            ),
        modifier = modifier,
    )
}

@Composable
private fun balanceValueTool(
    value: BalanceCell,
    labelResource: Int,
    selectedValue: BalanceCell,
    onSelectValue: (BalanceCell) -> Unit,
): PuzzleTool =
    PuzzleTool(
        label = stringResource(labelResource),
        stateDescription =
            stringResource(if (selectedValue == value) R.string.tool_selected else R.string.tool_not_selected),
        selected = selectedValue == value,
        onClick = { onSelectValue(value) },
        symbol = { Text(value.symbol(), style = MaterialTheme.typography.titleMedium) },
    )

@Composable
private fun HintCard(hint: BalanceHint) {
    LogicaCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        verticalSpacing = LogicaSpacing.text,
    ) {
        BodyText(hint.presentationText())
        SupportingText(stringResource(R.string.hint_legend))
    }
}

@Composable
private fun BalanceHint.presentationText(): String =
    when (kind) {
        BalanceHintKind.INCORRECT_VALUE ->
            stringResource(
                R.string.hint_incorrect_value,
                position.row + 1,
                position.column + 1,
                suggestedValue.symbol(),
            )
        BalanceHintKind.LOGICAL_DEDUCTION ->
            stringResource(
                R.string.hint_logical_deduction,
                position.row + 1,
                position.column + 1,
                suggestedValue.symbol(),
                technique.presentationText(),
            )
    }

@Composable
private fun BalanceLogicTechnique?.presentationText(): String =
    stringResource(
        when (this) {
            BalanceLogicTechnique.PREVENT_THREE -> R.string.rule_prevent_three
            BalanceLogicTechnique.COMPLETE_QUOTA -> R.string.rule_complete_quota
            BalanceLogicTechnique.PRESERVE_UNIQUENESS -> R.string.rule_preserve_uniqueness
            null -> R.string.empty
        },
    )

@Composable
private fun violationText(type: BalanceViolationType): String =
    stringResource(
        when (type) {
            BalanceViolationType.UNBALANCED_ROW -> R.string.violation_unbalanced_row
            BalanceViolationType.UNBALANCED_COLUMN -> R.string.violation_unbalanced_column
            BalanceViolationType.THREE_EQUAL_HORIZONTAL -> R.string.violation_three_horizontal
            BalanceViolationType.THREE_EQUAL_VERTICAL -> R.string.violation_three_vertical
            BalanceViolationType.DUPLICATE_ROWS -> R.string.violation_duplicate_rows
            BalanceViolationType.DUPLICATE_COLUMNS -> R.string.violation_duplicate_columns
            BalanceViolationType.FIXED_CLUE_CONFLICT -> R.string.violation_fixed_clue
            BalanceViolationType.BOARD_SIZE_MISMATCH -> R.string.violation_board_size
        },
    )
