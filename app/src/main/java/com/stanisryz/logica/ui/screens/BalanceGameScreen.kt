package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.stanisryz.logica.ui.balance.BalanceBoard
import com.stanisryz.logica.ui.balance.BalancePiece
import com.stanisryz.logica.ui.balance.symbol
import com.stanisryz.logica.ui.components.BodyText
import com.stanisryz.logica.ui.components.GameHeaderBadges
import com.stanisryz.logica.ui.components.GameMessage
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LeaveLevelGuard
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

    ScreenColumn(
        modifier = modifier,
        verticalSpacing = LogicaSpacing.item,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameHeaderBadges(difficultyLabel(PuzzleType.BALANCE, difficulty), levelNumber)
        MistakeIndicator(game.mistakesUsed, PuzzleMistakes.MAX_MISTAKES)
        // The board stays visible and intact at zero lives; only the actions stop working.
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
        BalanceToolBar(
            selectedValue = selectedValue,
            isPencilMode = isPencilMode,
            onSelectValue = onSelectValue,
            onTogglePencil = onTogglePencil,
            onHint = onHint,
            hintEnabled =
                !isHintLoading &&
                    game.status == BalanceGameStatus.IN_PROGRESS &&
                    economy.isGameplayAllowed,
            enabled = economy.isGameplayAllowed,
        )
        game.currentHint?.let { hint -> HintCard(hint) }
        GameMessage(game.violations.firstOrNull()?.let { violationText(it.type) })
        if (isHintLoading) SupportingText(stringResource(R.string.searching_hint))
    }
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

/** Explicit input: pick the value to place, and optionally switch to unvalidated pencil notes. */
@Composable
internal fun BalanceToolBar(
    selectedValue: BalanceCell,
    isPencilMode: Boolean,
    onSelectValue: (BalanceCell) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: (() -> Unit)? = null,
    hintEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
            ) +
                listOfNotNull(
                    onHint?.let { hint ->
                        PuzzleTool(
                            label = stringResource(R.string.hint),
                            stateDescription = null,
                            selected = null,
                            enabled = hintEnabled,
                            onClick = hint,
                            symbol = { Icon(Icons.Filled.Lightbulb, contentDescription = null) },
                        )
                    },
                ),
        modifier = modifier,
        enabled = enabled,
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
    symbol = { BalancePiece(value, Modifier.size(BALANCE_TOOL_PIECE_SIZE)) },
)

/** Explicit bounds keep the theme-independent Canvas piece compact inside the shared tool button. */
private val BALANCE_TOOL_PIECE_SIZE = 20.dp

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
