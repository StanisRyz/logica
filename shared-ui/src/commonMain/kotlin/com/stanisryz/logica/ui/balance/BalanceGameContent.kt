package com.stanisryz.logica.ui.balance

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceCellStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.balance_tool_black
import com.stanisryz.logica.shared.ui.generated.resources.balance_tool_white
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.hint
import com.stanisryz.logica.shared.ui.generated.resources.tool_caption_black
import com.stanisryz.logica.shared.ui.generated.resources.tool_caption_white
import com.stanisryz.logica.shared.ui.generated.resources.tool_not_selected
import com.stanisryz.logica.shared.ui.generated.resources.tool_off
import com.stanisryz.logica.shared.ui.generated.resources.tool_on
import com.stanisryz.logica.shared.ui.generated.resources.tool_pencil
import com.stanisryz.logica.shared.ui.generated.resources.tool_selected
import com.stanisryz.logica.ui.components.BoardDragCallbacks
import com.stanisryz.logica.ui.components.BoardInfoHeader
import com.stanisryz.logica.ui.components.CellGameSounds
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import com.stanisryz.logica.ui.components.SquareGameLayout
import com.stanisryz.logica.ui.components.hintTool
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pure Balance presentation. Hosts provide transient state and events; application policy can add
 * a neutral status slot without entering this module.
 */
@Composable
fun BalanceGameContent(
    puzzle: BalancePuzzle,
    game: BalanceGameState,
    difficulty: Difficulty,
    levelNumber: Int?,
    selectedValue: BalanceCell,
    isPencilMode: Boolean,
    isHintLoading: Boolean,
    gameplayEnabled: Boolean,
    onCellTapped: (BalancePosition) -> Unit,
    onSelectValue: (BalanceCell) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    contextBadgeLabel: String? = null,
    modifier: Modifier = Modifier,
    hintCount: Int? = null,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
    drag: BoardDragCallbacks<BalancePosition>? = null,
) {
    CellGameSounds(
        correctCells = game.cellStatuses.count { it.value == BalanceCellStatus.CORRECT },
        mistakesUsed = game.mistakesUsed,
        hintsUsed = game.hintsUsed,
        solved = game.status == BalanceGameStatus.SOLVED,
        failed = game.status == BalanceGameStatus.FAILED,
    )
    SquareGameLayout(
        modifier = modifier,
        metadataContent = {
            BoardInfoHeader(
                difficultyLabel = stringResource(difficulty.labelResource()),
                mistakesUsed = game.mistakesUsed,
                maxMistakes = PuzzleMistakes.MAX_MISTAKES,
                levelNumber = levelNumber,
                contextLabel = contextBadgeLabel,
                solvedCells = game.cellStatuses.count { it.value == BalanceCellStatus.CORRECT },
                totalCells = puzzle.size * puzzle.size - puzzle.fixedClues.size,
            )
        },
        hostStatusContent = hostStatusContent,
        boardContent = {
            BalanceBoard(
                puzzle = puzzle,
                game = game,
                onCellTapped = onCellTapped,
                drag = drag,
                enabled = gameplayEnabled,
                modifier = Modifier.fillMaxSize(),
            )
        },
        toolContent = {
            BalanceToolBar(
                selectedValue = selectedValue,
                isPencilMode = isPencilMode,
                onSelectValue = onSelectValue,
                onTogglePencil = onTogglePencil,
                onHint = onHint,
                hintEnabled =
                    !isHintLoading &&
                        game.status == BalanceGameStatus.IN_PROGRESS &&
                        gameplayEnabled,
                enabled = gameplayEnabled,
                hintCount = hintCount,
            )
        },
    )
}

/** Explicit Balance values plus Pencil and optional Hint actions. */
@Composable
fun BalanceToolBar(
    selectedValue: BalanceCell,
    isPencilMode: Boolean,
    onSelectValue: (BalanceCell) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: (() -> Unit)? = null,
    hintEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hintCount: Int? = null,
) {
    PuzzleToolBar(
        tools =
            listOf(
                balanceValueTool(
                    value = BalanceCell.ONE,
                    label = stringResource(Res.string.balance_tool_black),
                    caption = stringResource(Res.string.tool_caption_black),
                    selectedValue = selectedValue,
                    onSelectValue = onSelectValue,
                ),
                balanceValueTool(
                    value = BalanceCell.ZERO,
                    label = stringResource(Res.string.balance_tool_white),
                    caption = stringResource(Res.string.tool_caption_white),
                    selectedValue = selectedValue,
                    onSelectValue = onSelectValue,
                ),
                PuzzleTool(
                    label = stringResource(Res.string.tool_pencil),
                    stateDescription =
                        stringResource(if (isPencilMode) Res.string.tool_on else Res.string.tool_off),
                    selected = isPencilMode,
                    onClick = onTogglePencil,
                    symbol = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    caption = stringResource(Res.string.tool_pencil),
                ),
            ) +
                listOfNotNull(
                    onHint?.let { hint -> hintTool(hintCount = hintCount, enabled = hintEnabled, onClick = hint) },
                ),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun balanceValueTool(
    value: BalanceCell,
    label: String,
    caption: String,
    selectedValue: BalanceCell,
    onSelectValue: (BalanceCell) -> Unit,
): PuzzleTool =
    PuzzleTool(
        label = label,
        stateDescription =
            stringResource(
                if (selectedValue == value) Res.string.tool_selected else Res.string.tool_not_selected,
            ),
        selected = selectedValue == value,
        onClick = { onSelectValue(value) },
        symbol = { BalancePiece(value, Modifier.size(BALANCE_TOOL_PIECE_SIZE)) },
        caption = caption,
    )

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val BALANCE_TOOL_PIECE_SIZE = 20.dp
