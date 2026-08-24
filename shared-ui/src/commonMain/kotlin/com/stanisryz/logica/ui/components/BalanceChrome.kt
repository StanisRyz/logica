package com.stanisryz.logica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.catalog_level
import com.stanisryz.logica.shared.ui.generated.resources.mistakes_description
import com.stanisryz.logica.shared.ui.generated.resources.mistakes_label
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.stringResource

@Composable
fun DifficultyBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = BADGE_HORIZONTAL_PADDING, vertical = BADGE_VERTICAL_PADDING),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun GameHeaderBadges(
    difficultyLabel: String,
    levelNumber: Int?,
    modifier: Modifier = Modifier,
    contextLabel: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
    ) {
        DifficultyBadge(difficultyLabel)
        levelNumber?.let { level -> DifficultyBadge(stringResource(Res.string.catalog_level, level)) }
        contextLabel?.let { extra -> DifficultyBadge(extra) }
    }
}

@Composable
fun MistakeIndicator(
    mistakesUsed: Int,
    maxMistakes: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.mistakes_description, mistakesUsed, maxMistakes)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
    ) {
        Text(
            text = stringResource(Res.string.mistakes_label, mistakesUsed, maxMistakes),
            style = MaterialTheme.typography.labelLarge,
            color =
                if (mistakesUsed == 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
        repeat(maxMistakes) { index ->
            val used = index < mistakesUsed
            Box(
                modifier =
                    Modifier
                        .size(MISTAKE_DOT_SIZE)
                        .clip(CircleShape)
                        .background(if (used) MaterialTheme.colorScheme.error else Color.Transparent)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

@Composable
fun GameMessage(
    text: String?,
    modifier: Modifier = Modifier,
    isError: Boolean = true,
) {
    AnimatedVisibility(text != null, modifier = modifier) {
        Text(
            text = text.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private val BADGE_HORIZONTAL_PADDING = 12.dp
private val BADGE_VERTICAL_PADDING = 6.dp
private val MISTAKE_DOT_SIZE = 10.dp
