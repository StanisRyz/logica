package com.stanisryz.logica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.quest.DailyQuest
import com.stanisryz.logica.puzzle.core.quest.DailyQuestActivity
import com.stanisryz.logica.puzzle.core.quest.DailyQuestKind
import com.stanisryz.logica.puzzle.core.quest.DailyQuests
import com.stanisryz.logica.puzzle.core.quest.LoginGift
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.login_gift_day
import com.stanisryz.logica.shared.ui.generated.resources.login_gift_title
import com.stanisryz.logica.shared.ui.generated.resources.quest_different_games
import com.stanisryz.logica.shared.ui.generated.resources.quest_play_few
import com.stanisryz.logica.shared.ui.generated.resources.quest_play_many
import com.stanisryz.logica.shared.ui.generated.resources.quest_solve_2048
import com.stanisryz.logica.shared.ui.generated.resources.quest_solve_balance
import com.stanisryz.logica.shared.ui.generated.resources.quest_solve_crowns
import com.stanisryz.logica.shared.ui.generated.resources.quest_solve_daily
import com.stanisryz.logica.shared.ui.generated.resources.quest_solve_few
import com.stanisryz.logica.shared.ui.generated.resources.quest_solve_hard
import com.stanisryz.logica.shared.ui.generated.resources.quest_solve_many
import com.stanisryz.logica.shared.ui.generated.resources.quest_solve_nonogram
import com.stanisryz.logica.shared.ui.generated.resources.quest_solve_sudoku
import com.stanisryz.logica.shared.ui.generated.resources.quest_solve_word
import com.stanisryz.logica.shared.ui.generated.resources.quests_title
import com.stanisryz.logica.shared.ui.generated.resources.rewards_claim
import com.stanisryz.logica.shared.ui.generated.resources.rewards_claimed
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.stringResource

/** One of the day's quests as the hub shows it. */
data class DailyQuestUiState(
    val quest: DailyQuest,
    val progress: Int,
    val claimed: Boolean,
) {
    val complete: Boolean
        get() = progress >= quest.target
}

/**
 * The day's rewards: the login gift at [giftStreakDay] of its cycle and the three quests. Hosts
 * build it with [dailyRewardsUiState] from what they keep durably; the card claims nothing itself.
 */
data class DailyRewardsUiState(
    val giftStreakDay: Int,
    val giftClaimed: Boolean,
    val quests: List<DailyQuestUiState>,
) {
    val giftGems: Int
        get() = LoginGift.gemsFor(giftStreakDay)

    /** Something is waiting to be claimed, so the hub can point at it. */
    val hasClaimable: Boolean
        get() = !giftClaimed || quests.any { it.complete && !it.claimed }
}

/** The shared rule that turns a host's durable facts for [epochDay] into the card's state. */
fun dailyRewardsUiState(
    epochDay: Long,
    activity: DailyQuestActivity,
    claimedQuests: Set<Int>,
    lastGiftEpochDay: Long?,
    lastGiftStreakDay: Int,
): DailyRewardsUiState =
    DailyRewardsUiState(
        giftStreakDay = LoginGift.streakDay(lastGiftEpochDay, lastGiftStreakDay, epochDay),
        giftClaimed = lastGiftEpochDay == epochDay,
        quests =
            DailyQuests.forDay(epochDay).map { quest ->
                DailyQuestUiState(quest, quest.progress(activity), claimed = quest.index in claimedQuests)
            },
    )

@Composable
fun DailyRewardsCard(
    state: DailyRewardsUiState,
    onClaimGift: () -> Unit,
    onClaimQuest: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow, contentColor = colors.onSurface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            GiftRow(state, onClaimGift)
            HorizontalDivider(color = colors.outlineVariant)
            Text(
                text = stringResource(Res.string.quests_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
            )
            state.quests.forEach { quest -> QuestRow(quest, onClaim = { onClaimQuest(quest.quest.index) }) }
        }
    }
}

@Composable
private fun GiftRow(
    state: DailyRewardsUiState,
    onClaim: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
        Box(
            modifier = Modifier.size(ICON_BOX).clip(CircleShape).background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = colors.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.login_gift_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(Res.string.login_gift_day, state.giftStreakDay, LoginGift.CYCLE_DAYS),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            CycleDots(claimedThrough = if (state.giftClaimed) state.giftStreakDay else state.giftStreakDay - 1)
        }
        ClaimAction(gems = state.giftGems, complete = true, claimed = state.giftClaimed, onClaim = onClaim)
    }
}

/** The seven days of the gift cycle as short bars, filled through the last claimed day. */
@Composable
private fun CycleDots(claimedThrough: Int) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clearAndSetSemantics {}) {
        repeat(LoginGift.CYCLE_DAYS) { index ->
            Box(
                Modifier
                    .width(if (index == LoginGift.CYCLE_DAYS - 1) 22.dp else 14.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (index < claimedThrough) colors.primary else colors.surfaceContainerHighest),
            )
        }
    }
}

@Composable
private fun QuestRow(
    state: DailyQuestUiState,
    onClaim: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val title = questTitle(state.quest)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { state.progress.toFloat() / state.quest.target },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = if (state.complete) LocalLogicaPalette.current.success else colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    "${state.progress}/${state.quest.target}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        ClaimAction(gems = state.quest.gems, complete = state.complete, claimed = state.claimed, onClaim = onClaim)
    }
}

/** Claimed shows a check; claimable is the gem button; otherwise the reward waits as a quiet label. */
@Composable
private fun ClaimAction(
    gems: Int,
    complete: Boolean,
    claimed: Boolean,
    onClaim: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when {
        claimed -> {
            val label = stringResource(Res.string.rewards_claimed)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = label },
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LocalLogicaPalette.current.success)
                Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
        complete ->
            Button(onClick = onClaim, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text(stringResource(Res.string.rewards_claim))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Diamond, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("+$gems")
            }
        else ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Diamond, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Text("+$gems", style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
    }
}

@Composable
private fun questTitle(quest: DailyQuest): String =
    when (quest.kind) {
        DailyQuestKind.PLAY ->
            stringResource(if (quest.target.isFewForm()) Res.string.quest_play_few else Res.string.quest_play_many, quest.target)
        DailyQuestKind.SOLVE ->
            stringResource(if (quest.target.isFewForm()) Res.string.quest_solve_few else Res.string.quest_solve_many, quest.target)
        DailyQuestKind.SOLVE_GAME ->
            stringResource(
                when (quest.puzzleType) {
                    PuzzleType.CROWNS -> Res.string.quest_solve_crowns
                    PuzzleType.WORD -> Res.string.quest_solve_word
                    PuzzleType.SUDOKU -> Res.string.quest_solve_sudoku
                    PuzzleType.GAME_2048 -> Res.string.quest_solve_2048
                    PuzzleType.NONOGRAM -> Res.string.quest_solve_nonogram
                    else -> Res.string.quest_solve_balance
                },
            )
        DailyQuestKind.SOLVE_HARD -> stringResource(Res.string.quest_solve_hard)
        DailyQuestKind.SOLVE_DAILY -> stringResource(Res.string.quest_solve_daily)
        DailyQuestKind.DIFFERENT_GAMES -> stringResource(Res.string.quest_different_games, quest.target)
    }

/**
 * Russian counts ending in 2–4 (but not 12–14) take the "few" form; English and Turkish give both
 * forms the same text, so this picks correctly in every language the app ships.
 */
private fun Int.isFewForm(): Boolean = this % 10 in 2..4 && this % 100 !in 12..14

private val CARD_PADDING = 16.dp
private val ICON_BOX = 44.dp
