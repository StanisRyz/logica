package com.stanisryz.logica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/** Shared user-facing naming for the three puzzles, so every screen calls the same thing the same way. */
internal fun PuzzleType.titleResource(): Int =
    when (this) {
        PuzzleType.BALANCE -> R.string.balance
        PuzzleType.CROWNS -> R.string.crowns
        PuzzleType.WORD -> R.string.word
        else -> error("$this has no user-facing title yet.")
    }

/** A subtle per-puzzle accent taken from the shared scheme; the three puzzles never get their own themes. */
@Composable
internal fun PuzzleType.accentColor(): Color =
    when (this) {
        PuzzleType.BALANCE -> MaterialTheme.colorScheme.primary
        PuzzleType.CROWNS -> MaterialTheme.colorScheme.tertiary
        PuzzleType.WORD -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

/**
 * The shared card artwork: the puzzle's own accent tinted behind one existing vector, so Balance,
 * Crowns, and Word read as one family on Daily and catalog cards alike. Deliberately icon-sized
 * rather than an illustration framework.
 */
@Composable
internal fun PuzzleArtwork(
    puzzleType: PuzzleType,
    modifier: Modifier = Modifier,
    size: Dp = ARTWORK_SIZE,
) {
    val accent = puzzleType.accentColor()
    Box(
        modifier =
            modifier
                .size(size)
                .clip(MaterialTheme.shapes.small)
                .background(accent.copy(alpha = ARTWORK_TINT_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        val iconModifier = Modifier.size(size * ARTWORK_ICON_RATIO)
        if (puzzleType == PuzzleType.CROWNS) {
            Icon(painterResource(R.drawable.ic_crown), contentDescription = null, tint = accent, modifier = iconModifier)
        } else {
            val icon =
                when (puzzleType) {
                    PuzzleType.BALANCE -> Icons.Filled.Balance
                    PuzzleType.WORD -> Icons.Filled.SortByAlpha
                    else -> Icons.Filled.Extension
                }
            Icon(icon, contentDescription = null, tint = accent, modifier = iconModifier)
        }
    }
}

@Composable
internal fun Difficulty.russianLabel(): String = stringResource(difficultyResource())

internal fun Difficulty.difficultyResource(): Int =
    when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
        Difficulty.EXPERT -> R.string.difficulty_expert
    }

/** Word difficulty is word-length-only, so it is always shown as difficulty plus letter count. */
internal fun Difficulty.wordDifficultyResource(): Int =
    when (this) {
        Difficulty.EASY -> R.string.word_difficulty_easy
        Difficulty.MEDIUM -> R.string.word_difficulty_medium
        Difficulty.HARD -> R.string.word_difficulty_hard
        Difficulty.EXPERT -> R.string.word_difficulty_expert
    }

/** The label a puzzle's difficulty is presented with anywhere outside the Word start screen. */
@Composable
internal fun difficultyLabel(
    puzzleType: PuzzleType,
    difficulty: Difficulty,
): String =
    stringResource(
        if (puzzleType == PuzzleType.WORD) difficulty.wordDifficultyResource() else difficulty.difficultyResource(),
    )

private val ARTWORK_SIZE = 44.dp
private const val ARTWORK_ICON_RATIO = 0.55f
private const val ARTWORK_TINT_ALPHA = 0.14f
