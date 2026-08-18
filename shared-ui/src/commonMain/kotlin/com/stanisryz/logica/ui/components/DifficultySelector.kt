package com.stanisryz.logica.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Four equal direct-launch difficulty cards shared by Android and Web. */
@Composable
fun DifficultySelector(
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
            DifficultyCard(
                difficulty = difficulty,
                onClick = { onStart(difficulty) },
                enabled = enabled,
                cardHeight = cardHeight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DifficultyCard(
    difficulty: Difficulty,
    onClick: () -> Unit,
    enabled: Boolean,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(difficulty.labelResource())
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
                        onClickLabel = label,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Image(
                painter = painterResource(difficulty.artworkResource()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to CATALOG_LABEL_SCRIM,
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(0.5f).padding(start = DIFFICULTY_LABEL_PADDING),
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize * CATALOG_CARD_TITLE_SCALE,
                    ),
                color = DIFFICULTY_LABEL_COLOR.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
            )
        }
    }
}

private fun Difficulty.artworkResource(): DrawableResource =
    when (this) {
        Difficulty.EASY -> Res.drawable.difficulty_easy
        Difficulty.MEDIUM -> Res.drawable.difficulty_medium
        Difficulty.HARD -> Res.drawable.difficulty_hard
        Difficulty.EXPERT -> Res.drawable.difficulty_expert
    }

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val DIFFICULTY_LABEL_PADDING = 24.dp
private val DIFFICULTY_LABEL_COLOR = Color(0xFF1B2A35)
private val CATALOG_LABEL_SCRIM = Color(0xFFF4F8FB).copy(alpha = 0.15f)
private const val CATALOG_CARD_TITLE_SCALE = 1.40625f
private const val DISABLED_ALPHA = 0.38f
