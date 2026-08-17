package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The shared Catalog entry screen: a compact tutorial action followed by four direct-launch
 * adaptive difficulty cards. They always launch the authoritative current level at tap time.
 */
@Composable
internal fun PuzzleStartScreen(
    economy: PlayerEconomy,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (economy.isGameplayAllowed) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = LogicaSpacing.screenHorizontal,
                        vertical = LogicaSpacing.screenVertical,
                    ),
        ) {
            StartDifficultyContent(
                cardHeight = normalCardHeight(maxHeight),
                onOpenTutorial = onOpenTutorial,
                onStart = onStart,
                enabled = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        ScreenColumn(modifier) {
            StartDifficultyContent(
                cardHeight = ZERO_LIVES_CARD_HEIGHT,
                onOpenTutorial = onOpenTutorial,
                onStart = onStart,
                enabled = false,
                modifier = Modifier,
            )
            ZeroLivesCard(economy, onRestoreLife)
        }
    }
}

@Composable
private fun StartDifficultyContent(
    cardHeight: androidx.compose.ui.unit.Dp,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.section),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onOpenTutorial) {
                Text(stringResource(R.string.how_to_play_question))
            }
        }
        DifficultySelector(
            onStart = onStart,
            enabled = enabled,
            cardHeight = cardHeight,
        )
    }
}

private fun normalCardHeight(availableHeight: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    ((availableHeight - TUTORIAL_ACTION_HEIGHT - LogicaSpacing.section - LogicaSpacing.item * CARD_GAP_COUNT) /
        Difficulty.entries.size).coerceIn(MIN_CARD_HEIGHT, MAX_CARD_HEIGHT)

private val TUTORIAL_ACTION_HEIGHT = 48.dp
private val MIN_CARD_HEIGHT = 104.dp
private val MAX_CARD_HEIGHT = 168.dp
private val ZERO_LIVES_CARD_HEIGHT = 112.dp
private const val CARD_GAP_COUNT = 3
