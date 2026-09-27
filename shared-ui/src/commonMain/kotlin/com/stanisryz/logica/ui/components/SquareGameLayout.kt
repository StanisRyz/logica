package com.stanisryz.logica.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.ui.theme.LogicaSpacing

/** Constraint-driven scene for the square-board games. The puzzle stays the largest element. */
@Composable
internal fun SquareGameLayout(
    modifier: Modifier = Modifier,
    metadataContent: @Composable ColumnScope.(wideLayout: Boolean) -> Unit,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
    boardContent: @Composable BoxScope.() -> Unit,
    toolContent: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
        val wideLayout = isWideGameplayLayout(maxWidth, maxHeight)
        val verticalPadding = if (compact) COMPACT_VERTICAL_PADDING else NORMAL_VERTICAL_PADDING
        val sectionSpacing = if (compact) COMPACT_SECTION_SPACING else LogicaSpacing.item
        val panelWidth = minOf(WIDE_PANEL_MAX_WIDTH, maxWidth * WIDE_PANEL_WIDTH_FRACTION)

        if (wideLayout) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = LogicaSpacing.screenHorizontal, vertical = verticalPadding)
                        .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(sectionSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                    content = boardContent,
                )
                Column(
                    modifier = Modifier.width(panelWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    metadataContent(true)
                    hostStatusContent()
                    Spacer(Modifier.weight(1f))
                    toolContent()
                }
            }
        } else {
            CenteredBoardLayout(
                spacing = sectionSpacing,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = LogicaSpacing.screenHorizontal, vertical = verticalPadding),
                header = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        metadataContent(false)
                        hostStatusContent()
                    }
                },
                board = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = boardContent) },
                controls = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { toolContent() } },
            )
        }
    }
}

private val COMPACT_HEIGHT_THRESHOLD = 700.dp
private val COMPACT_VERTICAL_PADDING = 6.dp
private val NORMAL_VERTICAL_PADDING = 10.dp
private val COMPACT_SECTION_SPACING = 6.dp
private val WIDE_PANEL_MAX_WIDTH = 232.dp
private const val WIDE_PANEL_WIDTH_FRACTION = 0.42f

/**
 * Portrait board scene: the header on top, the square board centred in the whole area, and the
 * controls directly under the board. The board only moves off centre, and only shrinks, when the
 * header or the controls would otherwise not fit.
 */
@Composable
internal fun CenteredBoardLayout(
    spacing: Dp,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    board: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    Layout(contents = listOf(header, board, controls), modifier = modifier) { measurables, constraints ->
        val (headerParts, boardParts, controlParts) = measurables
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = spacing.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val headers = headerParts.map { it.measure(loose) }
        val controlsPlaced = controlParts.map { it.measure(loose) }
        val headerHeight = headers.sumOf { it.height } + gap * (headers.size - 1).coerceAtLeast(0)
        val controlsHeight = controlsPlaced.sumOf { it.height } + gap * (controlsPlaced.size - 1).coerceAtLeast(0)
        val side = minOf(width, height - headerHeight - controlsHeight - gap * 2).coerceAtLeast(0)
        val boards = boardParts.map { it.measure(Constraints.fixed(side, side)) }
        val minTop = headerHeight + gap
        val maxTop = height - controlsHeight - gap - side
        val boardTop = ((height - side) / 2).coerceAtMost(maxTop).coerceAtLeast(minTop)
        layout(width, height) {
            var y = 0
            headers.forEach { placeable ->
                placeable.placeRelative((width - placeable.width) / 2, y)
                y += placeable.height + gap
            }
            boards.forEach { it.placeRelative((width - side) / 2, boardTop) }
            y = boardTop + side + gap
            controlsPlaced.forEach { placeable ->
                placeable.placeRelative((width - placeable.width) / 2, y)
                y += placeable.height + gap
            }
        }
    }
}
