package com.stanisryz.logica.ui.crowns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tool_crown
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tool_mark
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.tool_caption_mark
import com.stanisryz.logica.shared.ui.generated.resources.tool_not_selected
import com.stanisryz.logica.shared.ui.generated.resources.tool_off
import com.stanisryz.logica.shared.ui.generated.resources.tool_on
import com.stanisryz.logica.shared.ui.generated.resources.tool_pencil
import com.stanisryz.logica.shared.ui.generated.resources.tool_selected
import com.stanisryz.logica.ui.components.GameHeaderBadges
import com.stanisryz.logica.ui.components.MistakeIndicator
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import com.stanisryz.logica.ui.components.SquareGameLayout
import com.stanisryz.logica.ui.components.hintTool
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Pure Crowns presentation; hosts own engines, persistence, economy, navigation, and haptics. */
@Composable
fun CrownsGameContent(
    puzzle: CrownsPuzzle,
    game: CrownsGameState,
    difficulty: Difficulty,
    levelNumber: Int?,
    selectedValue: CrownsPlayerCell,
    isPencilMode: Boolean,
    isHintLoading: Boolean,
    gameplayEnabled: Boolean,
    onCellTapped: (CrownsPosition) -> Unit,
    onSelectValue: (CrownsPlayerCell) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    contextBadgeLabel: String? = null,
    modifier: Modifier = Modifier,
    hintCount: Int? = null,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
) {
    SquareGameLayout(
        modifier = modifier,
        metadataContent = { wideLayout ->
            if (wideLayout) {
                GameHeaderBadges(stringResource(difficulty.labelResource()), levelNumber, contextLabel = contextBadgeLabel)
                MistakeIndicator(game.mistakesUsed, PuzzleMistakes.MAX_MISTAKES)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GameHeaderBadges(
                        stringResource(difficulty.labelResource()),
                        levelNumber,
                        modifier = Modifier.weight(1f),
                        contextLabel = contextBadgeLabel,
                    )
                    MistakeIndicator(game.mistakesUsed, PuzzleMistakes.MAX_MISTAKES)
                }
            }
        },
        hostStatusContent = hostStatusContent,
        boardContent = {
            CrownsBoard(
                puzzle = puzzle,
                game = game,
                onCellTapped = onCellTapped,
                enabled = gameplayEnabled,
                modifier = Modifier.fillMaxSize(),
            )
        },
        toolContent = {
            CrownsToolBar(
                selectedValue = selectedValue,
                isPencilMode = isPencilMode,
                onSelectValue = onSelectValue,
                onTogglePencil = onTogglePencil,
                onHint = onHint,
                hintEnabled =
                    !isHintLoading &&
                        game.status == CrownsGameStatus.IN_PROGRESS &&
                        gameplayEnabled,
                enabled = gameplayEnabled,
                hintCount = hintCount,
            )
        },
    )
}

/** Explicit Crown/Mark values plus Pencil and optional Hint actions. */
@Composable
fun CrownsToolBar(
    selectedValue: CrownsPlayerCell,
    isPencilMode: Boolean,
    onSelectValue: (CrownsPlayerCell) -> Unit,
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
                crownsValueTool(
                    value = CrownsPlayerCell.CROWN,
                    label = stringResource(Res.string.crowns_tool_crown),
                    caption = stringResource(Res.string.crowns_tool_crown),
                    selectedValue = selectedValue,
                    onSelectValue = onSelectValue,
                    symbol = { CrownIcon(Modifier.size(CROWN_TOOL_SIZE)) },
                ),
                crownsValueTool(
                    value = CrownsPlayerCell.MARKED,
                    label = stringResource(Res.string.crowns_tool_mark),
                    caption = stringResource(Res.string.tool_caption_mark),
                    selectedValue = selectedValue,
                    onSelectValue = onSelectValue,
                    symbol = { Text("×", style = MaterialTheme.typography.headlineSmall) },
                ),
                PuzzleTool(
                    label = stringResource(Res.string.tool_pencil),
                    stateDescription =
                        stringResource(if (isPencilMode) Res.string.tool_on else Res.string.tool_off),
                    selected = isPencilMode,
                    onClick = onTogglePencil,
                    symbol = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    caption = stringResource(Res.string.tool_pencil),
                ),
            ) +
                listOfNotNull(
                    onHint?.let { hintAction -> hintTool(hintCount = hintCount, enabled = hintEnabled, onClick = hintAction) },
                ),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun crownsValueTool(
    value: CrownsPlayerCell,
    label: String,
    caption: String,
    selectedValue: CrownsPlayerCell,
    onSelectValue: (CrownsPlayerCell) -> Unit,
    symbol: @Composable () -> Unit,
): PuzzleTool =
    PuzzleTool(
        label = label,
        stateDescription =
            stringResource(
                if (selectedValue == value) Res.string.tool_selected else Res.string.tool_not_selected,
            ),
        selected = selectedValue == value,
        onClick = { onSelectValue(value) },
        symbol = symbol,
        caption = caption,
    )

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val CROWN_TOOL_SIZE = 20.dp
