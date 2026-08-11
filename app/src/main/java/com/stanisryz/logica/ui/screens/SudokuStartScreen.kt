package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.components.PuzzleStartScreen

@Composable
internal fun SudokuStartScreen(
    hasActiveSession: Boolean,
    tutorialCompleted: Boolean,
    economy: PlayerEconomy,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuzzleStartScreen(
        puzzleType = PuzzleType.SUDOKU,
        introResource = R.string.sudoku_rules_intro,
        tutorialOfferBodyResource = R.string.sudoku_tutorial_offer_body,
        tutorialCompleted = tutorialCompleted,
        hasActiveSession = hasActiveSession,
        economy = economy,
        onOpenTutorial = onOpenTutorial,
        onStart = onStart,
        onRestoreLife = onRestoreLife,
        modifier = modifier,
        ruleResources =
            listOf(
                R.string.sudoku_rule_rows,
                R.string.sudoku_rule_columns,
                R.string.sudoku_rule_blocks,
                R.string.sudoku_rule_input,
            ),
        difficultyNoteResource = R.string.sudoku_difficulty_note,
    )
}
