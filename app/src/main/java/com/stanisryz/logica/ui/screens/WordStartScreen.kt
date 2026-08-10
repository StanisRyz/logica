package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.components.PuzzleStartScreen
import com.stanisryz.logica.ui.components.wordDifficultyResource

@Composable
internal fun WordStartScreen(
    hasActiveSession: Boolean,
    tutorialCompleted: Boolean,
    economy: PlayerEconomy,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuzzleStartScreen(
        puzzleType = PuzzleType.WORD,
        introResource = R.string.word_rules_intro,
        tutorialOfferBodyResource = R.string.word_tutorial_offer_body,
        tutorialCompleted = tutorialCompleted,
        hasActiveSession = hasActiveSession,
        economy = economy,
        onOpenTutorial = onOpenTutorial,
        onStart = onStart,
        onRestoreLife = onRestoreLife,
        modifier = modifier,
        ruleResources =
            listOf(
                R.string.word_rule_length,
                R.string.word_rule_attempts,
                R.string.word_rule_correct,
                R.string.word_rule_present,
                R.string.word_rule_absent,
                R.string.word_rule_repeats,
            ),
        difficultyNoteResource = R.string.word_difficulty_note,
        // Word difficulty is word-length-only, so the length is part of the option label itself.
        difficultyLabel = { difficulty -> stringResource(difficulty.wordDifficultyResource()) },
    )
}
