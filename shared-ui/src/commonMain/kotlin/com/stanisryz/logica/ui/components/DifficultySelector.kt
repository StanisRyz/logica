package com.stanisryz.logica.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_stars
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource

/** Four equal direct-launch difficulty cards shared by Android and Web. */
@Composable
fun DifficultySelector(
    onStart: (Difficulty) -> Unit,
    enabled: Boolean,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
    columns: Int = 1,
    stars: Map<Difficulty, Long> = emptyMap(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        // A wide window shows the four cards as a 2x2 grid; a phone keeps one column.
        Difficulty.entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
                row.forEach { difficulty ->
                    DifficultyCard(
                        difficulty = difficulty,
                        onClick = { onStart(difficulty) },
                        enabled = enabled,
                        cardHeight = cardHeight,
                        stars = stars[difficulty] ?: 0L,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyCard(
    difficulty: Difficulty,
    onClick: () -> Unit,
    enabled: Boolean,
    cardHeight: Dp,
    stars: Long,
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
                bitmap = imageResource(difficulty.artworkResource()),
                contentDescription = null,
                filterQuality = ArtworkFilterQuality,
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
            // The stars this difficulty has earned so far, each level counting its best attempt.
            if (stars > 0L) {
                val description = stringResource(Res.string.difficulty_stars, stars)
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(STARS_CHIP_MARGIN)
                            .clip(CircleShape)
                            .background(STARS_CHIP_BACKGROUND)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .clearAndSetSemantics { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = LocalLogicaPalette.current.star,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stars.toString(), style = MaterialTheme.typography.labelLarge, color = DIFFICULTY_LABEL_COLOR)
                }
            }
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

/** The difficulty's name in the current language. */
@Composable
fun Difficulty.displayName(): String = stringResource(labelResource())

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val DIFFICULTY_LABEL_PADDING = 24.dp
private val STARS_CHIP_MARGIN = 10.dp
private val STARS_CHIP_BACKGROUND = Color(0xFFFFFBF4).copy(alpha = 0.9f)
private val DIFFICULTY_LABEL_COLOR = Color(0xFF1B2A35)
private val CATALOG_LABEL_SCRIM = Color(0xFFF4F8FB).copy(alpha = 0.15f)
private const val CATALOG_CARD_TITLE_SCALE = 1.40625f
private const val DISABLED_ALPHA = 0.38f
