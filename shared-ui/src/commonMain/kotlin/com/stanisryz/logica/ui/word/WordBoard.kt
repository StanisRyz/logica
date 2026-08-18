package com.stanisryz.logica.ui.word

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordLetterFeedback
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.word_attempt_row_description
import com.stanisryz.logica.shared.ui.generated.resources.word_editable_cell_empty
import com.stanisryz.logica.shared.ui.generated.resources.word_editable_cell_letter
import com.stanisryz.logica.shared.ui.generated.resources.word_feedback_absent
import com.stanisryz.logica.shared.ui.generated.resources.word_feedback_correct
import com.stanisryz.logica.shared.ui.generated.resources.word_feedback_present
import com.stanisryz.logica.shared.ui.generated.resources.word_feedback_unknown
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class WordCell(
    val letter: Char?,
    val feedback: WordLetterFeedback?,
)

/** Shared adaptive Word board with draft pop/fade and accepted-attempt reveal animations. */
@Composable
fun WordBoard(
    game: WordGameState,
    modifier: Modifier = Modifier,
    selectedCellIndex: Int? = null,
    editableEnabled: Boolean = false,
    onCellSelected: (Int) -> Unit = {},
    acceptedAttemptRevision: Int = 0,
    onAcceptedAttemptRevealed: (Int) -> Unit = {},
) {
    val rows = buildRows(game)
    val cellSpacing = if (game.wordLength == WordRules.MAXIMUM_WORD_LENGTH) COMPACT_CELL_SPACING else CELL_SPACING
    var revealedCells by
        rememberSaveable(game.puzzleId, game.attempts.size, acceptedAttemptRevision) {
            mutableIntStateOf(if (acceptedAttemptRevision == 0) game.wordLength else 0)
        }
    val currentOnAcceptedAttemptRevealed by rememberUpdatedState(onAcceptedAttemptRevealed)
    LaunchedEffect(acceptedAttemptRevision) {
        if (acceptedAttemptRevision == 0) return@LaunchedEffect
        repeat(game.wordLength) { index ->
            delay(REVEAL_STEP_MILLIS)
            revealedCells = index + 1
        }
        currentOnAcceptedAttemptRevealed(acceptedAttemptRevision)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val columns = game.wordLength
        val widthBudget = minOf(maxWidth, BOARD_MAX_WIDTH)
        val cellFromWidth = (widthBudget - cellSpacing * (columns - 1)) / columns
        val cellSize =
            if (constraints.hasBoundedHeight) {
                minOf(
                    cellFromWidth,
                    (maxHeight - cellSpacing * (WordRules.MAXIMUM_ATTEMPTS - 1)) /
                        WordRules.MAXIMUM_ATTEMPTS,
                )
            } else {
                cellFromWidth
            }.coerceAtLeast(MIN_CELL_SIZE)
        Column(
            modifier = Modifier.width(cellSize * columns + cellSpacing * (columns - 1)),
            verticalArrangement = Arrangement.spacedBy(cellSpacing),
        ) {
            rows.forEachIndexed { rowIndex, row ->
                val isSubmitted = rowIndex < game.attempts.size
                val isCurrent = !game.isFinished && rowIndex == game.attempts.size
                WordBoardRow(
                    row = row,
                    rowIndex = rowIndex,
                    isSubmitted = isSubmitted,
                    isCurrent = isCurrent,
                    selectedCellIndex = selectedCellIndex,
                    editableEnabled = editableEnabled,
                    onCellSelected = onCellSelected,
                    revealedCells =
                        if (isSubmitted && rowIndex == game.attempts.lastIndex && acceptedAttemptRevision > 0) {
                            revealedCells
                        } else {
                            game.wordLength
                        },
                    cellSpacing = cellSpacing,
                    cellSize = cellSize,
                )
            }
        }
    }
}

@Composable
private fun WordBoardRow(
    row: List<WordCell>,
    rowIndex: Int,
    isSubmitted: Boolean,
    isCurrent: Boolean,
    selectedCellIndex: Int?,
    editableEnabled: Boolean,
    onCellSelected: (Int) -> Unit,
    revealedCells: Int,
    cellSpacing: Dp,
    cellSize: Dp,
) {
    val correctLabel = stringResource(Res.string.word_feedback_correct)
    val presentLabel = stringResource(Res.string.word_feedback_present)
    val absentLabel = stringResource(Res.string.word_feedback_absent)
    val letters =
        row.joinToString(separator = ", ") { cell ->
            val label =
                when (cell.feedback) {
                    WordLetterFeedback.CORRECT -> correctLabel
                    WordLetterFeedback.PRESENT -> presentLabel
                    else -> absentLabel
                }
            "${cell.letter?.uppercaseChar()} $label"
        }
    val rowDescription =
        if (isSubmitted) {
            stringResource(Res.string.word_attempt_row_description, rowIndex + 1, letters)
        } else {
            null
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (rowDescription == null) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics { contentDescription = rowDescription }
                    },
                ),
        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
    ) {
        row.forEachIndexed { cellIndex, cell ->
            WordBoardCell(
                cell =
                    if (isSubmitted && cellIndex >= revealedCells) {
                        cell.copy(feedback = null)
                    } else {
                        cell
                    },
                position = cellIndex,
                wordLength = row.size,
                isCurrent = isCurrent,
                isSelected = isCurrent && selectedCellIndex == cellIndex,
                editableEnabled = editableEnabled,
                cellSize = cellSize,
                onClick = { onCellSelected(cellIndex) },
                modifier = Modifier,
            )
        }
    }
}

@Composable
private fun WordBoardCell(
    cell: WordCell,
    position: Int,
    wordLength: Int,
    isCurrent: Boolean,
    isSelected: Boolean,
    editableEnabled: Boolean,
    cellSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container =
        when (cell.feedback) {
            WordLetterFeedback.CORRECT -> colors.primary
            WordLetterFeedback.PRESENT -> colors.tertiaryContainer
            WordLetterFeedback.ABSENT -> colors.surfaceVariant
            null -> Color.Transparent
        }
    val content =
        when (cell.feedback) {
            WordLetterFeedback.CORRECT -> colors.onPrimary
            WordLetterFeedback.PRESENT -> colors.onTertiaryContainer
            WordLetterFeedback.ABSENT -> colors.onSurfaceVariant.copy(alpha = DIMMED_ALPHA)
            null -> colors.onSurface
        }
    val borderWidth =
        when {
            isSelected -> SELECTED_BORDER_WIDTH
            cell.feedback == WordLetterFeedback.PRESENT -> PRESENT_BORDER_WIDTH
            cell.feedback == null -> EMPTY_BORDER_WIDTH
            else -> 0.dp
        }
    val borderColor =
        when {
            isSelected -> colors.primary
            cell.feedback == WordLetterFeedback.PRESENT -> colors.tertiary
            else -> colors.outlineVariant
        }
    val shape =
        if (cell.feedback == WordLetterFeedback.CORRECT) {
            RoundedCornerShape(CORRECT_CORNER)
        } else {
            RoundedCornerShape(CELL_CORNER)
        }
    val cellDescription =
        if (cell.letter == null) {
            stringResource(Res.string.word_editable_cell_empty, position + 1, wordLength)
        } else {
            stringResource(
                Res.string.word_editable_cell_letter,
                position + 1,
                wordLength,
                cell.letter.uppercaseChar().toString(),
            )
        }
    val scale = remember { Animatable(1f) }
    var previousLetter by remember { mutableStateOf(cell.letter) }
    LaunchedEffect(cell.letter) {
        val wasEmpty = previousLetter == null
        val changed = previousLetter != cell.letter
        previousLetter = cell.letter
        if (isCurrent && wasEmpty && cell.letter != null && changed) {
            scale.snapTo(LETTER_POP_SCALE)
            scale.animateTo(1f, tween(LETTER_POP_MILLIS))
        }
    }

    Box(
        modifier =
            modifier
                .size(cellSize)
                .scale(scale.value)
                .clip(shape)
                .background(container)
                .then(
                    if (borderWidth > 0.dp) {
                        Modifier.border(borderWidth, borderColor, shape)
                    } else {
                        Modifier
                    },
                ).then(
                    if (isCurrent) {
                        Modifier
                            .clickable(enabled = editableEnabled, role = Role.Button, onClick = onClick)
                            .semantics {
                                contentDescription = cellDescription
                                selected = isSelected
                            }
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = cell.letter,
            transitionSpec = {
                if (initialState != null && targetState != null && initialState != targetState) {
                    fadeIn(tween(REPLACE_FADE_MILLIS)) togetherWith fadeOut(tween(REPLACE_FADE_MILLIS))
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "wordLetter",
        ) { letter ->
            val letterSize =
                with(LocalDensity.current) {
                    (cellSize * LETTER_TEXT_RATIO).coerceIn(MIN_LETTER_TEXT, MAX_LETTER_TEXT).toSp()
                }
            Text(
                text = letter?.uppercaseChar()?.toString().orEmpty(),
                color = content,
                fontSize = letterSize,
                lineHeight = letterSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        lineHeightStyle =
                            LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                    ),
            )
        }
    }
}

private fun buildRows(game: WordGameState): List<List<WordCell>> =
    buildList {
        game.attempts.forEach { attempt ->
            add(attempt.letters.map { WordCell(it.letter, it.feedback) })
        }
        if (size < WordRules.MAXIMUM_ATTEMPTS) {
            add(
                List(game.wordLength) { index ->
                    WordCell(game.currentDraft[index], null)
                },
            )
        }
        while (size < WordRules.MAXIMUM_ATTEMPTS) {
            add(List(game.wordLength) { WordCell(null, null) })
        }
    }

internal fun WordLetterFeedback?.descriptionResource(): StringResource =
    when (this) {
        WordLetterFeedback.CORRECT -> Res.string.word_feedback_correct
        WordLetterFeedback.PRESENT -> Res.string.word_feedback_present
        WordLetterFeedback.ABSENT -> Res.string.word_feedback_absent
        null -> Res.string.word_feedback_unknown
    }

private val BOARD_MAX_WIDTH = 340.dp
private val CELL_SPACING = 6.dp
private val COMPACT_CELL_SPACING = 4.dp
private val MIN_CELL_SIZE = 24.dp
private const val LETTER_TEXT_RATIO = 0.55f
private val MIN_LETTER_TEXT = 14.dp
private val MAX_LETTER_TEXT = 26.dp
private val CELL_CORNER = 6.dp
private val CORRECT_CORNER = 12.dp
private val PRESENT_BORDER_WIDTH = 3.dp
private val SELECTED_BORDER_WIDTH = 3.dp
private val EMPTY_BORDER_WIDTH = 1.dp
private const val DIMMED_ALPHA = 0.6f
private const val LETTER_POP_SCALE = 1.1f
private const val LETTER_POP_MILLIS = 120
private const val REPLACE_FADE_MILLIS = 80
private const val REVEAL_STEP_MILLIS = 85L
