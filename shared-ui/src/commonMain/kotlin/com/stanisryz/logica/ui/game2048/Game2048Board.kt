package com.stanisryz.logica.ui.game2048

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048TileMovement
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_board_description
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_empty_cell_description
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_move_down
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_move_left
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_move_right
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_move_up
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_tile_description
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** Shared 4x4 board, gestures, accessibility actions, and deterministic trace animation. */
@Composable
fun Game2048Board(
    game: Game2048State,
    motionRevision: Long?,
    motionTrace: Game2048MoveTrace?,
    onMove: (Game2048Direction) -> Unit,
    onMotionFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = !game.status.isTerminal,
) {
    require((motionRevision == null) == (motionTrace == null)) {
        "2048 motion revision and trace must either both be present or both be absent."
    }
    val moveLeft = stringResource(Res.string.game_2048_move_left)
    val moveRight = stringResource(Res.string.game_2048_move_right)
    val moveUp = stringResource(Res.string.game_2048_move_up)
    val moveDown = stringResource(Res.string.game_2048_move_down)
    val boardDescription = stringResource(Res.string.game_2048_board_description)
    val enabled = inputEnabled && motionTrace == null && !game.status.isTerminal
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
    var phase by
        remember(motionRevision) {
            mutableStateOf(if (motionTrace == null) Game2048MotionPhase.IDLE else Game2048MotionPhase.MOVING)
        }
    val movementProgress = remember(motionRevision) { Animatable(if (motionTrace == null) 1f else 0f) }
    val mergeScale = remember(motionRevision) { Animatable(1f) }
    val spawnScale = remember(motionRevision) { Animatable(SPAWN_INITIAL_SCALE) }

    LaunchedEffect(motionRevision) {
        val revision = motionRevision ?: return@LaunchedEffect
        val trace = motionTrace ?: return@LaunchedEffect
        phase = Game2048MotionPhase.MOVING
        movementProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(MOVEMENT_MILLIS, easing = FastOutSlowInEasing),
        )
        if (trace.merges.isNotEmpty()) {
            phase = Game2048MotionPhase.MERGING
            mergeScale.animateTo(MERGE_POP_SCALE, tween(MERGE_HALF_MILLIS))
            mergeScale.animateTo(1f, tween(MERGE_HALF_MILLIS))
        }
        if (trace.spawnedTile != null) {
            phase = Game2048MotionPhase.SPAWNING
            spawnScale.animateTo(1f, tween(SPAWN_MILLIS, easing = FastOutSlowInEasing))
        }
        phase = Game2048MotionPhase.IDLE
        onMotionFinished(revision)
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val boardSize = minOf(maxWidth, maxHeight)
        Box(
            modifier =
                Modifier
                    .size(boardSize)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .game2048Swipe(enabled, onMove)
                    .semantics {
                        contentDescription = boardDescription
                        customActions = actions
                    }.padding(LogicaSpacing.boardPadding),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cellSize =
                    (maxWidth - LogicaSpacing.boardGap * (Game2048State.BOARD_SIZE - 1)) /
                        Game2048State.BOARD_SIZE
                val cellStepPx = with(LocalDensity.current) { (cellSize + LogicaSpacing.boardGap).toPx() }
                Game2048BackgroundGrid(game)
                Game2048TileOverlay(
                    game = game,
                    motionTrace = motionTrace,
                    phase = phase,
                    movementProgress = movementProgress.value,
                    mergeScale = mergeScale.value,
                    spawnScale = spawnScale.value,
                    cellSize = cellSize,
                    cellStepPx = cellStepPx,
                )
            }
        }
    }
}

@Composable
private fun Game2048BackgroundGrid(game: Game2048State) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.boardGap),
    ) {
        repeat(Game2048State.BOARD_SIZE) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.boardGap),
            ) {
                repeat(Game2048State.BOARD_SIZE) { column ->
                    val value = game.cellAt(row, column)
                    val description =
                        if (value == 0) {
                            stringResource(Res.string.game_2048_empty_cell_description, row + 1, column + 1)
                        } else {
                            stringResource(Res.string.game_2048_tile_description, row + 1, column + 1, value)
                        }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .semantics { contentDescription = description },
                    )
                }
            }
        }
    }
}

@Composable
private fun Game2048TileOverlay(
    game: Game2048State,
    motionTrace: Game2048MoveTrace?,
    phase: Game2048MotionPhase,
    movementProgress: Float,
    mergeScale: Float,
    spawnScale: Float,
    cellSize: Dp,
    cellStepPx: Float,
) {
    when (phase) {
        Game2048MotionPhase.MOVING ->
            motionTrace?.movements.orEmpty().forEach { movement ->
                Game2048MovingTile(movement, movementProgress, cellSize, cellStepPx)
            }
        Game2048MotionPhase.MERGING,
        Game2048MotionPhase.SPAWNING,
        Game2048MotionPhase.IDLE,
        -> {
            val hiddenSpawnIndex =
                motionTrace?.spawnedTile?.destinationIndex.takeIf { phase != Game2048MotionPhase.IDLE }
            game.board.forEachIndexed { index, value ->
                if (value != 0 && index != hiddenSpawnIndex) {
                    val isMerging =
                        phase == Game2048MotionPhase.MERGING &&
                            motionTrace?.merges?.any { it.destinationIndex == index } == true
                    Game2048OverlayTile(
                        value = value,
                        index = index,
                        cellSize = cellSize,
                        cellStepPx = cellStepPx,
                        scale = if (isMerging) mergeScale else 1f,
                    )
                }
            }
            if (phase == Game2048MotionPhase.SPAWNING) {
                motionTrace?.spawnedTile?.let { spawned ->
                    Game2048OverlayTile(
                        value = spawned.value,
                        index = spawned.destinationIndex,
                        cellSize = cellSize,
                        cellStepPx = cellStepPx,
                        scale = spawnScale,
                    )
                }
            }
        }
    }
}

@Composable
private fun Game2048MovingTile(
    movement: Game2048TileMovement,
    progress: Float,
    cellSize: Dp,
    cellStepPx: Float,
) {
    val sourceColumn = movement.sourceIndex % Game2048State.BOARD_SIZE
    val sourceRow = movement.sourceIndex / Game2048State.BOARD_SIZE
    val destinationColumn = movement.destinationIndex % Game2048State.BOARD_SIZE
    val destinationRow = movement.destinationIndex / Game2048State.BOARD_SIZE
    val x = (sourceColumn + (destinationColumn - sourceColumn) * progress) * cellStepPx
    val y = (sourceRow + (destinationRow - sourceRow) * progress) * cellStepPx
    Game2048TileSurface(
        value = movement.value,
        modifier =
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(cellSize),
    )
}

@Composable
private fun Game2048OverlayTile(
    value: Int,
    index: Int,
    cellSize: Dp,
    cellStepPx: Float,
    scale: Float,
) {
    val column = index % Game2048State.BOARD_SIZE
    val row = index / Game2048State.BOARD_SIZE
    Game2048TileSurface(
        value = value,
        modifier =
            Modifier
                .offset { IntOffset((column * cellStepPx).roundToInt(), (row * cellStepPx).roundToInt()) }
                .size(cellSize)
                .scale(scale),
    )
}

@Composable
private fun Game2048TileSurface(
    value: Int,
    modifier: Modifier,
) {
    val (containerColor, contentColor) = tileColors(value, MaterialTheme.colorScheme)
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(containerColor)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
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

private enum class Game2048MotionPhase {
    IDLE,
    MOVING,
    MERGING,
    SPAWNING,
}

private val MIN_SWIPE_DISTANCE = 32.dp
private const val AXIS_DOMINANCE_RATIO = 1.25f
private const val MOVEMENT_MILLIS = 120
private const val MERGE_HALF_MILLIS = 45
private const val MERGE_POP_SCALE = 1.12f
private const val SPAWN_MILLIS = 95
private const val SPAWN_INITIAL_SCALE = 0.7f
