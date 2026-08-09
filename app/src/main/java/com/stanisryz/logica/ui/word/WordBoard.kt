package com.stanisryz.logica.ui.word

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordLetterFeedback
import com.stanisryz.logica.puzzle.core.word.WordRules

/** One board cell: either a submitted letter with feedback, the current input, or an empty slot. */
private data class WordCell(
    val letter: Char?,
    val feedback: WordLetterFeedback?,
)

@Composable
internal fun WordBoard(
    game: WordGameState,
    modifier: Modifier = Modifier,
) {
    val rows = buildRows(game)
    Column(
        modifier = modifier.fillMaxWidth().widthIn(max = BOARD_MAX_WIDTH),
        verticalArrangement = Arrangement.spacedBy(CELL_SPACING),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            WordBoardRow(row = row, rowIndex = rowIndex, isSubmitted = rowIndex < game.attempts.size)
        }
    }
}

@Composable
private fun WordBoardRow(
    row: List<WordCell>,
    rowIndex: Int,
    isSubmitted: Boolean,
) {
    // Feedback labels are resolved up front: `joinToString` runs outside a composable context.
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
        horizontalArrangement = Arrangement.spacedBy(CELL_SPACING),
    ) {
        row.forEach { cell ->
            WordBoardCell(cell, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WordBoardCell(
    cell: WordCell,
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
    // Non-color cue: CORRECT is a solid filled square, PRESENT keeps a heavy outline on a tinted
    // surface, ABSENT is dimmed with no outline, and untouched cells are outline-only.
    val borderWidth =
        when (cell.feedback) {
            WordLetterFeedback.PRESENT -> PRESENT_BORDER_WIDTH
            WordLetterFeedback.ABSENT -> 0.dp
            WordLetterFeedback.CORRECT -> 0.dp
            null -> EMPTY_BORDER_WIDTH
        }
    val shape = if (cell.feedback == WordLetterFeedback.CORRECT) RoundedCornerShape(CORRECT_CORNER) else RoundedCornerShape(CELL_CORNER)

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(shape)
                .background(container)
                .then(
                    if (borderWidth > 0.dp) {
                        Modifier.border(
                            width = borderWidth,
                            color = if (cell.feedback == null) colors.outlineVariant else colors.tertiary,
                            shape = shape,
                        )
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text =
                cell.letter
                    ?.uppercaseChar()
                    ?.toString()
                    .orEmpty(),
            color = content,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun buildRows(game: WordGameState): List<List<WordCell>> =
    buildList {
        game.attempts.forEach { attempt ->
            add(attempt.letters.map { WordCell(it.letter, it.feedback) })
        }
        if (size < WordRules.MAXIMUM_ATTEMPTS) {
            add(
                List(WordRules.WORD_LENGTH) { index ->
                    WordCell(game.currentInput.getOrNull(index), null)
                },
            )
        }
        while (size < WordRules.MAXIMUM_ATTEMPTS) {
            add(List(WordRules.WORD_LENGTH) { WordCell(null, null) })
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
private val CELL_CORNER = 6.dp
private val CORRECT_CORNER = 12.dp
private val PRESENT_BORDER_WIDTH = 3.dp
private val EMPTY_BORDER_WIDTH = 1.dp
private const val DIMMED_ALPHA = 0.6f
