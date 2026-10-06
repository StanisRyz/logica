package com.stanisryz.logica.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.achievement_expert_25
import com.stanisryz.logica.ui.components.ArtworkFilterQuality
import com.stanisryz.logica.ui.components.GameIcon
import com.stanisryz.logica.ui.components.GameIconImage
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.web.generated.resources.web_refresh
import com.stanisryz.logica.web.generated.resources.web_weekly_burn
import com.stanisryz.logica.web.generated.resources.web_weekly_days_left
import com.stanisryz.logica.web.generated.resources.web_weekly_guest
import com.stanisryz.logica.web.generated.resources.web_weekly_hours_left
import com.stanisryz.logica.web.generated.resources.web_weekly_no_place
import com.stanisryz.logica.web.generated.resources.web_weekly_place
import com.stanisryz.logica.web.generated.resources.web_weekly_prize_body
import com.stanisryz.logica.web.generated.resources.web_weekly_prize_claim
import com.stanisryz.logica.web.generated.resources.web_weekly_prize_later
import com.stanisryz.logica.web.generated.resources.web_weekly_prize_place
import com.stanisryz.logica.web.generated.resources.web_weekly_prize_title
import com.stanisryz.logica.web.generated.resources.web_weekly_prizes
import com.stanisryz.logica.web.generated.resources.web_weekly_rules
import com.stanisryz.logica.web.generated.resources.web_weekly_stars
import com.stanisryz.logica.web.generated.resources.web_weekly_table
import com.stanisryz.logica.web.generated.resources.web_weekly_time_left
import com.stanisryz.logica.web.generated.resources.web_weekly_title
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

/**
 * The weekly tournament page, opened from the Profile or the Game hub: this week's own stars and
 * place, the time left, the table of the running week (older cycles dropped, scores shown as stars),
 * the prizes, and the rules. Reading it is the only time the table is fetched.
 */
@Composable
internal fun WebWeeklyTournamentPage(
    leaderboard: WebLeaderboardController,
    tournament: WebWeeklyTournamentController,
    token: WebPlayerContextToken?,
    stars: Int,
    claimedWeeks: Set<Int>,
    guest: Boolean,
) {
    val now = webClock.now()
    val week = WebWeeklyTournament.week(now)
    val board = WebWeeklyTournament.board(week)
    LaunchedEffect(token, week) {
        if (token != null) {
            leaderboard.load(board, top = TABLE_SIZE)
            tournament.refreshOwnPlace(token)
            tournament.checkPrize(token, claimedWeeks)
        }
    }
    val place by tournament.ownPlace.collectAsState()
    val state by leaderboard.state(board).collectAsState()
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LogicaSpacing.screenHorizontal, vertical = LogicaSpacing.screenVertical),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.section),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = colors.primaryContainer), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(LogicaSpacing.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            ) {
                Image(
                    bitmap = imageResource(Res.drawable.achievement_expert_25),
                    contentDescription = null,
                    filterQuality = ArtworkFilterQuality,
                    modifier = Modifier.size(72.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        pluralStringResource(WebRes.plurals.web_weekly_stars, stars, stars),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onPrimaryContainer,
                    )
                    Text(
                        place?.let { stringResource(WebRes.string.web_weekly_place, it) }
                            ?: stringResource(WebRes.string.web_weekly_no_place),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onPrimaryContainer,
                    )
                    Text(
                        stringResource(WebRes.string.web_weekly_time_left, timeLeftLabel(WebWeeklyTournament.millisUntilWeekEnd(now))),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer,
                    )
                }
            }
        }
        if (guest) {
            Text(stringResource(WebRes.string.web_weekly_guest), style = MaterialTheme.typography.bodyMedium, color = colors.error)
        }
        Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(WebRes.string.web_weekly_table),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { leaderboard.load(board, top = TABLE_SIZE) }, enabled = state != WebLeaderboardState.Loading) {
                    Text(stringResource(WebRes.string.web_refresh))
                }
            }
            // Entries of older cycles sort under this week's and are dropped; scores show as stars.
            LeaderboardTable(
                (state as? WebLeaderboardState.Ready)?.let { WebLeaderboardState.Ready(WebWeeklyTournament.rowsOfWeek(it.snapshot, week)) }
                    ?: state,
            )
        }
        WeeklyPrizeTable()
        Text(stringResource(WebRes.string.web_weekly_rules), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(stringResource(WebRes.string.web_weekly_burn), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun WeeklyPrizeTable() {
    val colors = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(LogicaSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(WebRes.string.web_weekly_prizes), style = MaterialTheme.typography.titleSmall)
            prizeRanges().forEach { (label, gems) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(WebRes.string.web_weekly_prize_place, label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    GameIconImage(GameIcon.GEM, size = 18.dp)
                    Text(" $gems", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

/** The prize places grouped by equal prizes: "1", "2", "3", "4–10", "11–20". */
private fun prizeRanges(): List<Pair<String, Int>> {
    val prizes = EconomyPolicy.WEEKLY_PRIZES
    val ranges = mutableListOf<Pair<String, Int>>()
    var start = 0
    while (start < prizes.size) {
        var end = start
        while (end + 1 < prizes.size && prizes[end + 1] == prizes[start]) end++
        ranges += (if (start == end) "${start + 1}" else "${start + 1}–${end + 1}") to prizes[start]
        start = end + 1
    }
    return ranges
}

@Composable
private fun timeLeftLabel(millis: Long): String {
    val hoursTotal = (millis / HOUR_MS).toInt()
    val days = hoursTotal / 24
    val hours = hoursTotal % 24
    val hoursLabel = stringResource(WebRes.string.web_weekly_hours_left, hours)
    return if (days > 0) "${pluralStringResource(WebRes.plurals.web_weekly_days_left, days, days)} $hoursLabel" else hoursLabel
}

/** The Game hub's compact tournament line under the Daily section: stars and place, opening the page. */
@Composable
internal fun WebWeeklyHubRow(
    stars: Int,
    place: Int?,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.large, color = colors.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = LogicaSpacing.cardContent, vertical = LogicaSpacing.item),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = colors.onSecondaryContainer)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(WebRes.string.web_weekly_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSecondaryContainer,
                )
                Text(
                    listOfNotNull(
                        pluralStringResource(WebRes.plurals.web_weekly_stars, stars, stars),
                        place?.let { stringResource(WebRes.string.web_weekly_place, it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSecondaryContainer,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSecondaryContainer)
        }
    }
}

/** Last week's result with its prize; the claim is the host's durable-first payment. */
@Composable
internal fun WebWeeklyPrizeDialog(
    prize: WebWeeklyPrize,
    onClaim: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = {
            Image(
                bitmap = imageResource(Res.drawable.achievement_expert_25),
                contentDescription = null,
                filterQuality = ArtworkFilterQuality,
                modifier = Modifier.size(72.dp),
            )
        },
        title = { Text(stringResource(WebRes.string.web_weekly_prize_title)) },
        text = { Text(pluralStringResource(WebRes.plurals.web_weekly_prize_body, prize.gems, prize.gems, prize.place)) },
        confirmButton = { Button(onClick = onClaim) { Text(stringResource(WebRes.string.web_weekly_prize_claim)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(WebRes.string.web_weekly_prize_later)) } },
    )
}

private const val TABLE_SIZE = 20
private const val HOUR_MS = 60L * 60L * 1_000L
