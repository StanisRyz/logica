package com.stanisryz.logica.ui.nonogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleStars
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPuzzle
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.catalog_level
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.gallery_close
import com.stanisryz.logica.shared.ui.generated.resources.gallery_count
import com.stanisryz.logica.shared.ui.generated.resources.gallery_daily
import com.stanisryz.logica.shared.ui.generated.resources.gallery_empty
import com.stanisryz.logica.shared.ui.generated.resources.gallery_replay
import com.stanisryz.logica.shared.ui.generated.resources.gallery_title
import com.stanisryz.logica.ui.components.StateArtwork
import com.stanisryz.logica.ui.components.StateArtworkImage
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Every picture the player has uncovered, per difficulty and newest first. Nothing is stored for
 * it: levels are cleared in order, so [clearedLevels] (the current level minus one) names every
 * solved level, and the host rebuilds each picture from its frozen level through [loadPicture].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonogramGallerySheet(
    clearedLevels: Map<Difficulty, Int>,
    loadPicture: suspend (Difficulty, Int) -> NonogramPuzzle?,
    starsOf: (Difficulty, Int) -> Int,
    onDismiss: () -> Unit,
    onReplay: ((Difficulty, Int) -> Unit)? = null,
    dailyPictures: List<DailyGalleryPicture> = emptyList(),
) {
    // The Daily's real pictures sit under their own chip, newest first.
    var showDaily by remember { mutableStateOf(clearedLevels.values.all { it <= 0 } && dailyPictures.isNotEmpty()) }
    var openedDaily by remember { mutableStateOf<DailyGalleryPicture?>(null) }
    var difficulty by remember {
        mutableStateOf(Difficulty.entries.firstOrNull { (clearedLevels[it] ?: 0) > 0 } ?: Difficulty.EASY)
    }
    var opened by remember { mutableStateOf<Int?>(null) }
    // Pictures already rebuilt stay for the life of the sheet, so scrolling back costs nothing.
    val pictures = remember { mutableStateMapOf<Pair<Difficulty, Int>, NonogramPuzzle>() }
    val load: suspend (Difficulty, Int) -> NonogramPuzzle? = { forDifficulty, level ->
        pictures[forDifficulty to level] ?: loadPicture(forDifficulty, level)?.also { pictures[forDifficulty to level] = it }
    }
    val count = clearedLevels[difficulty] ?: 0
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = LogicaSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            Text(stringResource(Res.string.gallery_title), style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text * 2),
            ) {
                Difficulty.entries.forEach { entry ->
                    FilterChip(
                        selected = !showDaily && entry == difficulty,
                        onClick = {
                            showDaily = false
                            difficulty = entry
                        },
                        label = { Text("${stringResource(entry.labelResource())} · ${clearedLevels[entry] ?: 0}") },
                    )
                }
                if (dailyPictures.isNotEmpty()) {
                    FilterChip(
                        selected = showDaily,
                        onClick = { showDaily = true },
                        label = { Text("${stringResource(Res.string.gallery_daily)} · ${dailyPictures.size}") },
                    )
                }
            }
            if (showDaily) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(THUMBNAIL_MIN_SIZE),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                    verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                ) {
                    items(dailyPictures.size, key = { index -> "daily-${dailyPictures[index].dateLabel}" }) { index ->
                        val picture = dailyPictures[index]
                        Card(
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { openedDaily = picture },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Column(Modifier.padding(LogicaSpacing.text * 2), horizontalAlignment = Alignment.CenterHorizontally) {
                                NonogramPicture(picture.puzzle, Modifier.fillMaxWidth())
                                Text(
                                    picture.dateLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = LogicaSpacing.text),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            } else if (count == 0) {
                Column(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StateArtworkImage(StateArtwork.EMPTY_GALLERY)
                    Text(
                        stringResource(Res.string.gallery_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    stringResource(Res.string.gallery_count, count.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(THUMBNAIL_MIN_SIZE),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                    verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                ) {
                    items(count, key = { index -> "$difficulty-${count - index}" }) { index ->
                        val level = count - index
                        GalleryItem(
                            difficulty = difficulty,
                            level = level,
                            stars = starsOf(difficulty, level),
                            load = load,
                            onClick = { opened = level },
                        )
                    }
                }
            }
        }
    }
    openedDaily?.let { picture ->
        Dialog(onDismissRequest = { openedDaily = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(
                    modifier = Modifier.padding(LogicaSpacing.cardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                ) {
                    Text(picture.dateLabel, style = MaterialTheme.typography.titleLarge)
                    NonogramPicture(picture.puzzle, Modifier.size(LARGE_PICTURE_SIZE))
                    OutlinedButton(onClick = { openedDaily = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.gallery_close))
                    }
                }
            }
        }
    }
    opened?.let { level ->
        PictureDialog(
            difficulty,
            level,
            starsOf(difficulty, level),
            load,
            onDismiss = { opened = null },
            onReplay = onReplay?.let { replay -> { replay(difficulty, level) } },
        )
    }
}

@Composable
private fun GalleryItem(
    difficulty: Difficulty,
    level: Int,
    stars: Int,
    load: suspend (Difficulty, Int) -> NonogramPuzzle?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(LogicaSpacing.text * 2), horizontalAlignment = Alignment.CenterHorizontally) {
            LoadedPicture(difficulty, level, load, Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.padding(top = LogicaSpacing.text),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(level.toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 4.dp))
                StarMarks(stars, 12.dp)
            }
        }
    }
}

@Composable
private fun PictureDialog(
    difficulty: Difficulty,
    level: Int,
    stars: Int,
    load: suspend (Difficulty, Int) -> NonogramPuzzle?,
    onDismiss: () -> Unit,
    onReplay: (() -> Unit)?,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(
                modifier = Modifier.padding(LogicaSpacing.cardPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            ) {
                Text(stringResource(Res.string.catalog_level, level), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(difficulty.labelResource()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LoadedPicture(difficulty, level, load, Modifier.size(LARGE_PICTURE_SIZE))
                StarMarks(stars, 22.dp)
                // Playing a picture again is a replay: it can raise its stars and pays no gems.
                onReplay?.let { replay ->
                    Button(onClick = replay, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.gallery_replay))
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.gallery_close))
                }
            }
        }
    }
}

@Composable
private fun LoadedPicture(
    difficulty: Difficulty,
    level: Int,
    load: suspend (Difficulty, Int) -> NonogramPuzzle?,
    modifier: Modifier,
) {
    val puzzle by produceState<NonogramPuzzle?>(null, difficulty, level) { value = load(difficulty, level) }
    NonogramPicture(puzzle, modifier)
}

/** One solved Daily picture: the host's formatted date and the rebuilt Generator V2 picture. */
data class DailyGalleryPicture(
    val dateLabel: String,
    val puzzle: NonogramPuzzle,
)

/** The finished picture alone: filled cells on a light ground, without clues or grid. */
@Composable
fun NonogramPicture(
    puzzle: NonogramPuzzle?,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.primary
    val ground = MaterialTheme.colorScheme.surfaceContainerLowest
    Canvas(
        modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(ground),
    ) {
        val picture = puzzle ?: return@Canvas
        val cell = size.minDimension / picture.size
        // A hairline gap between cells keeps the picture readable as pixels.
        val gap = (cell * PIXEL_GAP_FRACTION).coerceAtMost(1.5f)
        for (row in 0 until picture.size) {
            for (column in 0 until picture.size) {
                if (picture.isFilled(row, column)) {
                    drawRect(
                        color = ink,
                        topLeft = Offset(column * cell + gap / 2, row * cell + gap / 2),
                        size = Size(cell - gap, cell - gap),
                    )
                }
            }
        }
    }
}

@Composable
private fun StarMarks(
    stars: Int,
    size: androidx.compose.ui.unit.Dp,
) {
    // A level with no stars on record (solved before stars existed) shows none rather than zero.
    if (stars <= 0) return
    val gold = LocalLogicaPalette.current.star
    val empty = MaterialTheme.colorScheme.outline
    Row {
        repeat(PuzzleStars.MAX_STARS) { index ->
            // A missing star is outlined, not only paler.
            Icon(
                if (index < stars) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = null,
                tint = if (index < stars) gold else empty,
                modifier = Modifier.size(size),
            )
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

private val THUMBNAIL_MIN_SIZE = 84.dp
private val LARGE_PICTURE_SIZE = 240.dp
private const val PIXEL_GAP_FRACTION = 0.06f
