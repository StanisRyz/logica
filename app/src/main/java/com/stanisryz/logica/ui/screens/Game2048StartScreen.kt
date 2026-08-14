package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.ui.components.PuzzleStartScreen

@Composable
internal fun Game2048StartScreen(
    levels: Map<Difficulty, Int>,
    economy: PlayerEconomy,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuzzleStartScreen(
        levels = levels,
        economy = economy,
        onOpenTutorial = onOpenTutorial,
        onStart = onStart,
        onRestoreLife = onRestoreLife,
        modifier = modifier,
    )
}
