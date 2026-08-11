package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.components.PuzzleStartScreen
import com.stanisryz.logica.ui.components.game2048DifficultyResource

@Composable
internal fun Game2048StartScreen(
    hasActiveSession: Boolean,
    tutorialCompleted: Boolean,
    economy: PlayerEconomy,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuzzleStartScreen(
        puzzleType = PuzzleType.GAME_2048,
        introResource = R.string.game_2048_rules_intro,
        tutorialOfferBodyResource = R.string.game_2048_tutorial_offer_body,
        tutorialCompleted = tutorialCompleted,
        hasActiveSession = hasActiveSession,
        economy = economy,
        onOpenTutorial = onOpenTutorial,
        onStart = onStart,
        onRestoreLife = onRestoreLife,
        modifier = modifier,
        ruleResources = listOf(R.string.game_2048_rule_merge, R.string.game_2048_rule_end),
        difficultyNoteResource = R.string.game_2048_difficulty_note,
        difficultyLabel = { difficulty -> stringResource(difficulty.game2048DifficultyResource()) },
    )
}
