package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.tutorial.FirstPlayTutorialDialog

/**
 * The shared Catalog entry screen: a compact tutorial action followed by four direct-launch
 * adaptive difficulty cards. They always launch the authoritative current level at tap time.
 *
 * While [tutorialPending], the first difficulty tap offers the tutorial once instead; choosing
 * either answer, or opening the tutorial directly, reports [onTutorialOffered].
 */
@Composable
internal fun PuzzleStartScreen(
    puzzleType: PuzzleType,
    economy: PlayerEconomy,
    tutorialPending: Boolean,
    onTutorialOffered: () -> Unit,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
    stars: Map<Difficulty, Long> = emptyMap(),
) {
    var offeredDifficulty by remember { mutableStateOf<Difficulty?>(null) }
    val openTutorial = {
        if (tutorialPending) onTutorialOffered()
        onOpenTutorial()
    }
    val start: (Difficulty) -> Unit = { difficulty ->
        if (tutorialPending) offeredDifficulty = difficulty else onStart(difficulty)
    }
    offeredDifficulty?.let { difficulty ->
        FirstPlayTutorialDialog(
            puzzleType = puzzleType,
            onOpenTutorial = {
                offeredDifficulty = null
                openTutorial()
            },
            onPlay = {
                offeredDifficulty = null
                onTutorialOffered()
                onStart(difficulty)
            },
            onDismiss = { offeredDifficulty = null },
        )
    }
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
                onOpenTutorial = openTutorial,
                onStart = start,
                enabled = true,
                modifier = Modifier.fillMaxSize(),
                stars = stars,
            )
        }
    } else {
        // Why the cards are unavailable comes first, with the way back to playing, instead of below them.
        ScreenColumn(modifier) {
            ZeroLivesCard(economy, onRestoreLife)
            StartDifficultyContent(
                cardHeight = ZERO_LIVES_CARD_HEIGHT,
                onOpenTutorial = openTutorial,
                onStart = start,
                enabled = false,
                modifier = Modifier,
                stars = stars,
            )
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
    stars: Map<Difficulty, Long> = emptyMap(),
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.section),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedButton(onClick = onOpenTutorial) {
                Text(stringResource(R.string.how_to_play_question))
            }
        }
        DifficultySelector(
            onStart = onStart,
            enabled = enabled,
            cardHeight = cardHeight,
            stars = stars,
        )
    }
}

private fun normalCardHeight(availableHeight: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    (
        (availableHeight - TUTORIAL_ACTION_HEIGHT - LogicaSpacing.section - LogicaSpacing.item * CARD_GAP_COUNT) /
            Difficulty.entries.size
    ).coerceIn(MIN_CARD_HEIGHT, MAX_CARD_HEIGHT)

private val TUTORIAL_ACTION_HEIGHT = 48.dp
private val MIN_CARD_HEIGHT = 104.dp
private val MAX_CARD_HEIGHT = 168.dp
private val ZERO_LIVES_CARD_HEIGHT = 112.dp
private const val CARD_GAP_COUNT = 3
