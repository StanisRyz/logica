package com.stanisryz.logica.ui.daily

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.daily_archive_description
import com.stanisryz.logica.shared.ui.generated.resources.daily_archive_locked
import com.stanisryz.logica.shared.ui.generated.resources.daily_archive_missing_gems
import com.stanisryz.logica.shared.ui.generated.resources.daily_archive_open_day
import com.stanisryz.logica.shared.ui.generated.resources.daily_archive_unlock_body
import com.stanisryz.logica.shared.ui.generated.resources.daily_archive_unlock_title
import com.stanisryz.logica.shared.ui.generated.resources.daily_archive_watch_ad
import com.stanisryz.logica.shared.ui.generated.resources.daily_challenge
import com.stanisryz.logica.shared.ui.generated.resources.daily_progress_description
import com.stanisryz.logica.shared.ui.generated.resources.daily_progress_short
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_loading
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_unavailable
import com.stanisryz.logica.ui.components.GemPriceButton
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One past Daily day in the archive list. [dateLabel] arrives already formatted by the host. */
data class DailyArchiveDay(
    val epochDay: Long,
    val dateLabel: String,
    val completedCount: Int,
    val totalCount: Int,
    val unlocked: Boolean,
)

/**
 * The Daily archive: the past days, newest first, each with its progress and a lock until it is
 * opened. Tapping a day opens it; what opening costs is the day screen's business.
 */
@Composable
fun DailyArchiveList(
    days: List<DailyArchiveDay>,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = LogicaSpacing.screenHorizontal, vertical = LogicaSpacing.screenVertical),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
    ) {
        item(key = "description") {
            Text(
                pluralStringResource(Res.plurals.daily_archive_description, days.size, days.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = LogicaSpacing.text),
            )
        }
        items(days, key = { it.epochDay }) { day -> DailyArchiveRow(day, onOpenDay) }
    }
}

@Composable
private fun DailyArchiveRow(
    day: DailyArchiveDay,
    onOpenDay: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val progress = stringResource(Res.string.daily_progress_short, day.completedCount, day.totalCount)
    val progressDescription =
        pluralStringResource(Res.plurals.daily_progress_description, day.totalCount, day.completedCount, day.totalCount)
    val lockedLabel = stringResource(Res.string.daily_archive_locked)
    val openLabel = stringResource(Res.string.daily_archive_open_day)
    val description = listOfNotNull(day.dateLabel, progressDescription, lockedLabel.takeUnless { day.unlocked }).joinToString(", ")
    val complete = day.totalCount > 0 && day.completedCount >= day.totalCount
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (complete) colors.tertiaryContainer else colors.surfaceContainerLow,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClickLabel = openLabel) { onOpenDay(day.epochDay) }
                .clearAndSetSemantics {
                    contentDescription = description
                    onClick(openLabel) {
                        onOpenDay(day.epochDay)
                        true
                    }
                },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = LogicaSpacing.cardContent, vertical = LogicaSpacing.item),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            Icon(
                imageVector =
                    when {
                        !day.unlocked -> Icons.Rounded.Lock
                        complete -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.PlayCircle
                    },
                contentDescription = null,
                tint = if (day.unlocked) colors.primary else colors.onSurfaceVariant,
            )
            Text(day.dateLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(progress, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

/**
 * One archive day: the same entry cards as today's Daily under its date, playable once the day is
 * open. A locked day offers its price as the button itself and one rewarded ad that says it is an
 * ad; a balance below the price says what is missing. A day the player already started on its own
 * date has no price at all: the host opens it with the first tap.
 */
@Composable
fun DailyArchiveDayContent(
    dateLabel: String,
    entries: List<DailyHubEntry>,
    unlocked: Boolean,
    unlockPrice: Int,
    gems: Int,
    adState: DailyRewardedAdState,
    gameplayAllowed: Boolean,
    onUnlockWithGems: () -> Unit,
    onWatchAd: () -> Unit,
    onStart: (PuzzleType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val playable = unlocked || unlockPrice == 0
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LogicaSpacing.screenHorizontal, vertical = LogicaSpacing.screenVertical),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.section),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(LogicaSpacing.cardPadding).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            ) {
                val completed = entries.count { it.state == DailyHubEntryState.COMPLETED }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                        Text(stringResource(Res.string.daily_challenge), style = MaterialTheme.typography.titleLarge)
                        Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Text(
                        stringResource(Res.string.daily_progress_short, completed, entries.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                }
                val rowState = rememberLazyListState()
                LazyRow(
                    state = rowState,
                    modifier = Modifier.mouseDragScroll(rowState),
                    horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = LogicaSpacing.text),
                ) {
                    items(entries, key = { it.puzzleType }) { entry ->
                        DailyEntryCard(entry, gameplayAllowed && playable, onStart)
                    }
                }
            }
        }
        if (!playable) {
            DailyArchiveUnlockCard(unlockPrice, gems, adState, onUnlockWithGems, onWatchAd)
        }
    }
}

@Composable
private fun DailyArchiveUnlockCard(
    price: Int,
    gems: Int,
    adState: DailyRewardedAdState,
    onUnlockWithGems: () -> Unit,
    onWatchAd: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.tertiaryContainer) {
        Column(Modifier.padding(LogicaSpacing.cardContent), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.onTertiaryContainer)
                Spacer(Modifier.width(LogicaSpacing.text))
                Text(
                    stringResource(Res.string.daily_archive_unlock_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onTertiaryContainer,
                )
            }
            Text(
                stringResource(Res.string.daily_archive_unlock_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onTertiaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                GemPriceButton(price = price, enabled = gems >= price, onClick = onUnlockWithGems)
                OutlinedButton(onClick = onWatchAd, enabled = adState == DailyRewardedAdState.READY) {
                    when (adState) {
                        DailyRewardedAdState.LOADING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else -> Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            when (adState) {
                                DailyRewardedAdState.READY -> Res.string.daily_archive_watch_ad
                                DailyRewardedAdState.LOADING -> Res.string.second_chance_loading
                                DailyRewardedAdState.UNAVAILABLE -> Res.string.second_chance_unavailable
                            },
                        ),
                    )
                }
            }
            if (gems < price) {
                Text(
                    pluralStringResource(Res.plurals.daily_archive_missing_gems, price - gems, price - gems),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onTertiaryContainer,
                )
            }
        }
    }
}
