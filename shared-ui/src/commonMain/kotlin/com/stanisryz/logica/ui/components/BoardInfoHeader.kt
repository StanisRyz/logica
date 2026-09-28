package com.stanisryz.logica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The header that sits right on top of a square board. With [showTitle] it is a small title block:
 * the level (or the Daily label), the difficulty under it, and the mistakes as marks that turn red
 * one by one. Without it everything shares one line, as Sudoku needs every row for its board. A
 * thin bar shows how much of the board the player has closed; only cells the board already shows
 * as correct count, so it reveals nothing new.
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
    val progressDescription = stringResource(Res.string.board_progress_description, solvedCells, totalCells)
    val progress by animateFloatAsState(
        targetValue = if (totalCells > 0) (solvedCells.toFloat() / totalCells).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(PROGRESS_ANIMATION_MILLIS),
    )
    val placeLabel = contextLabel ?: levelNumber?.let { stringResource(Res.string.catalog_level, it) }.orEmpty()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = HEADER_HORIZONTAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(HEADER_ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showTitle) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (placeLabel.isNotEmpty()) {
                    Text(
                        text = placeLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                Text(
                    text = difficultyLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            MistakeMarks(mistakesUsed, maxMistakes)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = difficultyLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
                val mistakesDescription = stringResource(Res.string.mistakes_description, mistakesUsed, maxMistakes)
                Text(
                    text = stringResource(Res.string.sudoku_mistakes_short, mistakesUsed, maxMistakes),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = mistakesDescription },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (mistakesUsed == 0) colors.onSurfaceVariant else colors.error,
                    maxLines = 1,
                )
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

/** Mistakes as a row of marks: an empty ring per mistake left, a red cross per mistake made. */
@Composable
private fun MistakeMarks(
    mistakesUsed: Int,
    maxMistakes: Int,
) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(Res.string.mistakes_description, mistakesUsed, maxMistakes)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(MISTAKE_MARK_SPACING),
    ) {
        repeat(maxMistakes) { index ->
            val used = index < mistakesUsed
            Box(
                modifier =
                    Modifier
                        .size(MISTAKE_MARK_SIZE)
                        .clip(CircleShape)
                        .background(if (used) colors.error else colors.surfaceContainerHigh)
                        .border(MISTAKE_MARK_BORDER, if (used) colors.error else colors.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (used) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = colors.onError,
                        modifier = Modifier.size(MISTAKE_ICON_SIZE),
                    )
                }
            }
        }
    }
}

private val HEADER_HORIZONTAL_PADDING = 4.dp
private val HEADER_ROW_SPACING = 10.dp
private val PROGRESS_HEIGHT = 4.dp
private const val PROGRESS_ANIMATION_MILLIS = 250
private val MISTAKE_MARK_SIZE = 22.dp
private val MISTAKE_ICON_SIZE = 16.dp
private val MISTAKE_MARK_BORDER = 1.dp
private val MISTAKE_MARK_SPACING = 10.dp
