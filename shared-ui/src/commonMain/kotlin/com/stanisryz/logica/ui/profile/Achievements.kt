package com.stanisryz.logica.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.achievement_all_games
import com.stanisryz.logica.shared.ui.generated.resources.achievement_all_games_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_balance
import com.stanisryz.logica.shared.ui.generated.resources.achievement_balance_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_crowns
import com.stanisryz.logica.shared.ui.generated.resources.achievement_crowns_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_daily_1
import com.stanisryz.logica.shared.ui.generated.resources.achievement_daily_1_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_daily_30
import com.stanisryz.logica.shared.ui.generated.resources.achievement_daily_30_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_expert_1
import com.stanisryz.logica.shared.ui.generated.resources.achievement_expert_1_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_expert_25
import com.stanisryz.logica.shared.ui.generated.resources.achievement_expert_25_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_first_solve
import com.stanisryz.logica.shared.ui.generated.resources.achievement_first_solve_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_game2048
import com.stanisryz.logica.shared.ui.generated.resources.achievement_game2048_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_nonogram
import com.stanisryz.logica.shared.ui.generated.resources.achievement_nonogram_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_perfect_25
import com.stanisryz.logica.shared.ui.generated.resources.achievement_perfect_25_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_solver_1000
import com.stanisryz.logica.shared.ui.generated.resources.achievement_solver_1000_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_solver_250
import com.stanisryz.logica.shared.ui.generated.resources.achievement_solver_250_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_solver_50
import com.stanisryz.logica.shared.ui.generated.resources.achievement_solver_50_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_stars_100
import com.stanisryz.logica.shared.ui.generated.resources.achievement_stars_100_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_stars_500
import com.stanisryz.logica.shared.ui.generated.resources.achievement_stars_500_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_streak_30
import com.stanisryz.logica.shared.ui.generated.resources.achievement_streak_30_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_streak_7
import com.stanisryz.logica.shared.ui.generated.resources.achievement_streak_7_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_sudoku
import com.stanisryz.logica.shared.ui.generated.resources.achievement_sudoku_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_unlocked
import com.stanisryz.logica.shared.ui.generated.resources.achievement_word
import com.stanisryz.logica.shared.ui.generated.resources.achievement_word_body
import com.stanisryz.logica.shared.ui.generated.resources.achievement_word_first_try
import com.stanisryz.logica.shared.ui.generated.resources.achievement_word_first_try_body
import com.stanisryz.logica.shared.ui.generated.resources.achievements_count
import com.stanisryz.logica.shared.ui.generated.resources.achievements_show_all
import com.stanisryz.logica.shared.ui.generated.resources.achievements_show_less
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The achievements, each a goal over durable statistics the hosts already keep: solved counts,
 * stars, Daily completions, and streaks. Nothing is stored for them; an achievement is unlocked
 * whenever its progress reaches its target, so it is the same on every device the stats reach.
 * The stable [id] is only what hosts remember to announce a new one once.
 */
enum class Achievement(
    val id: String,
    internal val icon: ImageVector,
    internal val title: StringResource,
    internal val body: StringResource,
    val target: Long,
    private val progressOf: (ProfileStatistics) -> Long,
) {
    FIRST_SOLVE("first_solve", Icons.Rounded.TaskAlt, Res.string.achievement_first_solve, Res.string.achievement_first_solve_body, 1, {
        it.totalSolved
    }),
    ALL_GAMES("all_games", Icons.Rounded.ViewModule, Res.string.achievement_all_games, Res.string.achievement_all_games_body, 6, { stats ->
        listOf(
            stats.balance.totalSolved,
            stats.crowns.totalSolved,
            stats.sudoku.solved,
            stats.word.solved,
            stats.game2048.solved,
            stats.nonogram.solved,
        ).count { it > 0L }
            .toLong()
    }),
    SOLVER_50("solver_50", Icons.Rounded.Psychology, Res.string.achievement_solver_50, Res.string.achievement_solver_50_body, 50, {
        it.totalSolved
    }),
    SOLVER_250("solver_250", Icons.Rounded.MilitaryTech, Res.string.achievement_solver_250, Res.string.achievement_solver_250_body, 250, {
        it.totalSolved
    }),
    SOLVER_1000(
        "solver_1000",
        Icons.Rounded.EmojiEvents,
        Res.string.achievement_solver_1000,
        Res.string.achievement_solver_1000_body,
        1000,
        { it.totalSolved },
    ),
    BALANCE("balance_50", Icons.Rounded.Balance, Res.string.achievement_balance, Res.string.achievement_balance_body, 50, {
        it.balance.totalSolved
    }),
    CROWNS("crowns_50", Icons.Rounded.WorkspacePremium, Res.string.achievement_crowns, Res.string.achievement_crowns_body, 50, {
        it.crowns.totalSolved
    }),
    SUDOKU("sudoku_50", Icons.Rounded.Grid4x4, Res.string.achievement_sudoku, Res.string.achievement_sudoku_body, 50, { it.sudoku.solved }),
    WORD("word_50", Icons.Rounded.SortByAlpha, Res.string.achievement_word, Res.string.achievement_word_body, 50, { it.word.solved }),
    GAME_2048("game2048_25", Icons.Rounded.Extension, Res.string.achievement_game2048, Res.string.achievement_game2048_body, 25, {
        it.game2048.solved
    }),
    NONOGRAM("nonogram_25", Icons.Rounded.Brush, Res.string.achievement_nonogram, Res.string.achievement_nonogram_body, 25, {
        it.nonogram.solved
    }),
    WORD_FIRST_TRY(
        "word_first_try",
        Icons.Rounded.AutoAwesome,
        Res.string.achievement_word_first_try,
        Res.string.achievement_word_first_try_body,
        1,
        { it.word.solvedAttemptDistribution[1] },
    ),
    EXPERT_1("expert_1", Icons.Rounded.Verified, Res.string.achievement_expert_1, Res.string.achievement_expert_1_body, 1, ::expertSolved),
    EXPERT_25(
        "expert_25",
        Icons.Rounded.MilitaryTech,
        Res.string.achievement_expert_25,
        Res.string.achievement_expert_25_body,
        25,
        ::expertSolved,
    ),
    STARS_100("stars_100", Icons.Rounded.Star, Res.string.achievement_stars_100, Res.string.achievement_stars_100_body, 100, {
        it.stars?.total
            ?: 0L
    }),
    STARS_500("stars_500", Icons.Rounded.AutoAwesome, Res.string.achievement_stars_500, Res.string.achievement_stars_500_body, 500, {
        it.stars?.total
            ?: 0L
    }),
    PERFECT_25("perfect_25", Icons.Rounded.Verified, Res.string.achievement_perfect_25, Res.string.achievement_perfect_25_body, 25, {
        it.stars?.perfectLevels ?: 0L
    }),
    DAILY_1("daily_1", Icons.Rounded.EventAvailable, Res.string.achievement_daily_1, Res.string.achievement_daily_1_body, 1, {
        it.dailyMetrics?.completedCount ?: 0L
    }),
    DAILY_30("daily_30", Icons.Rounded.CalendarMonth, Res.string.achievement_daily_30, Res.string.achievement_daily_30_body, 30, {
        it.dailyMetrics?.completedCount ?: 0L
    }),
    STREAK_7("streak_7", Icons.Rounded.Bolt, Res.string.achievement_streak_7, Res.string.achievement_streak_7_body, 7, {
        it.dailyMetrics?.bestStreak ?: 0L
    }),
    STREAK_30("streak_30", Icons.Rounded.LocalFireDepartment, Res.string.achievement_streak_30, Res.string.achievement_streak_30_body, 30, {
        it.dailyMetrics?.bestStreak ?: 0L
    }),
    ;

    /** Progress toward [target], capped at it. */
    fun progress(statistics: ProfileStatistics): Long = progressOf(statistics).coerceIn(0L, target)

    fun isUnlocked(statistics: ProfileStatistics): Boolean = progress(statistics) >= target
}

private fun expertSolved(statistics: ProfileStatistics): Long =
    listOf(
        statistics.balance.solvedByDifficulty,
        statistics.crowns.solvedByDifficulty,
        statistics.sudoku.solvedByDifficulty,
        statistics.game2048.solvedByDifficulty,
        statistics.nonogram.solvedByDifficulty,
    ).sumOf { it[Difficulty.EXPERT] }

/** Ids of every achievement [statistics] has reached, for hosts that announce new ones. */
fun ProfileStatistics.unlockedAchievementIds(): Set<String> =
    Achievement.entries
        .filter {
            it.isUnlocked(this)
        }.mapTo(linkedSetOf()) { it.id }

/**
 * The Profile's achievements: the count, then the unlocked ones and those closest to done; the
 * rest open on request so the Profile stays short.
 */
@Composable
internal fun AchievementsCard(statistics: ProfileStatistics) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val ordered =
        Achievement.entries.sortedWith(
            compareByDescending<Achievement> { it.isUnlocked(statistics) }
                .thenByDescending { it.progress(statistics).toDouble() / it.target },
        )
    val unlocked = ordered.count { it.isUnlocked(statistics) }
    val shown = if (expanded) ordered else ordered.take(COLLAPSED_COUNT)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
        Text(
            stringResource(Res.string.achievements_count, unlocked, Achievement.entries.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        shown.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                row.forEach { achievement -> AchievementTile(achievement, statistics, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(if (expanded) Res.string.achievements_show_less else Res.string.achievements_show_all))
        }
    }
}

@Composable
private fun AchievementTile(
    achievement: Achievement,
    statistics: ProfileStatistics,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalLogicaPalette.current
    val unlocked = achievement.isUnlocked(statistics)
    val progress = achievement.progress(statistics)
    val title = stringResource(achievement.title)
    val body = stringResource(achievement.body)
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$title. $body. $progress/${achievement.target}" },
        shape = MaterialTheme.shapes.medium,
        color = if (unlocked) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (unlocked) colors.onPrimaryContainer else colors.onSurface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (unlocked) palette.star else colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        achievement.icon,
                        contentDescription = null,
                        tint = if (unlocked) ACHIEVEMENT_ICON_INK else colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = if (unlocked) colors.onPrimaryContainer else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!unlocked && achievement.target > 1L) {
                LinearProgressIndicator(
                    progress = { progress.toFloat() / achievement.target },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    drawStopIndicator = {},
                )
                Text("$progress / ${achievement.target}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * The queue of achievements a host has just seen reached. An open result card claims them at once
 * and shows them inside itself; whatever no card claims appears as a short banner.
 */
@Stable
class AchievementAnnouncer {
    internal val pending = mutableStateListOf<Achievement>()

    fun announce(achievements: List<Achievement>) {
        achievements.filterNot(pending::contains).let(pending::addAll)
    }

    internal fun claimAll(): List<Achievement> = pending.toList().also { pending.clear() }
}

val LocalAchievementAnnouncer = staticCompositionLocalOf<AchievementAnnouncer?> { null }

/** Hosts place this once over their screens; it shows unclaimed achievements one at a time. */
@Composable
fun AchievementAnnouncementHost(
    announcer: AchievementAnnouncer,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf<Achievement?>(null) }
    var visible by remember { mutableStateOf(false) }
    val waiting = announcer.pending.size
    LaunchedEffect(waiting, current) {
        if (current != null || waiting == 0) return@LaunchedEffect
        // A result card that is open claims them first; the banner waits a moment for it.
        delay(CLAIM_WINDOW_MILLIS)
        val next = announcer.pending.firstOrNull() ?: return@LaunchedEffect
        announcer.pending.remove(next)
        current = next
        visible = true
        delay(BANNER_MILLIS)
        visible = false
        delay(BANNER_EXIT_MILLIS)
        current = null
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val shown = current ?: return@AnimatedVisibility
        val label = stringResource(Res.string.achievement_unlocked)
        val title = stringResource(shown.title)
        Surface(
            modifier =
                Modifier
                    .padding(horizontal = LogicaSpacing.screenHorizontal, vertical = 8.dp)
                    .widthIn(max = BANNER_MAX_WIDTH)
                    .clickable { visible = false }
                    .semantics { liveRegion = LiveRegionMode.Polite },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AchievementMedal(shown, 36.dp)
                Column {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Achievements the open result card claimed, shown as one quiet line each inside the card. */
@Composable
internal fun ResultCardAchievements() {
    val announcer = LocalAchievementAnnouncer.current ?: return
    val claimed = remember { mutableStateListOf<Achievement>() }
    val waiting = announcer.pending.size
    LaunchedEffect(waiting) { if (waiting > 0) claimed += announcer.claimAll() }
    if (claimed.isEmpty()) return
    val label = stringResource(Res.string.achievement_unlocked)
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        claimed.forEach { achievement ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AchievementMedal(achievement, 28.dp)
                Column {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(achievement.title), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun AchievementMedal(
    achievement: Achievement,
    size: Dp,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(LocalLogicaPalette.current.star),
        contentAlignment = Alignment.Center,
    ) {
        Icon(achievement.icon, contentDescription = null, tint = ACHIEVEMENT_ICON_INK, modifier = Modifier.size(size * 0.55f))
    }
}

private const val COLLAPSED_COUNT = 6
private const val BANNER_MILLIS = 3_200L
private const val CLAIM_WINDOW_MILLIS = 700L
private const val BANNER_EXIT_MILLIS = 400L
private val BANNER_MAX_WIDTH = 480.dp
private val ACHIEVEMENT_ICON_INK =
    androidx.compose.ui.graphics
        .Color(0xFF3A2A02)
