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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordLetterFeedback
import com.stanisryz.logica.puzzle.core.word.WordRules
import kotlinx.coroutines.delay

/** One board cell: either a submitted letter with feedback, the positional draft, or an empty slot. */
private data class WordCell(
    val letter: Char?,
    val feedback: WordLetterFeedback?,
)

@Composable
internal fun WordBoard(
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

    Column(
        modifier = modifier.widthIn(max = BOARD_MAX_WIDTH).fillMaxWidth(),
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
            )
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
) {
    val correctLabel = stringResource(R.string.word_feedback_correct)
    val presentLabel = stringResource(R.string.word_feedback_present)
    val absentLabel = stringResource(R.string.word_feedback_absent)
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
        if (isSubmitted) stringResource(R.string.word_attempt_row_description, rowIndex + 1, letters) else null

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
                onClick = { onCellSelected(cellIndex) },
                modifier = Modifier.weight(1f),
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
            stringResource(R.string.word_editable_cell_empty, position + 1, wordLength)
        } else {
            stringResource(
                R.string.word_editable_cell_letter,
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
                .aspectRatio(1f)
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
            Text(
                text = letter?.uppercaseChar()?.toString().orEmpty(),
                color = content,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
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

internal fun WordLetterFeedback?.descriptionResource(): Int =
    when (this) {
        WordLetterFeedback.CORRECT -> R.string.word_feedback_correct
        WordLetterFeedback.PRESENT -> R.string.word_feedback_present
        WordLetterFeedback.ABSENT -> R.string.word_feedback_absent
        null -> R.string.word_feedback_unknown
    }

private val BOARD_MAX_WIDTH = 340.dp
private val CELL_SPACING = 6.dp
private val COMPACT_CELL_SPACING = 4.dp
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
