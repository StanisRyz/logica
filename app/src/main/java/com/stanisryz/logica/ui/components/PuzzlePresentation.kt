package com.stanisryz.logica.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
