package com.stanisryz.logica.ui.sudoku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuHint
import com.stanisryz.logica.puzzle.core.sudoku.SudokuHintTechnique
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.toPlatformDifficulty
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_hint_fallback
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_hint_hidden_block
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_hint_hidden_column
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_hint_hidden_row
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_hint_naked_single
import com.stanisryz.logica.ui.components.GameHeaderBadges
import com.stanisryz.logica.ui.components.MistakeIndicator
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Pure one-screen Sudoku presentation shared by Android and Web hosts. */
@Composable
fun SudokuGameContent(
    puzzle: SudokuPuzzle,
    game: SudokuGameState,
    selectedCell: SudokuPosition?,
    isPencilMode: Boolean,
    levelNumber: Int?,
    gameplayEnabled: Boolean,
    inputEnabled: Boolean,
    onCellSelected: (SudokuPosition) -> Unit,
    onDigit: (Int) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    modifier: Modifier = Modifier,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
        val verticalPadding = if (compact) COMPACT_VERTICAL_PADDING else NORMAL_VERTICAL_PADDING
        val sectionSpacing = if (compact) COMPACT_SECTION_SPACING else NORMAL_SECTION_SPACING
        val contextHeight = if (compact) COMPACT_CONTEXT_HEIGHT else NORMAL_CONTEXT_HEIGHT
        val keypadSpacing = if (compact) COMPACT_KEYPAD_SPACING else SUDOKU_DIGIT_ROW_SPACING

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = LogicaSpacing.gameplayHorizontal, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameHeaderBadges(
                difficultyLabel =
                    stringResource(
                        puzzle.id.difficulty
                            .toPlatformDifficulty()
                            .labelResource(),
                    ),
                levelNumber = levelNumber,
            )
            MistakeIndicator(game.mistakesUsed, SudokuGameState.MAX_MISTAKES)
            hostStatusContent()
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                SudokuBoard(
                    game = game,
                    selectedCell = selectedCell,
                    enabled = gameplayEnabled,
                    onCellSelected = onCellSelected,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            SudokuToolBar(
                isPencilMode = isPencilMode,
                onToggle = onTogglePencil,
                onHint = onHint,
                hintEnabled = game.status == SudokuGameStatus.IN_PROGRESS && gameplayEnabled,
                enabled = gameplayEnabled,
            )
            SudokuNumberPad(
                enabled = inputEnabled,
                onDigit = onDigit,
                spacing = keypadSpacing,
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(contextHeight),
                contentAlignment = Alignment.TopCenter,
            ) {
                game.currentHint?.let { SudokuHintCard(it, compact) }
            }
        }
    }
}

@Composable
private fun SudokuHintCard(
    hint: SudokuHint,
    compact: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = hint.presentationText(),
            modifier = Modifier.padding(CONTEXT_CARD_PADDING),
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            maxLines = MAX_HINT_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SudokuHint.presentationText(): String =
    when (technique) {
        SudokuHintTechnique.NAKED_SINGLE ->
            stringResource(Res.string.sudoku_hint_naked_single, position.row + 1, position.column + 1, value)
        SudokuHintTechnique.HIDDEN_SINGLE_ROW ->
            stringResource(
                Res.string.sudoku_hint_hidden_row,
                value,
                checkNotNull(unitIndex) + 1,
                position.column + 1,
            )
        SudokuHintTechnique.HIDDEN_SINGLE_COLUMN ->
            stringResource(
                Res.string.sudoku_hint_hidden_column,
                value,
                checkNotNull(unitIndex) + 1,
                position.row + 1,
            )
        SudokuHintTechnique.HIDDEN_SINGLE_BLOCK ->
            stringResource(
                Res.string.sudoku_hint_hidden_block,
                value,
                checkNotNull(unitIndex) + 1,
                position.row + 1,
                position.column + 1,
            )
        SudokuHintTechnique.FALLBACK_REVEAL ->
            stringResource(Res.string.sudoku_hint_fallback, position.row + 1, position.column + 1, value)
    }

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val COMPACT_HEIGHT_THRESHOLD = 700.dp
private val COMPACT_VERTICAL_PADDING = 6.dp
private val NORMAL_VERTICAL_PADDING = 12.dp
private val COMPACT_SECTION_SPACING = 4.dp
private val NORMAL_SECTION_SPACING = 8.dp
private val COMPACT_CONTEXT_HEIGHT = 64.dp
private val NORMAL_CONTEXT_HEIGHT = 76.dp
private val COMPACT_KEYPAD_SPACING = 4.dp
private val CONTEXT_CARD_PADDING = 10.dp
private const val MAX_HINT_LINES = 3
