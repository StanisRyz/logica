package com.stanisryz.logica.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/**
 * The shared Catalog entry screen: a compact tutorial action followed by four direct-launch
 * difficulty buttons. Every button includes its current level because progression is per difficulty.
 */
@Composable
internal fun PuzzleStartScreen(
    puzzleType: PuzzleType,
    levels: Map<Difficulty, Int>,
    economy: PlayerEconomy,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        PuzzleTitle(stringResource(puzzleType.titleResource()), puzzleType = puzzleType)
        TextButton(onClick = onOpenTutorial) {
            Text(stringResource(R.string.how_to_play_question))
        }
        ScreenSection(title = stringResource(R.string.difficulty)) {
            DifficultySelector(
                levels = levels,
                onStart = onStart,
                enabled = economy.isGameplayAllowed,
            )
        }

        ZeroLivesCard(economy, onRestoreLife)
    }
}
