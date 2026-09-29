package com.stanisryz.logica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleStars
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.catalog_level
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_stars
import com.stanisryz.logica.shared.ui.generated.resources.levels_cleared
import com.stanisryz.logica.shared.ui.generated.resources.levels_current
import com.stanisryz.logica.shared.ui.generated.resources.levels_empty
import com.stanisryz.logica.shared.ui.generated.resources.levels_hint
import com.stanisryz.logica.shared.ui.generated.resources.levels_title
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One game's level map: per difficulty, the current level first and then every cleared level
 * newest first with its best stars. The current level plays on as usual; a cleared one is a
 * replay, which can only raise its stars — hosts pay no gems for it and never move progression.
 * Nothing is stored for the map: levels are cleared in order, so everything below the current
 * level ([currentLevels], per difficulty) is cleared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelMapSheet(
    currentLevels: Map<Difficulty, Int>,
    starsOf: (Difficulty, Int) -> Int,
    onPlayCurrent: (Difficulty) -> Unit,
    onReplay: (Difficulty, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var difficulty by remember {
        mutableStateOf(Difficulty.entries.lastOrNull { (currentLevels[it] ?: 1) > 1 } ?: Difficulty.EASY)
    }
    val current = (currentLevels[difficulty] ?: 1).coerceAtLeast(1)
    val cleared = current - 1
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = LogicaSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            Text(stringResource(Res.string.levels_title), style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text * 2),
            ) {
                Difficulty.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == difficulty,
                        onClick = { difficulty = entry },
                        label = {
                            Text(
                                "${stringResource(entry.labelResource())} · ${((currentLevels[entry] ?: 1) - 1).coerceAtLeast(0)}",
                            )
                        },
                    )
                }
            }
            Text(
                stringResource(if (cleared > 0) Res.string.levels_hint else Res.string.levels_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            if (cleared > 0) {
                Text(
                    stringResource(Res.string.levels_cleared, cleared.toString()),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(TILE_MIN_SIZE),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            ) {
                items(current, key = { index -> "$difficulty-${current - index}" }) { index ->
                    val level = current - index
                    if (level == current) {
                        CurrentTile(level, onClick = { onPlayCurrent(difficulty) })
                    } else {
                        ClearedTile(level, starsOf(difficulty, level), onClick = { onReplay(difficulty, level) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentTile(
    level: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(Res.string.levels_current)
    Column(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.primary)
                .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
        Text(level.toString(), style = MaterialTheme.typography.titleMedium, color = colors.onPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onPrimary, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun ClearedTile(
    level: Int,
    stars: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val levelLabel = stringResource(Res.string.catalog_level, level)
    val starsLabel = stringResource(Res.string.difficulty_stars, stars.toLong())
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                // The sheet sits on the low container, so the tiles take the lightest surface.
                .background(colors.surfaceContainerLowest)
                .clickable(role = Role.Button, onClick = onClick)
                .clearAndSetSemantics { contentDescription = "$levelLabel. $starsLabel" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(level.toString(), style = MaterialTheme.typography.titleMedium)
            LevelStars(stars, 12.dp)
        }
    }
}

/** A level's best stars as three small marks; a level with none on record shows empty marks. */
@Composable
internal fun LevelStars(
    stars: Int,
    size: Dp,
) {
    val gold = LocalLogicaPalette.current.star
    val empty = MaterialTheme.colorScheme.outlineVariant
    Row {
        repeat(PuzzleStars.MAX_STARS) { index ->
            Icon(Icons.Rounded.Star, contentDescription = null, tint = if (index < stars) gold else empty, modifier = Modifier.size(size))
        }
    }
}

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val TILE_MIN_SIZE = 72.dp
