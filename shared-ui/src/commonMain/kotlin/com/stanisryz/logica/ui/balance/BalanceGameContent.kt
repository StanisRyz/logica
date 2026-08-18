package com.stanisryz.logica.ui.balance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
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
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.balance_tool_black
import com.stanisryz.logica.shared.ui.generated.resources.balance_tool_white
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.hint
import com.stanisryz.logica.shared.ui.generated.resources.hint_incorrect_value
import com.stanisryz.logica.shared.ui.generated.resources.hint_legend
import com.stanisryz.logica.shared.ui.generated.resources.hint_logical_deduction
import com.stanisryz.logica.shared.ui.generated.resources.rule_complete_quota
import com.stanisryz.logica.shared.ui.generated.resources.rule_preserve_uniqueness
import com.stanisryz.logica.shared.ui.generated.resources.rule_prevent_three
import com.stanisryz.logica.shared.ui.generated.resources.searching_hint
import com.stanisryz.logica.shared.ui.generated.resources.tool_not_selected
import com.stanisryz.logica.shared.ui.generated.resources.tool_off
import com.stanisryz.logica.shared.ui.generated.resources.tool_on
import com.stanisryz.logica.shared.ui.generated.resources.tool_pencil
import com.stanisryz.logica.shared.ui.generated.resources.tool_selected
import com.stanisryz.logica.shared.ui.generated.resources.violation_board_size
import com.stanisryz.logica.shared.ui.generated.resources.violation_duplicate_columns
import com.stanisryz.logica.shared.ui.generated.resources.violation_duplicate_rows
import com.stanisryz.logica.shared.ui.generated.resources.violation_fixed_clue
import com.stanisryz.logica.shared.ui.generated.resources.violation_three_horizontal
import com.stanisryz.logica.shared.ui.generated.resources.violation_three_vertical
import com.stanisryz.logica.shared.ui.generated.resources.violation_unbalanced_column
import com.stanisryz.logica.shared.ui.generated.resources.violation_unbalanced_row
import com.stanisryz.logica.ui.components.GameHeaderBadges
import com.stanisryz.logica.ui.components.GameMessage
import com.stanisryz.logica.ui.components.MistakeIndicator
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import com.stanisryz.logica.ui.theme.LogicaSpacing
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
    modifier: Modifier = Modifier,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = LogicaSpacing.screenHorizontal,
                    vertical = LogicaSpacing.screenVertical,
                ),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameHeaderBadges(stringResource(difficulty.labelResource()), levelNumber)
        MistakeIndicator(game.mistakesUsed, PuzzleMistakes.MAX_MISTAKES)
        hostStatusContent()
        BalanceBoard(
            puzzle = puzzle,
            game = game,
            onCellTapped = onCellTapped,
            enabled = gameplayEnabled,
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
                    gameplayEnabled,
            enabled = gameplayEnabled,
        )
        game.currentHint?.let { hint -> HintCard(hint) }
        GameMessage(game.violations.firstOrNull()?.let { violationText(it.type) })
        if (isHintLoading) {
            Text(
                text = stringResource(Res.string.searching_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
) {
    PuzzleToolBar(
        tools =
            listOf(
                balanceValueTool(
                    value = BalanceCell.ONE,
                    label = stringResource(Res.string.balance_tool_black),
                    selectedValue = selectedValue,
                    onSelectValue = onSelectValue,
                ),
                balanceValueTool(
                    value = BalanceCell.ZERO,
                    label = stringResource(Res.string.balance_tool_white),
                    selectedValue = selectedValue,
                    onSelectValue = onSelectValue,
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
                    onHint?.let { hint ->
                        PuzzleTool(
                            label = stringResource(Res.string.hint),
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
    label: String,
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
    )

@Composable
private fun HintCard(hint: BalanceHint) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(LogicaSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
        ) {
            Text(hint.presentationText(), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(Res.string.hint_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BalanceHint.presentationText(): String =
    when (kind) {
        BalanceHintKind.INCORRECT_VALUE ->
            stringResource(
                Res.string.hint_incorrect_value,
                position.row + 1,
                position.column + 1,
                suggestedValue.symbol(),
            )
        BalanceHintKind.LOGICAL_DEDUCTION ->
            stringResource(
                Res.string.hint_logical_deduction,
                position.row + 1,
                position.column + 1,
                suggestedValue.symbol(),
                technique.presentationText(),
            )
    }

@Composable
private fun BalanceLogicTechnique?.presentationText(): String =
    when (this) {
        BalanceLogicTechnique.PREVENT_THREE -> stringResource(Res.string.rule_prevent_three)
        BalanceLogicTechnique.COMPLETE_QUOTA -> stringResource(Res.string.rule_complete_quota)
        BalanceLogicTechnique.PRESERVE_UNIQUENESS -> stringResource(Res.string.rule_preserve_uniqueness)
        null -> ""
    }

@Composable
private fun violationText(type: BalanceViolationType): String = stringResource(type.presentationResource())

private fun BalanceViolationType.presentationResource(): StringResource =
    when (this) {
        BalanceViolationType.UNBALANCED_ROW -> Res.string.violation_unbalanced_row
        BalanceViolationType.UNBALANCED_COLUMN -> Res.string.violation_unbalanced_column
        BalanceViolationType.THREE_EQUAL_HORIZONTAL -> Res.string.violation_three_horizontal
        BalanceViolationType.THREE_EQUAL_VERTICAL -> Res.string.violation_three_vertical
        BalanceViolationType.DUPLICATE_ROWS -> Res.string.violation_duplicate_rows
        BalanceViolationType.DUPLICATE_COLUMNS -> Res.string.violation_duplicate_columns
        BalanceViolationType.FIXED_CLUE_CONFLICT -> Res.string.violation_fixed_clue
        BalanceViolationType.BOARD_SIZE_MISMATCH -> Res.string.violation_board_size
    }

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val BALANCE_TOOL_PIECE_SIZE = 20.dp
