package com.stanisryz.logica.ui.daily

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.best_daily_streak
import com.stanisryz.logica.shared.ui.generated.resources.current_daily_streak
import com.stanisryz.logica.shared.ui.generated.resources.daily_available
import com.stanisryz.logica.shared.ui.generated.resources.daily_challenge
import com.stanisryz.logica.shared.ui.generated.resources.daily_completed
import com.stanisryz.logica.shared.ui.generated.resources.daily_entry_failed
import com.stanisryz.logica.shared.ui.generated.resources.daily_entry_solved
import com.stanisryz.logica.shared.ui.generated.resources.daily_load_error
import com.stanisryz.logica.shared.ui.generated.resources.daily_loading
import com.stanisryz.logica.shared.ui.generated.resources.daily_progress_description
import com.stanisryz.logica.shared.ui.generated.resources.daily_progress_short
import com.stanisryz.logica.shared.ui.generated.resources.daily_result_solved
import com.stanisryz.logica.shared.ui.generated.resources.daily_result_solved_description
import com.stanisryz.logica.shared.ui.generated.resources.daily_result_word_attempts
import com.stanisryz.logica.shared.ui.generated.resources.daily_result_word_failed
import com.stanisryz.logica.shared.ui.generated.resources.daily_result_word_failed_description
import com.stanisryz.logica.shared.ui.generated.resources.daily_result_word_solved_description
import com.stanisryz.logica.shared.ui.generated.resources.daily_streak_hint
import com.stanisryz.logica.shared.ui.generated.resources.daily_streak_secured
import com.stanisryz.logica.shared.ui.generated.resources.game_catalog_play_label
import com.stanisryz.logica.shared.ui.generated.resources.retry
import com.stanisryz.logica.shared.ui.generated.resources.share_daily_result
import com.stanisryz.logica.shared.ui.generated.resources.share_daily_result_description
import com.stanisryz.logica.shared.ui.generated.resources.total_hints_used
import com.stanisryz.logica.ui.components.ArtworkFilterQuality
import com.stanisryz.logica.ui.components.catalogArtworkResource
import com.stanisryz.logica.ui.components.catalogTitleResource
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * The Daily block at the top of the Game Hub, shared by Android and Web. It stays compact so the
 * regular Catalog remains the main hub content, and it owns no lifecycle: hosts map their own
 * durable state onto [DailyHubUiState] and receive plain start events back.
 */
@Composable
fun DailyHubSection(
    uiState: DailyHubUiState,
    gameplayAllowed: Boolean,
    onStart: (PuzzleType) -> Unit,
    modifier: Modifier = Modifier,
    onRetryLoad: () -> Unit = {},
) {
    when (uiState) {
        DailyHubUiState.Loading -> DailyLoadingCard(modifier)
        is DailyHubUiState.Error -> DailyErrorCard(uiState.detailLabel, onRetryLoad, modifier)
        is DailyHubUiState.Content ->
            DailyContent(
                content = uiState,
                gameplayAllowed = gameplayAllowed,
                onStart = onStart,
                modifier = modifier,
            )
    }
}

@Composable
private fun DailyLoadingCard(modifier: Modifier) {
    DailyCard(modifier) {
        CircularProgressIndicator()
        Text(
            text = stringResource(Res.string.daily_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DailyErrorCard(
    detailLabel: String?,
    onRetryLoad: () -> Unit,
    modifier: Modifier,
) {
    DailyCard(modifier) {
        Text(
            text = detailLabel ?: stringResource(Res.string.daily_load_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetryLoad) { Text(stringResource(Res.string.retry)) }
    }
}

@Composable
private fun DailyContent(
    content: DailyHubUiState.Content,
    gameplayAllowed: Boolean,
    onStart: (PuzzleType) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
        val progressDescription =
            stringResource(Res.string.daily_progress_description, content.completedCount, content.totalCount)
        val progress by
            animateFloatAsState(
                targetValue = completedFraction(content.completedCount, content.totalCount),
                animationSpec = tween(DAILY_PROGRESS_ANIMATION_MILLIS),
                label = "daily-progress",
            )
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = DAILY_CARD_BORDER_ALPHA)),
        ) {
            Column(
                modifier = Modifier.padding(LogicaSpacing.cardPadding).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
                    ) {
                        Text(
                            text = stringResource(Res.string.daily_challenge),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = content.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text =
                            stringResource(
                                Res.string.daily_progress_short,
                                content.completedCount,
                                content.totalCount,
                            ),
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(
                                    horizontal = DAILY_PROGRESS_HORIZONTAL_PADDING,
                                    vertical = DAILY_PROGRESS_VERTICAL_PADDING,
                                ).clearAndSetSemantics { contentDescription = progressDescription },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = DAILY_PROGRESS_TRACK_ALPHA),
                )
                DailyStreakChip(content.streak)
                val rowState = rememberLazyListState()
                LazyRow(
                    state = rowState,
                    modifier = Modifier.mouseDragScroll(rowState),
                    horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                    contentPadding = PaddingValues(horizontal = DAILY_ROW_EDGE_PADDING, vertical = LogicaSpacing.text),
                ) {
                    items(content.entries, key = { it.puzzleType }) { entry ->
                        DailyEntryCard(entry, gameplayAllowed, onStart)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = content.completion != null,
            enter = fadeIn(tween(DAILY_REVEAL_MILLIS)) + expandVertically(tween(DAILY_REVEAL_MILLIS)),
            exit = fadeOut(tween(DAILY_REVEAL_MILLIS)) + shrinkVertically(tween(DAILY_REVEAL_MILLIS)),
        ) {
            content.completion?.let { completion -> DailyCompletionCard(completion) }
        }
    }
}

/**
 * Lets a mouse drag the row sideways like a finger does. The row's own scrolling answers touch
 * only, so without this a desktop player could not reach the entries past the visible edge.
 * Only mouse gestures are intercepted, and a real drag swallows the release so it opens nothing.
 */
private fun Modifier.mouseDragScroll(state: LazyListState): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            var dragging = false
            var lastX = down.position.x
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (dragging) change.consume()
                    break
                }
                if (!dragging && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) dragging = true
                if (dragging) {
                    state.dispatchRawDelta(lastX - change.position.x)
                    change.consume()
                }
                lastX = change.position.x
            }
        }
    }

private fun completedFraction(
    completed: Int,
    total: Int,
): Float {
    if (total <= 0) return 0f
    return completed.coerceIn(0, total).toFloat() / total
}

@Composable
private fun DailyCard(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(LogicaSpacing.cardPadding).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = { content() },
        )
    }
}

/** The V5 streak chip stays visibly separate from full completion: secured today, not finished. */
@Composable
private fun DailyStreakChip(streak: DailyHubStreak) {
    val label =
        if (streak.qualifiedToday) {
            stringResource(Res.string.daily_streak_secured)
        } else {
            stringResource(Res.string.daily_streak_hint)
        }
    val containerColor by
        animateColorAsState(
            targetValue =
                if (streak.qualifiedToday) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            animationSpec = tween(DAILY_STATE_ANIMATION_MILLIS),
            label = "daily-streak-background",
        )
    val contentColor by
        animateColorAsState(
            targetValue =
                if (streak.qualifiedToday) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            animationSpec = tween(DAILY_STATE_ANIMATION_MILLIS),
            label = "daily-streak-content",
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(containerColor)
                .padding(horizontal = LogicaSpacing.cardContent, vertical = LogicaSpacing.text)
                .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DAILY_CHIP_GAP),
    ) {
        Icon(
            imageVector = if (streak.qualifiedToday) Icons.Rounded.LocalFireDepartment else Icons.Rounded.Bolt,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(DAILY_CHIP_ICON_SIZE),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DailyEntryCard(
    entry: DailyHubEntry,
    gameplayAllowed: Boolean,
    onStart: (PuzzleType) -> Unit,
) {
    // On Web the first read of a title and artwork the catalog below also shows can stay empty for
    // good (the card was blank until the tab was reopened); reading them again a moment later comes
    // from the resource cache.
    var attempt by remember { mutableIntStateOf(0) }
    key(attempt) {
        val title = stringResource(entry.puzzleType.catalogTitleResource())
        val stateLabel = entry.state.chipLabel()
        if ((title.isEmpty() || stateLabel.isEmpty()) && attempt < DAILY_RESOURCE_RETRIES) {
            LaunchedEffect(Unit) {
                delay(DAILY_RESOURCE_RETRY_MILLIS)
                attempt++
            }
        }
        val actionable = gameplayAllowed && entry.state != DailyHubEntryState.COMPLETED
        val completed = entry.state == DailyHubEntryState.COMPLETED
        val cardColor by
            animateColorAsState(
                targetValue =
                    when {
                        completed -> MaterialTheme.colorScheme.tertiaryContainer
                        !gameplayAllowed -> MaterialTheme.colorScheme.surfaceContainerLow
                        else -> MaterialTheme.colorScheme.surfaceBright
                    },
                animationSpec = tween(DAILY_STATE_ANIMATION_MILLIS),
                label = "daily-entry-card-color",
            )
        Card(
            modifier =
                Modifier
                    .width(DAILY_CARD_WIDTH)
                    .heightIn(min = DAILY_CARD_MIN_HEIGHT)
                    .animateContentSize()
                    // The press and hover highlight follows the card's rounded shape.
                    .clip(CardDefaults.shape)
                    .then(
                        if (actionable) {
                            Modifier.clickable(
                                onClickLabel = stringResource(Res.string.game_catalog_play_label, title),
                                onClick = { onStart(entry.puzzleType) },
                            )
                        } else {
                            Modifier
                        },
                    ),
            colors =
                CardDefaults.cardColors(
                    containerColor = cardColor,
                    contentColor =
                        if (!gameplayAllowed && !completed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = DAILY_CARD_BORDER_ALPHA)),
            elevation = CardDefaults.cardElevation(defaultElevation = DAILY_ENTRY_ELEVATION),
        ) {
            Column(
                modifier = Modifier.padding(LogicaSpacing.cardContent).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = imageResource(entry.puzzleType.catalogArtworkResource()),
                    contentDescription = null,
                    filterQuality = ArtworkFilterQuality,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(DAILY_ARTWORK_HEIGHT)
                            .clip(MaterialTheme.shapes.medium),
                )
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                DailyEntryStateChip(entry.state, stateLabel)
            }
        }
    }
}

@Composable
private fun DailyEntryStateChip(
    state: DailyHubEntryState,
    label: String,
) {
    val completed = state == DailyHubEntryState.COMPLETED
    val containerColor by
        animateColorAsState(
            targetValue =
                if (completed) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            animationSpec = tween(DAILY_STATE_ANIMATION_MILLIS),
            label = "daily-entry-chip-background",
        )
    val contentColor by
        animateColorAsState(
            targetValue =
                if (completed) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            animationSpec = tween(DAILY_STATE_ANIMATION_MILLIS),
            label = "daily-entry-chip-content",
        )
    Row(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.small)
                .background(containerColor)
                .padding(horizontal = LogicaSpacing.cardContent, vertical = DAILY_CHIP_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DAILY_CHIP_GAP),
    ) {
        Icon(
            imageVector = state.chipIcon(),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(DAILY_CHIP_ICON_SIZE),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

private fun DailyHubEntryState.chipIcon(): ImageVector =
    when (this) {
        DailyHubEntryState.AVAILABLE -> Icons.Rounded.PlayCircleOutline
        DailyHubEntryState.RETRY -> Icons.Rounded.HighlightOff
        DailyHubEntryState.COMPLETED -> Icons.Rounded.CheckCircle
    }

@Composable
private fun DailyHubEntryState.chipLabel(): String =
    stringResource(
        when (this) {
            DailyHubEntryState.AVAILABLE -> Res.string.daily_available
            DailyHubEntryState.RETRY -> Res.string.daily_entry_failed
            DailyHubEntryState.COMPLETED -> Res.string.daily_entry_solved
        },
    )

/** The completed Daily card: results where provided, the streaks, and the optional host share. */
@Composable
private fun DailyCompletionCard(completion: DailyHubCompletion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(LogicaSpacing.cardPadding).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DAILY_CHIP_GAP),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text = stringResource(Res.string.daily_completed), style = MaterialTheme.typography.titleMedium)
            }
            completion.resultRows?.forEach { row -> DailyResultRow(row) }
            DailyLabeledValue(stringResource(Res.string.current_daily_streak), completion.currentStreak.toString())
            DailyLabeledValue(stringResource(Res.string.best_daily_streak), completion.bestStreak.toString())
            completion.hintsUsed?.let { hintsUsed ->
                DailyLabeledValue(stringResource(Res.string.total_hints_used), hintsUsed.toString())
            }
            completion.onShare?.let { onShare ->
                val shareDescription = stringResource(Res.string.share_daily_result_description)
                Button(
                    onClick = onShare,
                    modifier = Modifier.semantics { contentDescription = shareDescription },
                ) {
                    Text(stringResource(Res.string.share_daily_result))
                }
            }
        }
    }
}

@Composable
private fun currentStreakResource(): StringResource = CURRENT_STREAK_RESOURCE

@Composable
private fun bestStreakResource(): StringResource = BEST_STREAK_RESOURCE

@Composable
private fun hintsResource(): StringResource = HINTS_RESOURCE

private val CURRENT_STREAK_RESOURCE = Res.string.current_daily_streak
private val BEST_STREAK_RESOURCE = Res.string.best_daily_streak
private val HINTS_RESOURCE = Res.string.total_hints_used

@Composable
private fun DailyResultRow(row: DailyHubResultRow) {
    val label = stringResource(row.puzzleType.catalogTitleResource())
    val valueText: String
    val description: String
    if (row.puzzleType == PuzzleType.WORD) {
        if (row.solved) {
            val attemptsUsed = row.wordAttemptsUsed ?: 0
            valueText = stringResource(Res.string.daily_result_word_attempts, attemptsUsed, WordRules.MAXIMUM_ATTEMPTS)
            description = stringResource(Res.string.daily_result_word_solved_description, label, attemptsUsed)
        } else {
            valueText = stringResource(Res.string.daily_result_word_failed)
            description = stringResource(Res.string.daily_result_word_failed_description, label)
        }
    } else {
        valueText = stringResource(Res.string.daily_result_solved)
        description = stringResource(Res.string.daily_result_solved_description, label)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DailyLabeledValue(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val DAILY_CARD_WIDTH = 160.dp
private const val DAILY_RESOURCE_RETRIES = 5
private const val DAILY_RESOURCE_RETRY_MILLIS = 600L

/** A minimum, never a fixed height: the card grows with a larger font scale instead of clipping. */
private val DAILY_CARD_MIN_HEIGHT = 184.dp
private val DAILY_ARTWORK_HEIGHT = 78.dp
private val DAILY_CHIP_ICON_SIZE = 16.dp
private val DAILY_CHIP_GAP = 4.dp
private val DAILY_CHIP_VERTICAL_PADDING = 2.dp
private val DAILY_CARD_BORDER_ALPHA = 0.52f
private val DAILY_PROGRESS_TRACK_ALPHA = 0.18f
private val DAILY_PROGRESS_HORIZONTAL_PADDING = 14.dp
private val DAILY_PROGRESS_VERTICAL_PADDING = 7.dp
private val DAILY_ROW_EDGE_PADDING = 2.dp
private val DAILY_ENTRY_ELEVATION = 0.dp
private const val DAILY_PROGRESS_ANIMATION_MILLIS = 420
private const val DAILY_STATE_ANIMATION_MILLIS = 220
private const val DAILY_REVEAL_MILLIS = 240
