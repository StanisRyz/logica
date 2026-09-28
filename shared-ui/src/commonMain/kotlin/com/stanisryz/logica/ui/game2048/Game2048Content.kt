package com.stanisryz.logica.ui.game2048

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_goal_reached
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_level_cleared
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_score
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_target
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_target_reached
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_undo
import com.stanisryz.logica.shared.ui.generated.resources.tool_caption_undo
import com.stanisryz.logica.ui.components.BoardTitle
import com.stanisryz.logica.ui.components.CenteredBoardLayout
import com.stanisryz.logica.ui.components.LocalRoomyGameplayControls
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import com.stanisryz.logica.ui.components.isRoomyPortrait
import com.stanisryz.logica.ui.components.isWideGameplayLayout
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Pure Android/Web 2048 presentation. Application lifecycle and terminal policy stay in the host. */
@Composable
fun Game2048Content(
    game: Game2048State,
    difficulty: Difficulty,
    levelNumber: Int?,
    levelCleared: Boolean,
    motionRevision: Long?,
    motionTrace: Game2048MoveTrace?,
    gameplayEnabled: Boolean,
    canUndo: Boolean,
    onMove: (Game2048Direction) -> Unit,
    onUndo: () -> Unit,
    onMotionFinished: (Long) -> Unit,
    contextBadgeLabel: String? = null,
    modifier: Modifier = Modifier,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
) {
    require(game.puzzleId.difficulty == difficulty) { "2048 difficulty must match the game identity." }
    val difficultyLabel = stringResource(difficulty.labelResource())
    val header: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HEADER_SPACING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoardTitle(difficultyLabel, levelNumber, contextBadgeLabel)
            Game2048ScoreLine(game, levelCleared, levelNumber)
            hostStatusContent()
        }
    }
    val board: @Composable () -> Unit = {
        Game2048Board(
            game = game,
            motionRevision = motionRevision,
            motionTrace = motionTrace,
            onMove = onMove,
            onMotionFinished = onMotionFinished,
            inputEnabled = gameplayEnabled,
            modifier = Modifier.fillMaxSize(),
        )
    }
    val controls: @Composable () -> Unit = {
        PuzzleToolBar(
            tools =
                listOf(
                    PuzzleTool(
                        label = stringResource(Res.string.game_2048_undo),
                        stateDescription = null,
                        selected = null,
                        enabled = canUndo,
                        onClick = onUndo,
                        // An icon, not the "↶" glyph: the Web font has no glyph for it.
                        symbol = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
                        caption = stringResource(Res.string.tool_caption_undo),
                    ),
                ),
            enabled = gameplayEnabled,
        )
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
        val wideLayout = isWideGameplayLayout(maxWidth, maxHeight)
        val verticalPadding = if (compact) COMPACT_VERTICAL_PADDING else LogicaSpacing.screenVertical
        val sectionSpacing = if (compact) COMPACT_SECTION_SPACING else LogicaSpacing.item
        val gameplayHorizontal = LogicaSpacing.screenHorizontal
        val panelWidth = minOf(WIDE_PANEL_MAX_WIDTH, maxWidth * WIDE_PANEL_WIDTH_FRACTION)

        if (wideLayout) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = gameplayHorizontal, vertical = verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(WIDE_SECTION_SPACING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) { board() }
                Column(
                    modifier = Modifier.width(panelWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    Spacer(Modifier.weight(1f))
                    controls()
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
                            .padding(horizontal = gameplayHorizontal, vertical = verticalPadding),
                    header = header,
                    board = board,
                    controls = controls,
                )
            }
        }
    }
}

/**
 * Score on the left, the target on the right, and a bar of how close the score is to it. Reaching
 * the target turns the bar and the target to the success colour, and a cleared Catalog level says so
 * in place of the target label, so nothing new appears and the board never moves.
 */
@Composable
private fun Game2048ScoreLine(
    game: Game2048State,
    levelCleared: Boolean,
    levelNumber: Int?,
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalLogicaPalette.current
    val targetScore = game.puzzleId.rules.targetScore
    val reached = game.goalReached || levelCleared
    val targetLabel =
        when {
            levelCleared && levelNumber != null -> stringResource(Res.string.game_2048_level_cleared, levelNumber)
            reached -> stringResource(Res.string.game_2048_goal_reached)
            else -> stringResource(Res.string.game_2048_target)
        }
    val progress by animateFloatAsState(
        targetValue = targetScore?.let { (game.score.toFloat() / it).coerceIn(0f, 1f) } ?: if (reached) 1f else 0f,
        animationSpec = tween(SCORE_PROGRESS_MILLIS),
    )
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.game_2048_score),
                    modifier = Modifier.height(TARGET_LABEL_HEIGHT),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = formatGame2048Number(game.score),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Row(
                    modifier = Modifier.height(TARGET_LABEL_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (reached) {
                        Icon(
                            imageVector = Icons.Filled.TaskAlt,
                            contentDescription = null,
                            tint = palette.success,
                            modifier = Modifier.size(REACHED_ICON_SIZE),
                        )
                    }
                    Text(
                        text = targetLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (reached) palette.success else colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = game.targetMetricValue(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (reached) palette.success else colors.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(SCORE_PROGRESS_HEIGHT).clearAndSetSemantics { },
            color = if (reached) palette.success else colors.primary,
            trackColor = colors.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun Game2048State.targetMetricValue(): String {
    val targetScore = puzzleId.rules.targetScore ?: return requireNotNull(puzzleId.rules.targetTile).toString()
    val target = formatGame2048Number(targetScore)
    return if (goalReached) stringResource(Res.string.game_2048_target_reached, target) else target
}

/** Grouped thousands keep the shared target, live score, and host terminal summaries consistent. */
fun formatGame2048Number(value: Long): String =
    value
        .toString()
        .reversed()
        .chunked(GROUP_SIZE)
        .joinToString(GROUP_SEPARATOR)
        .reversed()

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val COMPACT_HEIGHT_THRESHOLD = 650.dp
private val COMPACT_VERTICAL_PADDING = 8.dp
private val COMPACT_SECTION_SPACING = 6.dp
private val HEADER_SPACING = 10.dp
private val REACHED_ICON_SIZE = 16.dp
private val TARGET_LABEL_HEIGHT = 18.dp
private val SCORE_PROGRESS_HEIGHT = 4.dp
private const val SCORE_PROGRESS_MILLIS = 250
private val WIDE_SECTION_SPACING = 8.dp
private val WIDE_PANEL_MAX_WIDTH = 232.dp
private const val WIDE_PANEL_WIDTH_FRACTION = 0.38f
private const val GROUP_SIZE = 3
private const val GROUP_SEPARATOR = "\u00A0"
