package com.stanisryz.logica.ui.crowns

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.crowns.RegionId

@Composable
internal fun CrownsBoard(
    puzzle: CrownsPuzzle,
    game: CrownsGameState,
    onCellTapped: (CrownsPosition) -> Unit,
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

    Box(
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth().aspectRatio(1f),
    ) {
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
                            isConflict = position in conflictPositions,
                            isHintTarget = position in (hint?.targetPositions ?: emptySet()),
                            isHintEvidence = position in (hint?.evidencePositions ?: emptySet()),
                            isHintConflict = position in (hint?.conflictPositions ?: emptySet()),
                            regionColorIndex = checkNotNull(regionNumbers[region]) - 1,
                            topBoundary = row == 0 || puzzle.regionAt(CrownsPosition(row - 1, column)) != region,
                            leftBoundary = column == 0 || puzzle.regionAt(CrownsPosition(row, column - 1)) != region,
                            bottomBoundary = row == puzzle.size - 1,
                            rightBoundary = column == puzzle.size - 1,
                            enabled = game.status == CrownsGameStatus.IN_PROGRESS,
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
    isConflict: Boolean,
    isHintTarget: Boolean,
    isHintEvidence: Boolean,
    isHintConflict: Boolean,
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
    val regionColors =
        listOf(
            colors.primaryContainer,
            colors.secondaryContainer,
            colors.tertiaryContainer,
            colors.surfaceVariant,
            colors.primaryContainer.copy(alpha = 0.64f),
            colors.secondaryContainer.copy(alpha = 0.64f),
            colors.tertiaryContainer.copy(alpha = 0.64f),
            colors.surfaceVariant.copy(alpha = 0.64f),
        )
    val background =
        when {
            isConflict -> colors.errorContainer
            isHintConflict -> colors.errorContainer.copy(alpha = 0.72f)
            isHintTarget -> colors.tertiaryContainer
            isHintEvidence -> colors.secondaryContainer
            else -> regionColors[regionColorIndex % regionColors.size]
        }
    val targetSuffix = if (isHintTarget) stringResource(R.string.hint_target_suffix) else ""
    val evidenceSuffix = if (isHintEvidence) stringResource(R.string.hint_evidence_suffix) else ""
    val hintConflictSuffix = if (isHintConflict) stringResource(R.string.hint_conflict_suffix) else ""
    val hintSuffix = targetSuffix + evidenceSuffix + hintConflictSuffix
    val description =
        stringResource(
            R.string.crowns_cell_description,
            position.row + 1,
            position.column + 1,
            regionNumber,
            cell.accessibilityLabel(),
            if (isConflict) stringResource(R.string.conflict_suffix) else "",
            hintSuffix,
        )
    val boundaryColor = colors.outline
    val internalColor = colors.outlineVariant
    val strongWidth = 3.dp
    val thinWidth = 1.dp

    Box(
        modifier =
            modifier
                .background(background)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics { contentDescription = description }
                .drawWithContent {
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
                },
        contentAlignment = Alignment.Center,
    ) {
        when (cell) {
            CrownsPlayerCell.EMPTY -> Unit
            CrownsPlayerCell.MARKED ->
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isConflict) colors.onErrorContainer else colors.onSurfaceVariant,
                )
            CrownsPlayerCell.CROWN ->
                Icon(
                    painter = painterResource(R.drawable.ic_crown),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.55f),
                    tint = if (isConflict) colors.onErrorContainer else colors.onSurface,
                )
        }
    }
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
