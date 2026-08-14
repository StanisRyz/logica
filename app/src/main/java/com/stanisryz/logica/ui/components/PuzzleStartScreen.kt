package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty

/**
 * The shared Catalog entry screen: a compact tutorial action followed by four direct-launch
 * difficulty buttons. Every button includes its current level because progression is per difficulty.
 */
@Composable
internal fun PuzzleStartScreen(
    levels: Map<Difficulty, Int>,
    economy: PlayerEconomy,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onOpenTutorial) {
                Text(stringResource(R.string.how_to_play_question))
            }
        }
        DifficultySelector(
            levels = levels,
            onStart = onStart,
            enabled = economy.isGameplayAllowed,
        )

        ZeroLivesCard(economy, onRestoreLife)
    }
}
