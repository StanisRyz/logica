package com.stanisryz.logica.ui.word

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.word.WordLetterFeedback
import com.stanisryz.logica.puzzle.core.word.WordLetterKnowledge

/**
 * Russian keyboard over the normalized core alphabet, so `ё` never appears as its own key.
 * Key styling mirrors [WordLetterKnowledge], which already keeps the strongest known state.
 */
@Composable
internal fun WordKeyboard(
    knowledge: WordLetterKnowledge,
    enabled: Boolean,
    onLetter: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = DEFAULT_KEY_HEIGHT,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KEY_SPACING),
    ) {
        LETTER_ROWS.forEach { rowLetters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KEY_SPACING),
            ) {
                rowLetters.forEach { letter ->
                    LetterKey(
                        letter = letter,
                        feedback = knowledge[letter],
                        enabled = enabled,
                        keyHeight = keyHeight,
                        onClick = { onLetter(letter) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KEY_SPACING),
        ) {
            ActionKey(
                label = { Text(stringResource(R.string.word_enter), fontWeight = FontWeight.Bold) },
                description = stringResource(R.string.word_enter),
                enabled = enabled,
                keyHeight = keyHeight,
                onClick = onSubmit,
                modifier = Modifier.weight(2f),
            )
            ActionKey(
                label = { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null) },
                description = stringResource(R.string.word_backspace),
                enabled = enabled,
                keyHeight = keyHeight,
                onClick = onBackspace,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LetterKey(
    letter: Char,
    feedback: WordLetterFeedback?,
    enabled: Boolean,
    keyHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val targetContainer =
        when (feedback) {
            WordLetterFeedback.CORRECT -> colors.primary
            WordLetterFeedback.PRESENT -> colors.tertiaryContainer
            WordLetterFeedback.ABSENT -> colors.surfaceVariant.copy(alpha = ABSENT_ALPHA)
            null -> colors.surfaceVariant
        }
    val targetContent =
        when (feedback) {
            WordLetterFeedback.CORRECT -> colors.onPrimary
            WordLetterFeedback.PRESENT -> colors.onTertiaryContainer
            WordLetterFeedback.ABSENT -> colors.onSurfaceVariant.copy(alpha = ABSENT_ALPHA)
            null -> colors.onSurfaceVariant
        }
    val container by
        animateColorAsState(
            targetValue = targetContainer,
            animationSpec = tween(KEY_FEEDBACK_MILLIS),
            label = "wordKeyContainer",
        )
    val content by
        animateColorAsState(
            targetValue = targetContent,
            animationSpec = tween(KEY_FEEDBACK_MILLIS),
            label = "wordKeyContent",
        )
    val description =
        stringResource(
            R.string.word_key_description,
            letter.uppercaseChar().toString(),
            stringResource(feedback.descriptionResource()),
        )

    Box(
        modifier =
            modifier
                .height(keyHeight)
                .clip(RoundedCornerShape(KEY_CORNER))
                .background(container)
                .then(
                    // Non-color cue: PRESENT keys keep a heavy outline, ABSENT keys have none.
                    if (feedback == WordLetterFeedback.PRESENT) {
                        Modifier.border(PRESENT_BORDER_WIDTH, colors.tertiary, RoundedCornerShape(KEY_CORNER))
                    } else {
                        Modifier
                    },
                ).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // A compacted keyboard keeps its letters proportional to the keys they sit on.
        val letterSize =
            with(LocalDensity.current) {
                (keyHeight * KEY_FONT_RATIO).coerceIn(MIN_KEY_FONT, MAX_KEY_FONT).toSp()
            }
        Text(
            text = letter.uppercaseChar().toString(),
            color = content,
            fontSize = letterSize,
            lineHeight = letterSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionKey(
    label: @Composable () -> Unit,
    description: String,
    enabled: Boolean,
    keyHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .height(keyHeight)
                .clip(RoundedCornerShape(KEY_CORNER))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = ACTION_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        label()
    }
}

/** Standard ЙЦУКЕН order, minus `ё`, split so every row fits a small portrait phone. */
private val LETTER_ROWS =
    listOf(
        "йцукенгшщзхъ".toList(),
        "фывапролджэ".toList(),
        "ячсмитьбю".toList(),
    )

/** Vertical gap between key rows; the screen adds it up when it budgets the keyboard's height. */
internal val WORD_KEY_SPACING = 4.dp

/** How many rows the keyboard has: three letter rows plus the action row. */
internal const val WORD_KEYBOARD_ROWS = 4

private val KEY_SPACING = WORD_KEY_SPACING
private val DEFAULT_KEY_HEIGHT = 48.dp
private val KEY_CORNER = 6.dp
private val PRESENT_BORDER_WIDTH = 2.dp
private val ACTION_PADDING = 8.dp
private const val KEY_FONT_RATIO = 0.32f
private val MIN_KEY_FONT = 12.dp
private val MAX_KEY_FONT = 16.dp
private const val ABSENT_ALPHA = 0.35f
private const val KEY_FEEDBACK_MILLIS = 160
