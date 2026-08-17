package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The one direct-launch difficulty selector for every Catalog game. Every large card opens the
 * authoritative current level for its difficulty; there is intentionally no selected state or
 * separate Start action.
 */
@Composable
internal fun DifficultySelector(
    onStart: (Difficulty) -> Unit,
    enabled: Boolean,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        Difficulty.entries.forEach { difficulty ->
            DifficultyOption(
                difficulty = difficulty,
                enabled = enabled,
                cardHeight = cardHeight,
                onStart = { onStart(difficulty) },
            )
        }
    }
}

@Composable
private fun DifficultyOption(
    difficulty: Difficulty,
    enabled: Boolean,
    cardHeight: Dp,
    onStart: () -> Unit,
) {
    DifficultyCard(
        difficulty = difficulty,
        onClick = onStart,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        cardHeight = cardHeight,
    )
}

/** One full-surface Catalog launch target, shared across all five games. */
@Composable
private fun DifficultyCard(
    difficulty: Difficulty,
    onClick: () -> Unit,
    enabled: Boolean,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.height(cardHeight),
        colors =
            CardDefaults.cardColors(
                containerColor = if (enabled) colors.surfaceContainerLow else colors.surfaceContainerHighest,
                contentColor = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = stringResource(difficulty.difficultyResource()),
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.CenterStart,
        ) {
            DifficultyArtworkFade(
                difficulty = difficulty,
                modifier = Modifier.fillMaxSize(),
            )
            CatalogCardLabelScrim(Modifier.fillMaxSize())
            Text(
                text = difficulty.russianLabel(),
                modifier = Modifier.fillMaxWidth(0.5f).padding(start = DIFFICULTY_LABEL_PADDING),
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize * DIFFICULTY_TITLE_SCALE,
                    ),
                color = DIFFICULTY_LABEL_COLOR.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
            )
        }
    }
}

@Composable
private fun DifficultyArtworkFade(
    difficulty: Difficulty,
    modifier: Modifier = Modifier,
) = CatalogCardArtwork(difficulty.artworkDrawableName(), modifier)

private fun Difficulty.artworkDrawableName(): String =
    when (this) {
        Difficulty.EASY -> "difficulty_easy"
        Difficulty.MEDIUM -> "difficulty_medium"
        Difficulty.HARD -> "difficulty_hard"
        Difficulty.EXPERT -> "difficulty_expert"
    }

private val DIFFICULTY_LABEL_PADDING = 24.dp
private val DIFFICULTY_LABEL_COLOR = Color(0xFF1B2A35)
private const val DIFFICULTY_TITLE_SCALE = 1.40625f
private const val DISABLED_ALPHA = 0.38f
