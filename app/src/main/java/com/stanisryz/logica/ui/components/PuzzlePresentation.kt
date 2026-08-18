package com.stanisryz.logica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.crowns.CrownIcon

/** Shared user-facing naming for the production puzzles, so every screen calls them the same thing. */
internal fun PuzzleType.titleResource(): Int =
    when (this) {
        PuzzleType.BALANCE -> R.string.balance
        PuzzleType.CROWNS -> R.string.crowns
        PuzzleType.WORD -> R.string.word
        PuzzleType.SUDOKU -> R.string.sudoku
        PuzzleType.GAME_2048 -> R.string.game_2048_title
        else -> error("$this has no user-facing title yet.")
    }

/** A subtle per-puzzle accent taken from the shared scheme; puzzles never get separate themes. */
@Composable
internal fun PuzzleType.accentColor(): Color =
    when (this) {
        PuzzleType.BALANCE -> MaterialTheme.colorScheme.primary
        PuzzleType.CROWNS -> MaterialTheme.colorScheme.tertiary
        PuzzleType.WORD -> MaterialTheme.colorScheme.secondary
        PuzzleType.SUDOKU -> MaterialTheme.colorScheme.primary
        PuzzleType.GAME_2048 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

/**
 * The shared card artwork: the puzzle's own accent tinted behind one existing vector, so Balance,
 * Crowns, Word, Sudoku, and 2048 read as one family on Daily and catalog cards alike. Deliberately
 * icon-sized rather than an illustration framework.
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
            CrownIcon(modifier = iconModifier, tint = accent)
        } else {
            val icon =
                when (puzzleType) {
                    PuzzleType.BALANCE -> Icons.Filled.Balance
                    PuzzleType.WORD -> Icons.Filled.SortByAlpha
                    PuzzleType.SUDOKU -> Icons.Filled.Extension
                    PuzzleType.GAME_2048 -> Icons.Filled.Grid4x4
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

/** 2048 difficulty changes only the score goal of a new game, so the goal is part of every label. */
internal fun Difficulty.game2048DifficultyResource(): Int =
    when (this) {
        Difficulty.EASY -> R.string.game_2048_difficulty_easy
        Difficulty.MEDIUM -> R.string.game_2048_difficulty_medium
        Difficulty.HARD -> R.string.game_2048_difficulty_hard
        Difficulty.EXPERT -> R.string.game_2048_difficulty_expert
    }

/** The shared difficulty label, including Word length or 2048 target where those define difficulty. */
@Composable
internal fun difficultyLabel(
    puzzleType: PuzzleType,
    difficulty: Difficulty,
): String =
    stringResource(
        when (puzzleType) {
            PuzzleType.WORD -> difficulty.wordDifficultyResource()
            PuzzleType.GAME_2048 -> difficulty.game2048DifficultyResource()
            else -> difficulty.difficultyResource()
        },
    )

private val ARTWORK_SIZE = 44.dp
private const val ARTWORK_ICON_RATIO = 0.55f
private const val ARTWORK_TINT_ALPHA = 0.14f
