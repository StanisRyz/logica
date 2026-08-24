package com.stanisryz.logica.ui.game2048

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_level_cleared_hint
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_score
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_target
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_target_reached
import com.stanisryz.logica.ui.components.GameHeaderBadges
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
    onMove: (Game2048Direction) -> Unit,
    onMotionFinished: (Long) -> Unit,
    contextBadgeLabel: String? = null,
    modifier: Modifier = Modifier,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
) {
    require(game.puzzleId.difficulty == difficulty) { "2048 difficulty must match the game identity." }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
        val verticalPadding = if (compact) COMPACT_VERTICAL_PADDING else LogicaSpacing.screenVertical
        val sectionSpacing = if (compact) COMPACT_SECTION_SPACING else LogicaSpacing.item
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = LogicaSpacing.screenHorizontal,
                        vertical = verticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameHeaderBadges(stringResource(difficulty.labelResource()), levelNumber, contextLabel = contextBadgeLabel)
            Game2048Metrics(game)
            if (levelCleared) Game2048ClearedStatus(levelNumber, compact)
            hostStatusContent()
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
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
        }
    }
}

@Composable
private fun Game2048Metrics(game: Game2048State) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        Game2048Metric(
            label = stringResource(Res.string.game_2048_target),
            value = game.targetMetricValue(),
            modifier = Modifier.weight(1f),
        )
        Game2048Metric(
            label = stringResource(Res.string.game_2048_score),
            value = formatGame2048Number(game.score),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Game2048Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(METRIC_PADDING),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Game2048ClearedStatus(
    levelNumber: Int?,
    compact: Boolean,
) {
    val palette = LocalLogicaPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
    ) {
        Row(
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(palette.successContainer)
                    .padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.TaskAlt,
                contentDescription = null,
                tint = palette.onSuccessContainer,
            )
            Text(
                text =
                    levelNumber?.let { stringResource(Res.string.game_2048_level_cleared, it) }
                        ?: stringResource(Res.string.game_2048_goal_reached),
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = palette.onSuccessContainer,
            )
        }
        Text(
            text = stringResource(Res.string.game_2048_level_cleared_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private val METRIC_PADDING = 12.dp
private const val GROUP_SIZE = 3
private const val GROUP_SEPARATOR = "\u00A0"




