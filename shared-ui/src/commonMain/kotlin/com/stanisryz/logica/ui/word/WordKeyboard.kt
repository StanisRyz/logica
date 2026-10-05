package com.stanisryz.logica.ui.word

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.word.WordLetterFeedback
import com.stanisryz.logica.puzzle.core.word.WordLetterKnowledge
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.word_backspace
import com.stanisryz.logica.shared.ui.generated.resources.word_enter
import com.stanisryz.logica.shared.ui.generated.resources.word_key_description
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

/**
 * Shared adaptive Russian keyboard over the normalized alphabet, without a separate `ё` key. Every
 * letter key has the same width — the widest row's width over its key count — and the rows are
 * centred. Enter and Backspace sit on the two edges of the last letter row, as in Russian Wordle-like
 * games, each half a key wider than a letter so that row is exactly as wide as the widest one. The
 * landscape panel keeps rows of five letters with Enter and Backspace in a row of their own.
 */
@Composable
fun WordKeyboard(
    knowledge: WordLetterKnowledge,
    enabled: Boolean,
    onLetter: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = DEFAULT_KEY_HEIGHT,
    landscapeCompact: Boolean = false,
    keySpacing: Dp = WORD_KEY_SPACING,
) {
    val rows = if (landscapeCompact) LETTER_ROWS.flatten().chunked(LANDSCAPE_LETTER_COLUMNS) else LETTER_ROWS
    val widestRow = rows.maxOf { it.size }
    val enterKey: @Composable (Modifier) -> Unit = { keyModifier ->
        ActionKey(
            label = { Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, contentDescription = null) },
            description = stringResource(Res.string.word_enter),
            enabled = enabled,
            keyHeight = keyHeight,
            onClick = onSubmit,
            modifier = keyModifier,
        )
    }
    val backspaceKey: @Composable (Modifier) -> Unit = { keyModifier ->
        ActionKey(
            label = { Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null) },
            description = stringResource(Res.string.word_backspace),
            enabled = enabled,
            keyHeight = keyHeight,
            onClick = onBackspace,
            modifier = keyModifier,
        )
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Whole pixels, so a full row never measures a pixel wider than the keyboard.
        val density = LocalDensity.current
        val letterWidth =
            with(density) {
                floor((maxWidth.toPx() - keySpacing.toPx() * (widestRow - 1)) / widestRow).coerceAtLeast(0f).toDp()
            }
        val actionWidth =
            with(density) { floor((letterWidth * ACTION_KEY_LETTERS + keySpacing * ACTION_KEY_GAPS).toPx()).toDp() }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(keySpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            rows.forEachIndexed { rowIndex, rowLetters ->
                val actionsOnEdges = !landscapeCompact && rowIndex == rows.lastIndex
                Row(horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                    if (actionsOnEdges) enterKey(Modifier.width(actionWidth))
                    rowLetters.forEach { letter ->
                        LetterKey(
                            letter = letter,
                            feedback = knowledge[letter],
                            enabled = enabled,
                            keyHeight = keyHeight,
                            onClick = { onLetter(letter) },
                            modifier = Modifier.width(letterWidth),
                        )
                    }
                    if (actionsOnEdges) backspaceKey(Modifier.width(actionWidth))
                }
            }
            if (landscapeCompact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(keySpacing),
                ) {
                    enterKey(Modifier.weight(2f))
                    backspaceKey(Modifier.weight(1f))
                }
            }
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
            Res.string.word_key_description,
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
                    if (feedback == WordLetterFeedback.PRESENT) {
                        Modifier.border(PRESENT_BORDER_WIDTH, colors.tertiary, RoundedCornerShape(KEY_CORNER))
                    } else {
                        Modifier
                    },
                ).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
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

private val LETTER_ROWS =
    listOf(
        "йцукенгшщзхъ".toList(),
        "фывапролджэ".toList(),
        "ячсмитьбю".toList(),
    )

internal val WORD_KEY_SPACING = 4.dp
internal const val WORD_KEYBOARD_ROWS = 3
internal const val WORD_LANDSCAPE_KEYBOARD_ROWS = 8

private const val LANDSCAPE_LETTER_COLUMNS = 5
private val DEFAULT_KEY_HEIGHT = 48.dp
private val KEY_CORNER = 6.dp
private val PRESENT_BORDER_WIDTH = 2.dp
private val ACTION_PADDING = 4.dp

// The last row holds 9 letters and two action keys in the width of 12 letters: the three spare
// letters and one spare gap are shared between Enter and Backspace.
private const val ACTION_KEY_LETTERS = 1.5f
private const val ACTION_KEY_GAPS = 0.5f
private const val KEY_FONT_RATIO = 0.32f
private val MIN_KEY_FONT = 12.dp
private val MAX_KEY_FONT = 16.dp
private const val ABSENT_ALPHA = 0.35f
private const val KEY_FEEDBACK_MILLIS = 160
