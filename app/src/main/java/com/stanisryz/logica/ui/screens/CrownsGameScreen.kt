package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.crowns.CrownsGameContext
import com.stanisryz.logica.crowns.CrownsGameError
import com.stanisryz.logica.crowns.CrownsGameLaunch
import com.stanisryz.logica.crowns.CrownsGameUiState
import com.stanisryz.logica.crowns.CrownsGameViewModel
import com.stanisryz.logica.crowns.CrownsGameViewModelFactory
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
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
import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes
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
import com.stanisryz.logica.ui.components.MistakeIndicator
import com.stanisryz.logica.ui.components.PuzzleTerminalDialog
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.SupportingText
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.difficultyLabel
import com.stanisryz.logica.ui.crowns.CrownsBoard
import com.stanisryz.logica.ui.theme.LogicaSpacing

@Composable
internal fun CrownsGameRoute(
    launch: CrownsGameLaunch,
    sessionRepository: GameSessionRepository,
    completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(launch, sessionRepository, completionRepository, economyRepository) {
            CrownsGameViewModelFactory(launch, sessionRepository, completionRepository, economyRepository)
        }
    val gameViewModel: CrownsGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()

    CrownsGameScreen(
        uiState = uiState,
        economy = economy,
        onCellTapped = gameViewModel::onCellTapped,
        onSelectValue = gameViewModel::selectValue,
        onTogglePencil = gameViewModel::togglePencilMode,
        onHint = gameViewModel::requestHint,
        onRetryPuzzle = gameViewModel::retry,
        onRetryCompletion = gameViewModel::retryCompletion,
        onRestoreLife = onRestoreLife,
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
    economy: PlayerEconomy,
    onCellTapped: (CrownsPosition) -> Unit,
    onSelectValue: (CrownsPlayerCell) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetryPuzzle: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRestoreLife: () -> Unit,
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
                selectedValue = uiState.selectedValue,
                isPencilMode = uiState.isPencilMode,
                isHintLoading = uiState.isHintLoading,
                completionPersistence = uiState.completionPersistence,
                economy = economy,
                onCellTapped = onCellTapped,
                onSelectValue = onSelectValue,
                onTogglePencil = onTogglePencil,
                onHint = onHint,
                onRetryPuzzle = onRetryPuzzle,
                onRetryCompletion = onRetryCompletion,
                onRestoreLife = onRestoreLife,
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
    selectedValue: CrownsPlayerCell,
    isPencilMode: Boolean,
    isHintLoading: Boolean,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onCellTapped: (CrownsPosition) -> Unit,
    onSelectValue: (CrownsPlayerCell) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetryPuzzle: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onNewPuzzle: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
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

    ScreenColumn(
        modifier = modifier,
        verticalSpacing = LogicaSpacing.item,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DifficultyBadge(difficultyLabel(PuzzleType.CROWNS, difficulty))
        MistakeIndicator(game.mistakesUsed, PuzzleMistakes.MAX_MISTAKES)
        // The saved puzzle stays visible and intact at zero lives; only the actions stop working.
        ZeroLivesCard(economy, onRestoreLife)
        CrownsBoard(
            puzzle = puzzle,
            game = game,
            onCellTapped = { position ->
                if (!economy.isGameplayAllowed) return@CrownsBoard
                if (hapticsEnabled) {
                    val feedback =
                        if (isPencilMode) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP
                    view.performHapticFeedback(feedback)
                }
                onCellTapped(position)
            },
        )
        CrownsToolBar(selectedValue, isPencilMode, onSelectValue, onTogglePencil)
        game.currentHint?.let { hint -> CrownsHintCard(hint) }
        GameMessage(game.violations.firstOrNull()?.let { crownsViolationText(it.type) })
        GameActionBar(
            listOf(
                GameAction(
                    icon = Icons.Filled.Lightbulb,
                    label = stringResource(R.string.hint),
                    enabled =
                        !isHintLoading &&
                            game.status == CrownsGameStatus.IN_PROGRESS &&
                            economy.isGameplayAllowed,
                    onClick = onHint,
                ),
            ),
        )
        if (isHintLoading) SupportingText(stringResource(R.string.searching_hint))
    }

    if (game.status.isTerminal) {
        PuzzleTerminalDialog(
            isSolved = game.status == CrownsGameStatus.SOLVED,
            completionPersistence = completionPersistence,
            hintsUsed = game.hintsUsed,
            maxMistakes = PuzzleMistakes.MAX_MISTAKES,
            lives = economy.lives,
            isRetryAllowed = economy.isGameplayAllowed,
            isDaily = isDaily,
            onRetryCompletion = onRetryCompletion,
            onRetryPuzzle = onRetryPuzzle,
            onNewPuzzle = onNewPuzzle,
            onCatalog = onCatalog,
            onToday = onToday,
        )
    }
}

/** Explicit input: pick the value to place, and optionally switch to unvalidated pencil notes. */
@Composable
internal fun CrownsToolBar(
    selectedValue: CrownsPlayerCell,
    isPencilMode: Boolean,
    onSelectValue: (CrownsPlayerCell) -> Unit,
    onTogglePencil: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedLabel = stringResource(R.string.tool_selected)
    val unselectedLabel = stringResource(R.string.tool_not_selected)
    val crownSize = 20.dp
    PuzzleToolBar(
        tools =
            listOf(
                PuzzleTool(
                    label = stringResource(R.string.crowns_tool_crown),
                    stateDescription = if (selectedValue == CrownsPlayerCell.CROWN) selectedLabel else unselectedLabel,
                    selected = selectedValue == CrownsPlayerCell.CROWN,
                    onClick = { onSelectValue(CrownsPlayerCell.CROWN) },
                    symbol = {
                        Icon(
                            painter = painterResource(R.drawable.ic_crown),
                            contentDescription = null,
                            modifier = Modifier.size(crownSize),
                        )
                    },
                ),
                PuzzleTool(
                    label = stringResource(R.string.crowns_tool_mark),
                    stateDescription = if (selectedValue == CrownsPlayerCell.MARKED) selectedLabel else unselectedLabel,
                    selected = selectedValue == CrownsPlayerCell.MARKED,
                    onClick = { onSelectValue(CrownsPlayerCell.MARKED) },
                    symbol = { Text("×", style = MaterialTheme.typography.titleMedium) },
                ),
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
