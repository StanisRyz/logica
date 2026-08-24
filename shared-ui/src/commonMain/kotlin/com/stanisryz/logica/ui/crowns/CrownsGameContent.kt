package com.stanisryz.logica.ui.crowns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.crowns_hint_incorrect_crown
import com.stanisryz.logica.shared.ui.generated.resources.crowns_hint_incorrect_mark
import com.stanisryz.logica.shared.ui.generated.resources.crowns_hint_legend
import com.stanisryz.logica.shared.ui.generated.resources.crowns_hint_mark
import com.stanisryz.logica.shared.ui.generated.resources.crowns_hint_place
import com.stanisryz.logica.shared.ui.generated.resources.crowns_technique_region_column
import com.stanisryz.logica.shared.ui.generated.resources.crowns_technique_region_row
import com.stanisryz.logica.shared.ui.generated.resources.crowns_technique_single_column
import com.stanisryz.logica.shared.ui.generated.resources.crowns_technique_single_region
import com.stanisryz.logica.shared.ui.generated.resources.crowns_technique_single_row
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tool_crown
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tool_mark
import com.stanisryz.logica.shared.ui.generated.resources.crowns_violation_column
import com.stanisryz.logica.shared.ui.generated.resources.crowns_violation_diagonal
import com.stanisryz.logica.shared.ui.generated.resources.crowns_violation_position
import com.stanisryz.logica.shared.ui.generated.resources.crowns_violation_region
import com.stanisryz.logica.shared.ui.generated.resources.crowns_violation_row
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.hint
import com.stanisryz.logica.shared.ui.generated.resources.searching_hint
import com.stanisryz.logica.shared.ui.generated.resources.tool_not_selected
import com.stanisryz.logica.shared.ui.generated.resources.tool_off
import com.stanisryz.logica.shared.ui.generated.resources.tool_on
import com.stanisryz.logica.shared.ui.generated.resources.tool_pencil
import com.stanisryz.logica.shared.ui.generated.resources.tool_selected
import com.stanisryz.logica.ui.components.GameHeaderBadges
import com.stanisryz.logica.ui.components.GameMessage
import com.stanisryz.logica.ui.components.MistakeIndicator
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import com.stanisryz.logica.ui.components.SquareGameLayout
import com.stanisryz.logica.ui.theme.LogicaSpacing
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
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
) {
    SquareGameLayout(
        modifier = modifier,
        metadataContent = {
            GameHeaderBadges(stringResource(difficulty.labelResource()), levelNumber, contextLabel = contextBadgeLabel)
            MistakeIndicator(game.mistakesUsed, PuzzleMistakes.MAX_MISTAKES)
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
            )
        },
        contextStatusContent = { compact ->
            CrownsContextStatus(
                hint = game.currentHint,
                violation = game.violations.firstOrNull()?.type,
                isHintLoading = isHintLoading,
                compact = compact,
                modifier = Modifier.fillMaxWidth(),
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
) {
    PuzzleToolBar(
        tools =
            listOf(
                crownsValueTool(
                    value = CrownsPlayerCell.CROWN,
                    label = stringResource(Res.string.crowns_tool_crown),
                    selectedValue = selectedValue,
                    onSelectValue = onSelectValue,
                    symbol = { CrownIcon(Modifier.size(CROWN_TOOL_SIZE)) },
                ),
                crownsValueTool(
                    value = CrownsPlayerCell.MARKED,
                    label = stringResource(Res.string.crowns_tool_mark),
                    selectedValue = selectedValue,
                    onSelectValue = onSelectValue,
                    symbol = { Text("×", style = MaterialTheme.typography.titleMedium) },
                ),
                PuzzleTool(
                    label = stringResource(Res.string.tool_pencil),
                    stateDescription =
                        stringResource(if (isPencilMode) Res.string.tool_on else Res.string.tool_off),
                    selected = isPencilMode,
                    onClick = onTogglePencil,
                    symbol = { Icon(Icons.Filled.Edit, contentDescription = null) },
                ),
            ) +
                listOfNotNull(
                    onHint?.let { hintAction ->
                        PuzzleTool(
                            label = stringResource(Res.string.hint),
                            stateDescription = null,
                            selected = null,
                            enabled = hintEnabled,
                            onClick = hintAction,
                            symbol = { Icon(Icons.Filled.Lightbulb, contentDescription = null) },
                        )
                    },
                ),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun crownsValueTool(
    value: CrownsPlayerCell,
    label: String,
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
    )

@Composable
private fun CrownsContextStatus(
    hint: CrownsHint?,
    violation: CrownsViolationType?,
    isHintLoading: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        when {
            hint != null -> CrownsHintCard(hint, compact)
            violation != null -> GameMessage(crownsViolationText(violation))
            isHintLoading ->
                Text(
                    text = stringResource(Res.string.searching_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
private fun CrownsHintCard(
    hint: CrownsHint,
    compact: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(CONTEXT_CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
        ) {
            Text(
                text = hint.presentationText(),
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                maxLines = if (compact) COMPACT_HINT_LINES else NORMAL_HINT_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                Text(
                    text = stringResource(Res.string.crowns_hint_legend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = HINT_LEGEND_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CrownsHint.presentationText(): String {
    val firstTarget = targetPositions.sortedWith(compareBy(CrownsPosition::row, CrownsPosition::column)).first()
    return when (action) {
        CrownsHintAction.CLEAR_CROWN ->
            stringResource(Res.string.crowns_hint_incorrect_crown, firstTarget.row + 1, firstTarget.column + 1)
        CrownsHintAction.CLEAR_MARK ->
            stringResource(Res.string.crowns_hint_incorrect_mark, firstTarget.row + 1, firstTarget.column + 1)
        CrownsHintAction.PLACE_CROWN ->
            stringResource(
                Res.string.crowns_hint_place,
                firstTarget.row + 1,
                firstTarget.column + 1,
                technique.presentationText(),
            )
        CrownsHintAction.MARK_POSITIONS ->
            stringResource(
                Res.string.crowns_hint_mark,
                targetPositions.size,
                technique.presentationText(),
            )
    }
}

@Composable
private fun CrownsLogicTechnique?.presentationText(): String =
    when (this) {
        CrownsLogicTechnique.SINGLE_CANDIDATE_ROW -> stringResource(Res.string.crowns_technique_single_row)
        CrownsLogicTechnique.SINGLE_CANDIDATE_COLUMN -> stringResource(Res.string.crowns_technique_single_column)
        CrownsLogicTechnique.SINGLE_CANDIDATE_REGION -> stringResource(Res.string.crowns_technique_single_region)
        CrownsLogicTechnique.REGION_LOCKED_TO_ROW -> stringResource(Res.string.crowns_technique_region_row)
        CrownsLogicTechnique.REGION_LOCKED_TO_COLUMN -> stringResource(Res.string.crowns_technique_region_column)
        null -> ""
    }

@Composable
private fun crownsViolationText(type: CrownsViolationType): String = stringResource(type.presentationResource())

private fun CrownsViolationType.presentationResource(): StringResource =
    when (this) {
        CrownsViolationType.POSITION_OUTSIDE_BOARD -> Res.string.crowns_violation_position
        CrownsViolationType.ROW_CONFLICT -> Res.string.crowns_violation_row
        CrownsViolationType.COLUMN_CONFLICT -> Res.string.crowns_violation_column
        CrownsViolationType.REGION_CONFLICT -> Res.string.crowns_violation_region
        CrownsViolationType.DIAGONAL_ADJACENCY_CONFLICT -> Res.string.crowns_violation_diagonal
    }

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val CROWN_TOOL_SIZE = 20.dp
private val CONTEXT_CARD_PADDING = 12.dp
private const val COMPACT_HINT_LINES = 3
private const val NORMAL_HINT_LINES = 3
private const val HINT_LEGEND_LINES = 2


