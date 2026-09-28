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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
            val roomy = isRoomyPortrait(maxWidth, maxHeight)
            CompositionLocalProvider(LocalRoomyGameplayControls provides roomy) {
                CenteredBoardLayout(
                    spacing = sectionSpacing,
                    anchorControlsToBottom = roomy,
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
}

private val COMPACT_HEIGHT_THRESHOLD = 700.dp
private val COMPACT_VERTICAL_PADDING = 6.dp
private val NORMAL_VERTICAL_PADDING = 10.dp
private val COMPACT_SECTION_SPACING = 6.dp
private val WIDE_PANEL_MAX_WIDTH = 232.dp
private val ROOMY_EXTRA_HEIGHT = 320.dp

/**
 * True inside a tall portrait gameplay area, where the controls grow and sit at the bottom edge,
 * under the thumb; shorter screens keep the compact controls right under the board.
 */
internal val LocalRoomyGameplayControls = staticCompositionLocalOf { false }

/** A portrait area is roomy when its height beats its width by more than the controls need. */
internal fun isRoomyPortrait(
    width: Dp,
    height: Dp,
): Boolean = height - width >= ROOMY_EXTRA_HEIGHT

private const val WIDE_PANEL_WIDTH_FRACTION = 0.42f

/**
 * Portrait board scene: the header sits right on top of the square board and the controls right
 * under it, and that whole group is centred in the free height, so spare room splits evenly above
 * and below instead of opening a gap between the header and the board. With
 * [anchorControlsToBottom] the controls sit at the bottom edge instead and the header and board are
 * centred in the height above them. The board is as wide as the area allows and shrinks only when
 * the height runs out.
 */
@Composable
internal fun CenteredBoardLayout(
    spacing: Dp,
    modifier: Modifier = Modifier,
    anchorControlsToBottom: Boolean = false,
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
        val groupHeight = headerHeight + gap + side + gap + controlsHeight
        val groupTop: Int
        val controlsTop: Int
        if (anchorControlsToBottom) {
            controlsTop = height - controlsHeight
            groupTop = ((controlsTop - gap - (headerHeight + gap + side)) / 2).coerceAtLeast(0)
        } else {
            groupTop = ((height - groupHeight) / 2).coerceAtLeast(0)
            controlsTop = groupTop + headerHeight + gap + side + gap
        }
        val boardTop = groupTop + headerHeight + gap
        layout(width, height) {
            var y = groupTop
            headers.forEach { placeable ->
                placeable.placeRelative((width - placeable.width) / 2, y)
                y += placeable.height + gap
            }
            boards.forEach { it.placeRelative((width - side) / 2, boardTop) }
            y = controlsTop
            controlsPlaced.forEach { placeable ->
                placeable.placeRelative((width - placeable.width) / 2, y)
                y += placeable.height + gap
            }
        }
    }
}
