package com.stanisryz.logica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.ui.theme.LogicaSpacing

/** One headline metric with its label. */
internal data class Metric(
    val label: String,
    val value: String,
)

/**
 * Overall metrics as compact tiles, two per row, so the Profile summary can be scanned instead of
 * read. Tiles grow vertically, so long labels and large font scales wrap rather than clip.
 */
@Composable
internal fun MetricGrid(
    metrics: List<Metric>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        metrics.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            ) {
                row.forEach { metric -> MetricTile(metric, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(
    metric: Metric,
    modifier: Modifier = Modifier,
) {
    LogicaCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        verticalSpacing = LogicaSpacing.text,
    ) {
        MetricValue(metric.value)
        SupportingText(metric.label)
    }
}

/**
 * The Word attempt distribution as lightweight bars. Bars are relative to the busiest bucket and
 * the exact count stays visible, so an all-zero distribution simply draws empty tracks.
 */
@Composable
internal fun AttemptDistributionBars(
    counts: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    val maximum = counts.values.maxOrNull() ?: 0
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text * 2),
    ) {
        counts.toSortedMap().forEach { (attempts, count) ->
            AttemptBar(attempts = attempts, count = count, maximum = maximum)
        }
    }
}

@Composable
private fun AttemptBar(
    attempts: Int,
    count: Int,
    maximum: Int,
) {
    val colors = MaterialTheme.colorScheme
    val fraction by animateFloatAsState(barFraction(count, maximum), label = "attemptBar")
    val description = stringResource(R.string.word_attempt_bar_description, attempts, count)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
    ) {
        Text(
            text = attempts.toString(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = BAR_LABEL_WIDTH),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(BAR_HEIGHT)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(colors.surfaceContainerHighest),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(colors.primary),
                )
            }
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = BAR_COUNT_WIDTH).padding(start = LogicaSpacing.text),
        )
    }
}

private val BAR_HEIGHT = 18.dp
private val BAR_LABEL_WIDTH = 16.dp
private val BAR_COUNT_WIDTH = 24.dp
