package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.components.PuzzleStartScreen

@Composable
internal fun BalanceStartScreen(
    levels: Map<Difficulty, Int>,
    tutorialCompleted: Boolean,
    economy: PlayerEconomy,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuzzleStartScreen(
        puzzleType = PuzzleType.BALANCE,
        introResource = R.string.balance_rules_summary,
        tutorialOfferBodyResource = R.string.balance_tutorial_offer_body,
        tutorialCompleted = tutorialCompleted,
        levels = levels,
        economy = economy,
        onOpenTutorial = onOpenTutorial,
        onStart = onStart,
        onRestoreLife = onRestoreLife,
        modifier = modifier,
    )
}
