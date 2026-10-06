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
import com.stanisryz.logica.puzzle.core.word.WordLanguage
import com.stanisryz.logica.puzzle.core.word.WordLetterFeedback
import com.stanisryz.logica.puzzle.core.word.WordLetterKnowledge
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.word_backspace
import com.stanisryz.logica.shared.ui.generated.resources.word_enter
import com.stanisryz.logica.shared.ui.generated.resources.word_key_description
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

/**
 * Shared adaptive keyboard over the game language's normalized alphabet ([wordKeyboardRows]: Russian
 * ЙЦУКЕН without a separate `ё` key, English QWERTY, Turkish Q without q, w, x). Every letter key has
 * the same width — the widest row's width over its key count — and the rows are centred. Enter and
 * Backspace sit on the two edges of the last letter row, as in Wordle-like games, sharing the letters
 * and gaps that row lacks so it is exactly as wide as the widest one. The landscape panel keeps rows
 * of five letters with Enter and Backspace in a row of their own.
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
    language: WordLanguage = WordLanguage.RUSSIAN,
) {
    val letterRows = wordKeyboardRows(language)
    val rows = if (landscapeCompact) letterRows.flatten().chunked(LANDSCAPE_LETTER_COLUMNS) else letterRows
    val widestRow = rows.maxOf { it.size }
    // The last row's two action keys share the letters and gaps it lacks next to the widest row.
    val missingLetters = widestRow - rows.last().size
    val actionLetters = missingLetters / 2f
    val actionGaps = (missingLetters - 2) / 2f
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
            with(density) { floor((letterWidth * actionLetters + keySpacing * actionGaps).toPx()).toDp() }
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
                            shown = language.displayUppercase(letter),
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
    shown: Char,
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
            WordLetterFeedback.ABSENT -> colors.surfaceVariant.copy(alpha = ABSENT_KEY_CONTAINER_ALPHA)
            null -> colors.surfaceVariant
        }
    val targetContent =
        when (feedback) {
            WordLetterFeedback.CORRECT -> colors.onPrimary
            WordLetterFeedback.PRESENT -> colors.onTertiaryContainer
            WordLetterFeedback.ABSENT -> colors.onSurfaceVariant.copy(alpha = ABSENT_KEY_CONTENT_ALPHA)
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
            shown.toString(),
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
            text = shown.toString(),
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

/**
 * The on-screen letter rows of one Word language, each letter of its alphabet exactly once: Russian
 * ЙЦУКЕН (12/11/9, `ё` typed as `е`), English QWERTY (10/9/7), and Turkish Q without q, w, x
 * (10/11/8).
 */
fun wordKeyboardRows(language: WordLanguage): List<List<Char>> =
    when (language) {
        WordLanguage.RUSSIAN -> listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю")
        WordLanguage.ENGLISH -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        WordLanguage.TURKISH -> listOf("ertyuıopğü", "asdfghjklşi", "zcvbnmöç")
    }.map(String::toList)

internal val WORD_KEY_SPACING = 4.dp
internal const val WORD_KEYBOARD_ROWS = 3
internal const val WORD_LANDSCAPE_KEYBOARD_ROWS = 8

private const val LANDSCAPE_LETTER_COLUMNS = 5
private val DEFAULT_KEY_HEIGHT = 48.dp
private val KEY_CORNER = 6.dp
private val PRESENT_BORDER_WIDTH = 2.dp
private val ACTION_PADDING = 4.dp

private const val KEY_FONT_RATIO = 0.32f
private val MIN_KEY_FONT = 12.dp
private val MAX_KEY_FONT = 16.dp

/** An absent key fades its key, not its letter: the letter keeps 4.5:1 (see `LogicaContrast`). */
internal const val ABSENT_KEY_CONTAINER_ALPHA = 0.35f
internal const val ABSENT_KEY_CONTENT_ALPHA = 0.8f
private const val KEY_FEEDBACK_MILLIS = 160
