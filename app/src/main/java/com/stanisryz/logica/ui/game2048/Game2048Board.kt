package com.stanisryz.logica.ui.game2048

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlin.math.abs

@Composable
internal fun Game2048Board(
    game: Game2048State,
    onMove: (Game2048Direction) -> Unit,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = !game.status.isTerminal,
) {
    val moveLeft = stringResource(R.string.game_2048_move_left)
    val moveRight = stringResource(R.string.game_2048_move_right)
    val moveUp = stringResource(R.string.game_2048_move_up)
    val moveDown = stringResource(R.string.game_2048_move_down)
    val boardDescription = stringResource(R.string.game_2048_board_description)
    val enabled = inputEnabled && !game.status.isTerminal
    val actions =
        if (enabled) {
            listOf(
                semanticMove(moveLeft, Game2048Direction.LEFT, onMove),
                semanticMove(moveRight, Game2048Direction.RIGHT, onMove),
                semanticMove(moveUp, Game2048Direction.UP, onMove),
                semanticMove(moveDown, Game2048Direction.DOWN, onMove),
            )
        } else {
            emptyList()
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .game2048Swipe(enabled, onMove)
                .semantics {
                    contentDescription = boardDescription
                    customActions = actions
                }.padding(LogicaSpacing.boardPadding),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.boardGap),
    ) {
        repeat(Game2048State.BOARD_SIZE) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.boardGap),
            ) {
                repeat(Game2048State.BOARD_SIZE) { column ->
                    Game2048Tile(
                        value = game.cellAt(row, column),
                        row = row,
                        column = column,
                        modifier = Modifier.fillMaxHeight().weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun Game2048Tile(
    value: Int,
    row: Int,
    column: Int,
    modifier: Modifier = Modifier,
) {
    val description =
        if (value == 0) {
            stringResource(R.string.game_2048_empty_cell_description, row + 1, column + 1)
        } else {
            stringResource(R.string.game_2048_tile_description, row + 1, column + 1, value)
        }
    val (containerColor, contentColor) = tileColors(value, MaterialTheme.colorScheme)
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(containerColor)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (value != 0) {
            Text(
                text = value.toString(),
                modifier = Modifier.padding(LogicaSpacing.text),
                color = contentColor,
                style =
                    when (value.toString().length) {
                        1, 2 -> MaterialTheme.typography.headlineMedium
                        3 -> MaterialTheme.typography.headlineSmall
                        else -> MaterialTheme.typography.titleLarge
                    },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Modifier.game2048Swipe(
    enabled: Boolean,
    onMove: (Game2048Direction) -> Unit,
): Modifier {
    val threshold = with(LocalDensity.current) { MIN_SWIPE_DISTANCE.toPx() }
    return pointerInput(enabled, threshold, onMove) {
        if (!enabled) return@pointerInput
        var distance = Offset.Zero
        detectDragGestures(
            onDragStart = { distance = Offset.Zero },
            onDragCancel = { distance = Offset.Zero },
            onDragEnd = {
                directionFor(distance, threshold)?.let(onMove)
                distance = Offset.Zero
            },
            onDrag = { change, dragAmount ->
                change.consume()
                distance += dragAmount
            },
        )
    }
}

private fun directionFor(
    distance: Offset,
    threshold: Float,
): Game2048Direction? {
    val horizontal = abs(distance.x)
    val vertical = abs(distance.y)
    val dominant = maxOf(horizontal, vertical)
    val secondary = minOf(horizontal, vertical)
    if (dominant < threshold || dominant < secondary * AXIS_DOMINANCE_RATIO) return null
    return if (horizontal > vertical) {
        if (distance.x > 0f) Game2048Direction.RIGHT else Game2048Direction.LEFT
    } else {
        if (distance.y > 0f) Game2048Direction.DOWN else Game2048Direction.UP
    }
}

private fun semanticMove(
    label: String,
    direction: Game2048Direction,
    onMove: (Game2048Direction) -> Unit,
): CustomAccessibilityAction =
    CustomAccessibilityAction(label) {
        onMove(direction)
        true
    }

private fun tileColors(
    value: Int,
    colors: ColorScheme,
): Pair<Color, Color> =
    when (value) {
        0 -> colors.surfaceContainerLow to colors.onSurfaceVariant
        2 -> colors.surface to colors.onSurface
        4 -> colors.secondaryContainer to colors.onSecondaryContainer
        8 -> colors.tertiaryContainer to colors.onTertiaryContainer
        16 -> colors.primaryContainer to colors.onPrimaryContainer
        32 -> colors.inverseSurface to colors.inverseOnSurface
        64 -> colors.primary to colors.onPrimary
        128 -> colors.secondary to colors.onSecondary
        256 -> colors.tertiary to colors.onTertiary
        512 -> colors.primaryContainer to colors.onPrimaryContainer
        1024 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.inverseSurface to colors.inverseOnSurface
    }

private val MIN_SWIPE_DISTANCE = 32.dp
private const val AXIS_DOMINANCE_RATIO = 1.25f
