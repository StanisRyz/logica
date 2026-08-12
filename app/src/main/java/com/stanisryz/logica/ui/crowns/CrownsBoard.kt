package com.stanisryz.logica.ui.crowns

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.crowns.CrownsCellStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.crowns.RegionId
import com.stanisryz.logica.ui.theme.LocalLogicaPalette

@Composable
internal fun CrownsBoard(
    puzzle: CrownsPuzzle,
    game: CrownsGameState,
    onCellTapped: (CrownsPosition) -> Unit,
    guidedPositions: Set<CrownsPosition> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val conflictPositions = remember(game.violations) { game.violations.flatMapTo(mutableSetOf()) { it.affectedPositions } }
    val regionNumbers =
        remember(puzzle) {
            puzzle.regionAssignments.values
                .distinct()
                .sortedBy(RegionId::value)
                .mapIndexed { index, id -> id to index + 1 }
                .toMap()
        }
    val hint = game.currentHint

    BoxWithConstraints(
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth().aspectRatio(1f),
    ) {
        // Pencil marks have to stay readable inside one cell of an 8x8 board on a small screen.
        val pencilSize = ((maxWidth / puzzle.size).value * PENCIL_RATIO).coerceIn(8f, 16f).dp
        Column(Modifier.fillMaxSize()) {
            repeat(puzzle.size) { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(puzzle.size) { column ->
                        val position = CrownsPosition(row, column)
                        val region = puzzle.regionAt(position)
                        CrownsCellView(
                            position = position,
                            regionNumber = checkNotNull(regionNumbers[region]),
                            cell = game.cellAt(position),
                            status = game.statusAt(position),
                            pencil = game.pencilAt(position),
                            pencilSize = pencilSize,
                            isConflict = position in conflictPositions,
                            isHintTarget = position in (hint?.targetPositions ?: emptySet()),
                            isHintEvidence = position in (hint?.evidencePositions ?: emptySet()),
                            isHintConflict = position in (hint?.conflictPositions ?: emptySet()),
                            isGuided = position in guidedPositions,
                            regionColorIndex = checkNotNull(regionNumbers[region]) - 1,
                            topBoundary = row == 0 || puzzle.regionAt(CrownsPosition(row - 1, column)) != region,
                            leftBoundary = column == 0 || puzzle.regionAt(CrownsPosition(row, column - 1)) != region,
                            bottomBoundary = row == puzzle.size - 1 || puzzle.regionAt(CrownsPosition(row + 1, column)) != region,
                            rightBoundary = column == puzzle.size - 1 || puzzle.regionAt(CrownsPosition(row, column + 1)) != region,
                            enabled = game.status == CrownsGameStatus.IN_PROGRESS && !game.isLocked(position),
                            onClick = { onCellTapped(position) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrownsCellView(
    position: CrownsPosition,
    regionNumber: Int,
    cell: CrownsPlayerCell,
    status: CrownsCellStatus,
    pencil: Set<CrownsPlayerCell>,
    pencilSize: Dp,
    isConflict: Boolean,
    isHintTarget: Boolean,
    isHintEvidence: Boolean,
    isHintConflict: Boolean,
    isGuided: Boolean,
    regionColorIndex: Int,
    topBoundary: Boolean,
    leftBoundary: Boolean,
    bottomBoundary: Boolean,
    rightBoundary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalLogicaPalette.current
    val regionColors = palette.crownsRegions
    val isIncorrect = status == CrownsCellStatus.INCORRECT
    val isConfirmed = status == CrownsCellStatus.CORRECT
    val background =
        when {
            isConflict || isIncorrect -> colors.errorContainer
            isHintConflict -> colors.errorContainer.copy(alpha = 0.72f)
            isHintTarget -> colors.tertiaryContainer
            isHintEvidence -> colors.secondaryContainer
            else -> regionColors[regionColorIndex % regionColors.size]
        }
    val targetSuffix = if (isHintTarget) stringResource(R.string.hint_target_suffix) else ""
    val evidenceSuffix = if (isHintEvidence) stringResource(R.string.hint_evidence_suffix) else ""
    val hintConflictSuffix = if (isHintConflict) stringResource(R.string.hint_conflict_suffix) else ""
    val guidedSuffix = if (isGuided) stringResource(R.string.crowns_guided_suffix) else ""
    val stateLabel =
        stringResource(
            when (status) {
                CrownsCellStatus.CORRECT -> R.string.confirmed_cell
                CrownsCellStatus.INCORRECT -> R.string.incorrect_cell
                else -> R.string.editable_cell
            },
        )
    val hintSuffix = targetSuffix + evidenceSuffix + hintConflictSuffix + guidedSuffix + pencil.pencilAccessibilitySuffix()
    val description =
        stringResource(
            R.string.crowns_cell_description,
            position.row + 1,
            position.column + 1,
            regionNumber,
            cell.accessibilityLabel(),
            stateLabel,
            if (isConflict) stringResource(R.string.conflict_suffix) else "",
            hintSuffix,
        )
    val boundaryColor = colors.outline
    val internalColor = colors.outlineVariant
    val confirmedRingColor = palette.onCrownsRegion.copy(alpha = CONFIRMED_RING_ALPHA)
    val strongWidth = 2.dp
    val thinWidth = 1.dp

    Box(
        modifier =
            modifier
                .background(background)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics {
                    contentDescription = description
                    role = androidx.compose.ui.semantics.Role.Button
                }.drawWithContent {
                    drawContent()
                    val strong = strongWidth.toPx()
                    val thin = thinWidth.toPx()
                    drawLine(
                        color = if (topBoundary) boundaryColor else internalColor,
                        start = Offset.Zero,
                        end = Offset(size.width, 0f),
                        strokeWidth = if (topBoundary) strong else thin,
                        cap = StrokeCap.Square,
                    )
                    drawLine(
                        color = if (leftBoundary) boundaryColor else internalColor,
                        start = Offset.Zero,
                        end = Offset(0f, size.height),
                        strokeWidth = if (leftBoundary) strong else thin,
                        cap = StrokeCap.Square,
                    )
                    if (bottomBoundary) {
                        drawLine(
                            boundaryColor,
                            Offset(0f, size.height),
                            Offset(size.width, size.height),
                            strong,
                            StrokeCap.Square,
                        )
                    }
                    if (rightBoundary) {
                        drawLine(
                            boundaryColor,
                            Offset(size.width, 0f),
                            Offset(size.width, size.height),
                            strong,
                            StrokeCap.Square,
                        )
                    }
                    when {
                        isConflict || isHintConflict || isIncorrect ->
                            drawRect(
                                color = colors.error,
                                style = Stroke(width = strong),
                            )
                        isHintTarget || isGuided ->
                            drawRect(
                                color = colors.primary,
                                style = Stroke(width = strong),
                            )
                        isHintEvidence ->
                            drawRect(
                                color = colors.secondary,
                                style =
                                    Stroke(
                                        width = thin * 2f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(thin * 3f, thin * 2f)),
                                    ),
                            )
                        // Confirmed cells get a restrained inset ring: it reads as "settled" without
                        // an icon on every cell and leaves the region colour fully visible.
                        isConfirmed -> {
                            val inset = strong * 1.5f
                            drawRect(
                                color = confirmedRingColor,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - 2f * inset, size.height - 2f * inset),
                                style = Stroke(width = thin),
                            )
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        val symbolTint = if (isConflict || isIncorrect) colors.onErrorContainer else palette.onCrownsRegion
        when (cell) {
            CrownsPlayerCell.EMPTY -> Unit
            CrownsPlayerCell.MARKED ->
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleMedium,
                    color = symbolTint,
                )
            CrownsPlayerCell.CROWN ->
                Icon(
                    painter = painterResource(R.drawable.ic_crown),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.55f),
                    tint = symbolTint,
                )
        }
        if (isIncorrect) {
            // A wrong value must be recognisable without relying on the error colour alone.
            Icon(
                imageVector = Icons.Filled.PriorityHigh,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.align(Alignment.TopStart).size(pencilSize),
            )
        }
        if (isConfirmed && cell != CrownsPlayerCell.EMPTY) {
            /*
             * A committed crown or blocked mark the puzzle's own answer agrees with, marked the same
             * way Sudoku marks a confirmed digit. The inset ring alone reads as a colour change on a
             * coloured region, so the confirmation is carried by an icon rather than by colour.
             * Pencil marks and wrong values never reach this: only a `CORRECT` committed cell does.
             */
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = palette.success,
                modifier = Modifier.align(Alignment.TopEnd).padding(1.dp).size(pencilSize),
            )
        }
        if (pencil.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                if (CrownsPlayerCell.CROWN in pencil) {
                    Icon(
                        painter = painterResource(R.drawable.ic_crown),
                        contentDescription = null,
                        modifier = Modifier.size(pencilSize),
                        tint = palette.onCrownsRegion.copy(alpha = PENCIL_ALPHA),
                    )
                }
                if (CrownsPlayerCell.MARKED in pencil) {
                    Text(
                        text = "×",
                        fontSize = pencilSize.value.sp,
                        lineHeight = pencilSize.value.sp,
                        color = palette.onCrownsRegion.copy(alpha = PENCIL_ALPHA),
                    )
                }
            }
        }
    }
}

@Composable
private fun Set<CrownsPlayerCell>.pencilAccessibilitySuffix(): String =
    if (isEmpty()) {
        ""
    } else {
        val labels = sortedBy(CrownsPlayerCell::ordinal).map { it.accessibilityLabel() }
        stringResource(R.string.pencil_marks_suffix, labels.joinToString(separator = ", "))
    }

@Composable
private fun CrownsPlayerCell.accessibilityLabel(): String =
    stringResource(
        when (this) {
            CrownsPlayerCell.EMPTY -> R.string.crowns_cell_empty
            CrownsPlayerCell.MARKED -> R.string.crowns_cell_marked
            CrownsPlayerCell.CROWN -> R.string.crowns_cell_crown
        },
    )

private const val PENCIL_RATIO = 0.3f
private const val PENCIL_ALPHA = 0.7f
private const val CONFIRMED_RING_ALPHA = 0.4f
