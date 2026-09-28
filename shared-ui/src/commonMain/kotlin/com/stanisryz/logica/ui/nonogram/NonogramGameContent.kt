package com.stanisryz.logica.ui.nonogram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes
import com.stanisryz.logica.puzzle.core.nonogram.NonogramCell
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameState
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameStatus
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPosition
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPuzzle
import com.stanisryz.logica.puzzle.core.nonogram.NonogramTool
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_caption_cross
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_caption_fill
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_tool_cross
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_tool_fill
import com.stanisryz.logica.shared.ui.generated.resources.tool_not_selected
import com.stanisryz.logica.shared.ui.generated.resources.tool_selected
import com.stanisryz.logica.ui.components.BoardInfoHeader
import com.stanisryz.logica.ui.components.CellGameSounds
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import com.stanisryz.logica.ui.components.SquareGameLayout
import com.stanisryz.logica.ui.components.displayName
import com.stanisryz.logica.ui.components.hintTool
import org.jetbrains.compose.resources.stringResource

/**
 * Pure Nonogram presentation. Hosts own the attempt and apply [onCell] with the selected tool; the
 * header counts the filled cells already found, which reveals nothing new.
 */
@Composable
fun NonogramGameContent(
    puzzle: NonogramPuzzle,
    game: NonogramGameState,
    difficulty: Difficulty,
    levelNumber: Int?,
    selectedTool: NonogramTool,
    gameplayEnabled: Boolean,
    onCell: (NonogramPosition) -> Unit,
    onSelectTool: (NonogramTool) -> Unit,
    onHint: () -> Unit,
    modifier: Modifier = Modifier,
    contextBadgeLabel: String? = null,
    hintCount: Int? = null,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
) {
    CellGameSounds(
        correctCells = game.cells.size - game.cells.count { it == NonogramCell.UNKNOWN } - game.mistakeCells.size,
        mistakesUsed = game.mistakesUsed,
        hintsUsed = game.hintsUsed,
        solved = game.status == NonogramGameStatus.SOLVED,
        failed = game.status == NonogramGameStatus.FAILED,
    )
    SquareGameLayout(
        modifier = modifier,
        metadataContent = {
            BoardInfoHeader(
                difficultyLabel = difficulty.displayName(),
                mistakesUsed = game.mistakesUsed,
                maxMistakes = PuzzleMistakes.MAX_MISTAKES,
                levelNumber = levelNumber,
                contextLabel = contextBadgeLabel,
                solvedCells = game.filledFound,
                totalCells = puzzle.filledCount,
            )
        },
        hostStatusContent = hostStatusContent,
        boardContent = {
            NonogramBoard(
                puzzle = puzzle,
                game = game,
                onCell = onCell,
                enabled = gameplayEnabled,
                modifier = Modifier.fillMaxSize(),
            )
        },
        toolContent = {
            NonogramToolBar(
                selectedTool = selectedTool,
                onSelectTool = onSelectTool,
                onHint = onHint,
                hintEnabled = gameplayEnabled && game.status == NonogramGameStatus.IN_PROGRESS,
                enabled = gameplayEnabled,
                hintCount = hintCount,
            )
        },
    )
}

/** Fill and Cross, plus an optional Hint. */
@Composable
fun NonogramToolBar(
    selectedTool: NonogramTool,
    onSelectTool: (NonogramTool) -> Unit,
    modifier: Modifier = Modifier,
    onHint: (() -> Unit)? = null,
    hintEnabled: Boolean = false,
    enabled: Boolean = true,
    hintCount: Int? = null,
) {
    val fillColor = MaterialTheme.colorScheme.primary
    PuzzleToolBar(
        tools =
            listOf(
                PuzzleTool(
                    label = stringResource(Res.string.nonogram_tool_fill),
                    stateDescription = selectedDescription(selectedTool == NonogramTool.FILL),
                    selected = selectedTool == NonogramTool.FILL,
                    onClick = { onSelectTool(NonogramTool.FILL) },
                    symbol = { Box(Modifier.size(TOOL_SQUARE).background(fillColor, RoundedCornerShape(3.dp))) },
                    caption = stringResource(Res.string.nonogram_caption_fill),
                ),
                PuzzleTool(
                    label = stringResource(Res.string.nonogram_tool_cross),
                    stateDescription = selectedDescription(selectedTool == NonogramTool.CROSS),
                    selected = selectedTool == NonogramTool.CROSS,
                    onClick = { onSelectTool(NonogramTool.CROSS) },
                    symbol = { Icon(Icons.Rounded.Close, contentDescription = null) },
                    caption = stringResource(Res.string.nonogram_caption_cross),
                ),
            ) + listOfNotNull(onHint?.let { hintTool(hintCount = hintCount, enabled = hintEnabled, onClick = it) }),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun selectedDescription(selected: Boolean): String =
    stringResource(if (selected) Res.string.tool_selected else Res.string.tool_not_selected)

private val TOOL_SQUARE = 18.dp
