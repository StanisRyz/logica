package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.components.PuzzleStartScreen

@Composable
internal fun CrownsStartScreen(
    hasActiveSession: Boolean,
    tutorialCompleted: Boolean,
    economy: PlayerEconomy,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuzzleStartScreen(
        puzzleType = PuzzleType.CROWNS,
        introResource = R.string.crowns_rules_intro,
        tutorialOfferBodyResource = R.string.crowns_tutorial_offer_body,
        tutorialCompleted = tutorialCompleted,
        hasActiveSession = hasActiveSession,
        economy = economy,
        onOpenTutorial = onOpenTutorial,
        onStart = onStart,
        onRestoreLife = onRestoreLife,
        modifier = modifier,
        ruleResources =
            listOf(
                R.string.crowns_rule_row,
                R.string.crowns_rule_column,
                R.string.crowns_rule_region,
                R.string.crowns_rule_diagonal,
            ),
    )
}
