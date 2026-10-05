package com.stanisryz.logica.ui.blocksudoku

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockCell
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockPiece
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuRules
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuState
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuStatus
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_board_description
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_cell_filled
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_cell_free
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_piece_description
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_piece_unfit
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_place_here
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_score
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_target
import com.stanisryz.logica.shared.ui.generated.resources.board_cell_state_description
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.ui.components.BoardTitle
import com.stanisryz.logica.ui.components.CenteredBoardLayout
import com.stanisryz.logica.ui.components.GameDock
import com.stanisryz.logica.ui.components.GameSound
import com.stanisryz.logica.ui.components.LocalGameSounds
import com.stanisryz.logica.ui.components.SemanticCellGrid
import com.stanisryz.logica.ui.components.isWideGameplayLayout
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Shared Block Sudoku presentation. A piece is dragged from the tray onto the board — it floats a
 * little above the finger, shows where it would land, and lights up the lines and boxes it would
 * clear — or tapped in the tray and then placed with a tap on the board. The host owns the engine,
 * statistics, economy, and every terminal decision; this only reports the chosen placement.
 */
@Composable
fun BlockSudokuContent(
    state: BlockSudokuState,
    difficulty: Difficulty,
    levelNumber: Int?,
    gameplayEnabled: Boolean,
    onPlace: (trayIndex: Int, row: Int, column: Int) -> Unit,
    modifier: Modifier = Modifier,
    contextBadgeLabel: String? = null,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
) {
    val sounds = LocalGameSounds.current
    // A placement taps, a clear pops (once, however many lines), the target rings once, and a stuck
    // tray sighs; the placement that ends the game sounds only its outcome, and the first frame nothing.
    val wasTerminal = remember { mutableStateOf(state.status.isTerminal) }
    val lastPlacements = remember { mutableIntStateOf(state.placements) }
    LaunchedEffect(state.placements, state.status) {
        val outcome =
            when {
                !state.status.isTerminal || wasTerminal.value -> null
                state.status == BlockSudokuStatus.SOLVED -> GameSound.WIN
                else -> GameSound.FAIL
            }
        val placed = state.placements > lastPlacements.intValue
        wasTerminal.value = state.status.isTerminal
        lastPlacements.intValue = state.placements
        val move =
            if (!placed) {
                null
            } else if (state.lastCleared.isNotEmpty()) {
                GameSound.MERGE
            } else {
                GameSound.TAP
            }
        (outcome ?: move)?.let(sounds::play)
    }

    var dragging by remember { mutableStateOf<DragState?>(null) }
    var selected by remember(state.deal) { mutableStateOf<Int?>(null) }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    var boardBounds by remember { mutableStateOf(Rect.Zero) }
    val interactive = gameplayEnabled && state.status == BlockSudokuStatus.IN_PROGRESS
    val currentState by rememberUpdatedState(state)
    val currentOnPlace by rememberUpdatedState(onPlace)

    // Where a dragged piece would land: its top-left cell on the board, or null when nothing fits there.
    fun anchorFor(
        drag: DragState,
        piece: BlockPiece,
    ): BlockCell? {
        if (boardBounds.width <= 0f) return null
        val cell = boardBounds.width / BlockSudokuRules.SIZE
        val topLeft = dragTopLeft(drag, piece, cell)
        return blockSudokuDropAnchor(
            board = currentState.board,
            piece = piece,
            exactRow = (topLeft.y - boardBounds.top) / cell,
            exactColumn = (topLeft.x - boardBounds.left) / cell,
        )
    }

    val header: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HEADER_SPACING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoardTitle(stringResource(difficulty.labelResource()), levelNumber, contextBadgeLabel)
            ScoreLine(state)
            hostStatusContent()
        }
    }
    val preview =
        dragging?.let { drag -> state.tray.getOrNull(drag.trayIndex)?.let { piece -> anchorFor(drag, piece)?.let { piece to it } } }
    val board: @Composable () -> Unit = {
        BlockBoard(
            state = state,
            preview = preview,
            placeEnabled = interactive && selected?.let { state.tray.getOrNull(it) } != null,
            onTapCell = { cell ->
                val index = selected ?: return@BlockBoard
                val piece = currentState.tray.getOrNull(index) ?: return@BlockBoard
                val row = cell.row - (piece.height - 1) / 2
                val column = cell.column - (piece.width - 1) / 2
                if (interactive && BlockSudokuRules.canPlace(currentState.board, piece, row, column)) {
                    selected = null
                    currentOnPlace(index, row, column)
                }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { boardBounds = it.boundsInRoot().translate(-containerOrigin) },
        )
    }
    val tray: @Composable () -> Unit = {
        GameDock {
            Row(
                modifier = Modifier.fillMaxWidth().height(TRAY_HEIGHT),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.tray.forEachIndexed { index, piece ->
                    TraySlot(
                        piece = piece,
                        fits = piece != null && BlockSudokuRules.fitsAnywhere(state.board, piece),
                        selected = selected == index,
                        hidden = dragging?.trayIndex == index,
                        enabled = interactive,
                        onTap = { selected = if (selected == index) null else index },
                        onDrag = { pointer, lift ->
                            dragging = if (pointer == null) null else DragState(index, pointer - containerOrigin, lift)
                        },
                        onDrop = { pointer, lift ->
                            val drag = DragState(index, pointer - containerOrigin, lift)
                            val droppedPiece = currentState.tray.getOrNull(index)
                            val anchor = droppedPiece?.let { anchorFor(drag, it) }
                            dragging = null
                            if (anchor != null) {
                                selected = null
                                currentOnPlace(index, anchor.row, anchor.column)
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { containerOrigin = it.boundsInRoot().topLeft },
    ) {
        val wide = isWideGameplayLayout(maxWidth, maxHeight)
        val panelWidth = minOf(WIDE_PANEL_MAX_WIDTH, maxWidth * WIDE_PANEL_FRACTION)
        if (wide) {
            Row(
                modifier =
                    Modifier.fillMaxSize().padding(
                        horizontal = LogicaSpacing.screenHorizontal,
                        vertical = LogicaSpacing.screenVertical,
                    ),
                horizontalArrangement = Arrangement.spacedBy(WIDE_GROUP_SPACING, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.weight(1f, fill = false).fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true),
                    contentAlignment = Alignment.Center,
                ) { board() }
                Column(
                    modifier = Modifier.width(panelWidth),
                    verticalArrangement = Arrangement.spacedBy(WIDE_PANEL_SPACING),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    tray()
                }
            }
        } else {
            CenteredBoardLayout(
                spacing = LogicaSpacing.item,
                anchorControlsToBottom = true,
                modifier =
                    Modifier.fillMaxSize().padding(
                        horizontal = LogicaSpacing.screenHorizontal,
                        vertical = LogicaSpacing.screenVertical,
                    ),
                header = header,
                board = board,
                controls = tray,
            )
        }
        // The dragged piece floats at board size above the finger, over everything.
        dragging?.let { drag ->
            val piece = state.tray.getOrNull(drag.trayIndex) ?: return@let
            val cell = boardBounds.width / BlockSudokuRules.SIZE
            if (cell > 0f) {
                val color = if (preview != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                Canvas(Modifier.fillMaxSize()) {
                    val topLeft = dragTopLeft(drag, piece, cell)
                    piece.cells.forEach { block -> drawBlock(topLeft + Offset(block.column * cell, block.row * cell), cell, color) }
                }
            }
        }
    }
}

/** One drag in progress: which tray piece, the pointer in the content's own coordinates, and its lift. */
private data class DragState(
    val trayIndex: Int,
    val pointer: Offset,
    val lift: Float,
)

/** The dragged piece is centred over the finger with its bottom edge [DragState.lift] above it, whatever its height. */
private fun dragTopLeft(
    drag: DragState,
    piece: BlockPiece,
    cell: Float,
): Offset = drag.pointer - Offset(piece.width * cell / 2f, piece.height * cell + drag.lift)

@Composable
private fun BlockBoard(
    state: BlockSudokuState,
    preview: Pair<BlockPiece, BlockCell>?,
    placeEnabled: Boolean,
    onTapCell: (BlockCell) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalLogicaPalette.current
    val description = stringResource(Res.string.block_sudoku_board_description, state.board.count { it })
    val previewCells =
        preview?.let { (piece, anchor) -> piece.cells.map { BlockCell(anchor.row + it.row, anchor.column + it.column) }.toSet() }.orEmpty()
    val clearing = preview?.let { (piece, anchor) -> BlockSudokuRules.clearedBy(state.board, piece, anchor.row, anchor.column) }.orEmpty()
    // The cells the last placement cleared fade out once, so a clear reads as a clear.
    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.placements) {
        if (state.lastCleared.isEmpty()) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, tween(CLEAR_FLASH_MILLIS))
    }
    val currentOnTap by rememberUpdatedState(onTapCell)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(
            Modifier
                .aspectRatio(1f)
                .width(side)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surfaceContainerLowest)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val up = waitForUpOrCancel() ?: return@awaitEachGesture
                        val cell = size.width / BlockSudokuRules.SIZE.toFloat()
                        val column = (up.x / cell).toInt()
                        val row = (up.y / cell).toInt()
                        if (row in 0 until BlockSudokuRules.SIZE &&
                            column in 0 until BlockSudokuRules.SIZE
                        ) {
                            currentOnTap(BlockCell(row, column))
                        }
                        down.consume()
                    }
                },
        ) {
            val size = BlockSudokuRules.SIZE
            val cell = this.size.width / size
            // Alternate 3x3 boxes are tinted, like a Sudoku grid.
            for (boxRow in 0 until 3) {
                for (boxColumn in 0 until 3) {
                    if ((boxRow + boxColumn) % 2 == 1) {
                        drawRect(colors.surfaceContainer, Offset(boxColumn * 3 * cell, boxRow * 3 * cell), Size(3 * cell, 3 * cell))
                    }
                }
            }
            for (row in 0 until size) {
                for (column in 0 until size) {
                    val position = BlockCell(row, column)
                    val origin = Offset(column * cell, row * cell)
                    when {
                        state.isFilled(row, column) ->
                            drawBlock(origin, cell, if (position in clearing) palette.success else colors.primary)
                        position in previewCells ->
                            drawBlock(
                                origin,
                                cell,
                                (if (clearing.isNotEmpty()) palette.success else colors.primary).copy(alpha = PREVIEW_ALPHA),
                            )
                        position in state.lastCleared && flash.value > 0f ->
                            drawBlock(origin, cell, palette.success.copy(alpha = flash.value))
                    }
                }
            }
            for (line in 0..size) {
                val strong = line % 3 == 0
                val stroke = if (strong) 2.dp.toPx() else 1.dp.toPx()
                val color = if (strong) colors.outline else colors.outlineVariant
                drawLine(color, Offset(line * cell, 0f), Offset(line * cell, this.size.height), stroke, StrokeCap.Square)
                drawLine(color, Offset(0f, line * cell), Offset(this.size.width, line * cell), stroke, StrokeCap.Square)
            }
        }
        // Each cell for a screen reader, and with a selected piece a «place here» action like a tap.
        val taken = stringResource(Res.string.block_sudoku_cell_filled)
        val free = stringResource(Res.string.block_sudoku_cell_free)
        SemanticCellGrid(
            size = BlockSudokuRules.SIZE,
            modifier = Modifier.size(side),
            cellDescription = { row, column ->
                stringResource(
                    Res.string.board_cell_state_description,
                    row + 1,
                    column + 1,
                    if (state.isFilled(row, column)) taken else free,
                )
            },
            actionLabel = stringResource(Res.string.block_sudoku_place_here),
            cellAction = { row, column -> if (placeEnabled) ({ currentOnTap(BlockCell(row, column)) }) else null },
        )
    }
}

/** Waits for the pointer to lift; null when the gesture was taken by something else (a drag, a pinch). */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUpOrCancel(): Offset? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: return null
        if (change.isConsumed) return null
        if (!change.pressed) return change.position
    }
}

@Composable
private fun TraySlot(
    piece: BlockPiece?,
    fits: Boolean,
    selected: Boolean,
    hidden: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onDrag: (pointer: Offset?, lift: Float) -> Unit,
    onDrop: (pointer: Offset, lift: Float) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var origin by remember { mutableStateOf(Offset.Zero) }
    val currentOrigin by rememberUpdatedState(origin)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnTap by rememberUpdatedState(onTap)
    val pieceDescription =
        piece
            ?.let {
                val shape =
                    pluralStringResource(Res.plurals.block_sudoku_piece_description, it.cells.size, it.cells.size, it.width, it.height)
                if (fits) shape else stringResource(Res.string.block_sudoku_piece_unfit, shape)
            }.orEmpty()
    val alpha by animateFloatAsState(
        if (piece == null || hidden) {
            0f
        } else if (fits) {
            1f
        } else {
            UNFIT_ALPHA
        },
    )
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .clearAndSetSemantics {
                // A piece is a button that selects it for «tap a cell to place»; an empty slot says nothing.
                if (piece != null && !hidden) {
                    contentDescription = pieceDescription
                    this.selected = selected
                    role = Role.Button
                    if (enabled && fits) {
                        onClick {
                            currentOnTap()
                            true
                        }
                    }
                }
            }.then(
                if (enabled && piece != null && fits) {
                    Modifier.pointerInput(piece) {
                        val lift = FINGER_GAP.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var moved = false
                            var last = down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                last = change.position
                                if (!change.pressed) break
                                if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                if (moved) {
                                    change.consume()
                                    currentOnDrag(currentOrigin + change.position, lift)
                                }
                            }
                            if (moved) currentOnDrop(currentOrigin + last, lift) else currentOnTap()
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (piece != null) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val cell = minOf(this.size.width / TRAY_CELLS, this.size.height / TRAY_CELLS)
                val offset = Offset((this.size.width - piece.width * cell) / 2f, (this.size.height - piece.height * cell) / 2f)
                piece.cells.forEach { block ->
                    drawBlock(offset + Offset(block.column * cell, block.row * cell), cell, colors.primary.copy(alpha = alpha))
                }
            }
        }
    }
}

private fun DrawScope.drawBlock(
    origin: Offset,
    cell: Float,
    color: Color,
) {
    val inset = cell * BLOCK_INSET
    drawRoundRect(
        color = color,
        topLeft = origin + Offset(inset, inset),
        size = Size(cell - 2 * inset, cell - 2 * inset),
        cornerRadius = CornerRadius(cell * BLOCK_CORNER),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.18f * color.alpha),
        topLeft = origin + Offset(inset, inset),
        size = Size(cell - 2 * inset, cell - 2 * inset),
        cornerRadius = CornerRadius(cell * BLOCK_CORNER),
        style = Stroke(width = cell * 0.06f),
    )
}

@Composable
private fun ScoreLine(state: BlockSudokuState) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalLogicaPalette.current
    val reached = state.status == BlockSudokuStatus.SOLVED
    val progress by animateFloatAsState((state.score.toFloat() / state.targetScore).coerceIn(0f, 1f), tween(SCORE_PROGRESS_MILLIS))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.block_sudoku_score),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(state.score.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(Res.string.block_sudoku_target),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    state.targetScore.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (reached) palette.success else colors.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clearAndSetSemantics { },
            color = if (reached) palette.success else colors.primary,
            trackColor = colors.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val HEADER_SPACING = 10.dp
private val TRAY_HEIGHT = 112.dp

/** Gap between the touch point and the dragged piece's bottom edge, so the finger never covers it. */
private val FINGER_GAP = 24.dp
private val WIDE_GROUP_SPACING = 40.dp
private val WIDE_PANEL_SPACING = 28.dp
private val WIDE_PANEL_MAX_WIDTH = 340.dp
private const val WIDE_PANEL_FRACTION = 0.38f
private const val TRAY_CELLS = 5f
private const val UNFIT_ALPHA = 0.3f
private const val PREVIEW_ALPHA = 0.4f
private const val BLOCK_INSET = 0.06f
private const val BLOCK_CORNER = 0.18f
private const val CLEAR_FLASH_MILLIS = 380
private const val SCORE_PROGRESS_MILLIS = 250
