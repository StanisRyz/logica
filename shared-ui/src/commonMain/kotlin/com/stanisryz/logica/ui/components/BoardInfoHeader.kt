package com.stanisryz.logica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.board_progress_description
import com.stanisryz.logica.shared.ui.generated.resources.catalog_level
import com.stanisryz.logica.shared.ui.generated.resources.mistakes_description
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_mistakes_short
import org.jetbrains.compose.resources.stringResource

/**
 * The header that sits right on top of a square board. With [showTitle] the level (or the Daily
 * label) becomes a title over a difficulty/mistakes line; without it everything shares one line,
 * as Sudoku needs every row for its board. A thin bar shows how much of the board the player has
 * closed; only cells the board already shows as correct count, so it reveals nothing new.
 */
@Composable
fun BoardInfoHeader(
    difficultyLabel: String,
    mistakesUsed: Int,
    maxMistakes: Int,
    levelNumber: Int?,
    contextLabel: String?,
    solvedCells: Int,
    totalCells: Int,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val mistakesDescription = stringResource(Res.string.mistakes_description, mistakesUsed, maxMistakes)
    val progressDescription = stringResource(Res.string.board_progress_description, solvedCells, totalCells)
    val progress by animateFloatAsState(
        targetValue = if (totalCells > 0) (solvedCells.toFloat() / totalCells).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(PROGRESS_ANIMATION_MILLIS),
    )
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = HEADER_HORIZONTAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(HEADER_ROW_SPACING),
    ) {
        val placeLabel = contextLabel ?: levelNumber?.let { stringResource(Res.string.catalog_level, it) }.orEmpty()
        if (showTitle && placeLabel.isNotEmpty()) {
            Text(
                text = placeLabel,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = difficultyLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = stringResource(Res.string.sudoku_mistakes_short, mistakesUsed, maxMistakes),
                modifier = Modifier.clearAndSetSemantics { contentDescription = mistakesDescription },
                style = MaterialTheme.typography.bodyLarge,
                color = if (mistakesUsed == 0) colors.onSurfaceVariant else colors.error,
                maxLines = 1,
            )
            // Under a title the line is difficulty on the left and mistakes on the right.
            if (!showTitle) {
                Text(
                    text = placeLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PROGRESS_HEIGHT)
                    .clearAndSetSemantics { contentDescription = progressDescription },
            color = colors.primary,
            trackColor = colors.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private val HEADER_HORIZONTAL_PADDING = 4.dp
private val HEADER_ROW_SPACING = 8.dp
private val PROGRESS_HEIGHT = 4.dp
private const val PROGRESS_ANIMATION_MILLIS = 250
