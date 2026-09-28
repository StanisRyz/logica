package com.stanisryz.logica.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_stars
import com.stanisryz.logica.shared.ui.generated.resources.profile_achievements
import com.stanisryz.logica.shared.ui.generated.resources.profile_best_streak
import com.stanisryz.logica.shared.ui.generated.resources.profile_calendar_completed
import com.stanisryz.logica.shared.ui.generated.resources.profile_calendar_day
import com.stanisryz.logica.shared.ui.generated.resources.profile_calendar_none
import com.stanisryz.logica.shared.ui.generated.resources.profile_calendar_partial
import com.stanisryz.logica.shared.ui.generated.resources.profile_calendar_weekdays
import com.stanisryz.logica.shared.ui.generated.resources.profile_daily_short
import com.stanisryz.logica.shared.ui.generated.resources.profile_empty_body
import com.stanisryz.logica.shared.ui.generated.resources.profile_empty_title
import com.stanisryz.logica.shared.ui.generated.resources.profile_games
import com.stanisryz.logica.shared.ui.generated.resources.profile_gems
import com.stanisryz.logica.shared.ui.generated.resources.profile_hints_short
import com.stanisryz.logica.shared.ui.generated.resources.profile_lives
import com.stanisryz.logica.shared.ui.generated.resources.profile_load_error
import com.stanisryz.logica.shared.ui.generated.resources.profile_not_played
import com.stanisryz.logica.shared.ui.generated.resources.profile_recent_days
import com.stanisryz.logica.shared.ui.generated.resources.profile_solved_count
import com.stanisryz.logica.shared.ui.generated.resources.profile_solved_short
import com.stanisryz.logica.shared.ui.generated.resources.profile_stars
import com.stanisryz.logica.shared.ui.generated.resources.profile_streak
import com.stanisryz.logica.shared.ui.generated.resources.profile_to_games
import com.stanisryz.logica.shared.ui.generated.resources.retry
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_failed_count
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_played
import com.stanisryz.logica.shared.ui.generated.resources.total_hints_used
import com.stanisryz.logica.shared.ui.generated.resources.word_attempt_bar_description
import com.stanisryz.logica.shared.ui.generated.resources.word_attempt_distribution
import com.stanisryz.logica.shared.ui.generated.resources.word_percent_value
import com.stanisryz.logica.shared.ui.generated.resources.word_win_rate
import com.stanisryz.logica.ui.components.catalogTitleResource
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Shared scrolling Profile presentation used by both platform hosts. */
@Composable
fun ProfileContent(
    uiState: ProfileUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGames: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    AnimatedContent(
        targetState = uiState,
        contentKey = ProfileUiState::presentationKey,
        transitionSpec = { fadeIn(tween(SCREEN_MILLIS)) togetherWith fadeOut(tween(SHORT_MILLIS)) },
        label = "profileState",
    ) { state ->
        Box(
            Modifier.semantics {
                if (state.presentationKey() != uiState.presentationKey()) hideFromAccessibility()
            },
        ) {
            when (state) {
                ProfileUiState.Loading -> LoadingState(modifier)
                ProfileUiState.Error -> ErrorState(onRetry, modifier)
                ProfileUiState.Empty -> EmptyState(modifier, onOpenGames)
                is ProfileUiState.Ready -> ReadyProfileContent(state.statistics, modifier, footer)
            }
        }
    }
}

private fun ProfileUiState.presentationKey(): String =
    when (this) {
        ProfileUiState.Loading -> "loading"
        ProfileUiState.Error -> "error"
        ProfileUiState.Empty -> "empty"
        is ProfileUiState.Ready -> "content"
    }

@Composable
private fun ReadyProfileContent(
    statistics: ProfileStatistics,
    modifier: Modifier,
    footer: (@Composable () -> Unit)?,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = LogicaSpacing.screenHorizontal,
                    vertical = LogicaSpacing.screenVertical,
                ),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.section),
    ) {
        SummaryCard(statistics)
        val calendar = statistics.dailyMetrics?.calendar
        if (calendar != null) {
            ProfileSection(stringResource(Res.string.profile_recent_days)) { DailyCalendarCard(calendar) }
        } else {
            statistics.dailyMetrics?.recentDays?.takeIf { it.isNotEmpty() }?.let { days ->
                ProfileSection(stringResource(Res.string.profile_recent_days)) { RecentDaysRow(days) }
            }
        }
        ProfileSection(stringResource(Res.string.profile_achievements)) { AchievementsCard(statistics) }
        ProfileSection(stringResource(Res.string.profile_games)) {
            ProfileCard(verticalSpacing = 0.dp) {
                val games = profileGames(statistics)
                games.forEachIndexed { index, game ->
                    GameRow(game)
                    if (index < games.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA)),
                        )
                    }
                }
            }
        }
        // A host section after the games, such as the Web leaderboard.
        footer?.invoke()
    }
}

/** The headline numbers in one card: solved, streak, Daily, then the wallet and hints in a quiet row. */
@Composable
private fun SummaryCard(statistics: ProfileStatistics) {
    val daily = statistics.dailyMetrics
    ProfileCard(verticalSpacing = LogicaSpacing.item) {
        Row(Modifier.fillMaxWidth()) {
            SummaryMetric(
                value = statistics.totalSolved.toString(),
                label = stringResource(Res.string.profile_solved_short),
                modifier = Modifier.weight(1f),
            )
            statistics.stars?.let { stars ->
                SummaryMetric(
                    value = stars.total.toString(),
                    label = stringResource(Res.string.profile_stars),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Star,
                    iconTint = LocalLogicaPalette.current.star,
                )
            }
            if (daily != null) {
                SummaryMetric(
                    value = daily.currentStreak.toString(),
                    label = stringResource(Res.string.profile_streak),
                    caption = stringResource(Res.string.profile_best_streak, daily.bestStreak),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Bolt,
                )
                SummaryMetric(
                    value = daily.completedCount.toString(),
                    label = stringResource(Res.string.profile_daily_short),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            statistics.economy?.let { economy ->
                InlineMetric(Icons.Rounded.Diamond, economy.gems.toString(), stringResource(Res.string.profile_gems))
                InlineMetric(
                    Icons.Rounded.Favorite,
                    "${economy.lives}/${economy.maximumLives}",
                    stringResource(Res.string.profile_lives),
                )
            }
            InlineMetric(Icons.Rounded.Lightbulb, statistics.totalHintsUsed.toString(), stringResource(Res.string.total_hints_used))
        }
        statistics.economy?.restoreLabel?.let { restore ->
            SupportingText(restore, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        caption?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InlineMetric(
    icon: ImageVector,
    value: String,
    description: String,
) {
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$description: $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The current month of Daily history: a full Daily fills its date, a partly solved one rings it,
 * and today carries an outline. Every state also has a text description, never colour alone.
 */
@Composable
private fun DailyCalendarCard(month: DailyCalendarMonth) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalLogicaPalette.current
    val weekdays = stringArrayResource(Res.array.profile_calendar_weekdays)
    val completedLabel = stringResource(Res.string.profile_calendar_completed)
    val partialLabel = stringResource(Res.string.profile_calendar_partial)
    val noneLabel = stringResource(Res.string.profile_calendar_none)
    ProfileCard(verticalSpacing = LogicaSpacing.text) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(month.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            CalendarLegendDot(filled = true)
            Text(completedLabel, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(LogicaSpacing.item))
            CalendarLegendDot(filled = false)
            Text(partialLabel, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            repeat(DAYS_IN_WEEK) { index ->
                Text(
                    weekdays.getOrNull(index).orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val cells = month.leadingBlankDays + month.daysInMonth
        val weeks = (cells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (weekday in 0 until DAYS_IN_WEEK) {
                    val day = week * DAYS_IN_WEEK + weekday - month.leadingBlankDays + 1
                    Box(Modifier.weight(1f).height(CALENDAR_CELL_HEIGHT), contentAlignment = Alignment.Center) {
                        if (day in 1..month.daysInMonth) {
                            val state = month.days[day] ?: DailyCalendarDayState.NONE
                            val isToday = month.today == day
                            val isFuture = month.today != null && day > month.today
                            val description =
                                stringResource(
                                    Res.string.profile_calendar_day,
                                    day,
                                    when (state) {
                                        DailyCalendarDayState.COMPLETED -> completedLabel
                                        DailyCalendarDayState.PARTIAL -> partialLabel
                                        DailyCalendarDayState.NONE -> noneLabel
                                    },
                                )
                            Box(
                                modifier =
                                    Modifier
                                        .size(CALENDAR_DAY_SIZE)
                                        .clip(CircleShape)
                                        .background(
                                            if (state == DailyCalendarDayState.COMPLETED) palette.successContainer else Color.Transparent,
                                        ).border(
                                            width = if (state == DailyCalendarDayState.PARTIAL) 2.dp else 0.dp,
                                            color = if (state == DailyCalendarDayState.PARTIAL) palette.success else Color.Transparent,
                                            shape = CircleShape,
                                        ).clearAndSetSemantics { contentDescription = description },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (state != DailyCalendarDayState.NONE || isToday) FontWeight.SemiBold else null,
                                    color =
                                        when {
                                            state == DailyCalendarDayState.COMPLETED -> palette.onSuccessContainer
                                            isToday -> colors.primary
                                            isFuture -> colors.onSurfaceVariant.copy(alpha = FUTURE_DAY_ALPHA)
                                            state == DailyCalendarDayState.NONE -> colors.onSurfaceVariant
                                            else -> colors.onSurface
                                        },
                                )
                                // Today is marked by a small dot under its number, apart from the result rings.
                                if (isToday) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 3.dp)
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (state ==
                                                    DailyCalendarDayState.COMPLETED
                                                ) {
                                                    palette.onSuccessContainer
                                                } else {
                                                    colors.primary
                                                },
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarLegendDot(filled: Boolean) {
    val palette = LocalLogicaPalette.current
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(if (filled) palette.successContainer else Color.Transparent)
            .border(if (filled) 0.dp else 2.dp, if (filled) Color.Transparent else palette.success, CircleShape),
    )
}

/** The last few Daily days side by side: the host date label over the solved count. */
@Composable
private fun RecentDaysRow(days: List<DailyRecentDay>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
        days.forEach { day ->
            val palette = LocalLogicaPalette.current
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (day.fullyCompleted) palette.successContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        ).padding(vertical = LogicaSpacing.item, horizontal = 4.dp)
                        .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${day.solvedCount}/${day.totalCount}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (day.fullyCompleted) palette.onSuccessContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    day.dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One game in the Profile list; [details] is null for a game that has never been finished. */
private class ProfileGame(
    val puzzleType: PuzzleType,
    val headline: Long,
    val played: Long?,
    val failed: Long?,
    val byDifficulty: ProfileDifficultyCounts,
    val hintsUsed: Long? = null,
    val word: WordProfileStatistics? = null,
    val stars: Long = 0L,
) {
    val isPlayed: Boolean get() = (played ?: headline) > 0L
}

private fun profileGames(statistics: ProfileStatistics): List<ProfileGame> {
    val stars = statistics.stars ?: ProfileStarSummary.EMPTY
    return listOf(
        ProfileGame(
            PuzzleType.BALANCE,
            statistics.balance.totalSolved,
            null,
            null,
            statistics.balance.solvedByDifficulty,
            stars = stars.forGame(PuzzleType.BALANCE),
        ),
        ProfileGame(
            PuzzleType.CROWNS,
            statistics.crowns.totalSolved,
            null,
            null,
            statistics.crowns.solvedByDifficulty,
            stars = stars.forGame(PuzzleType.CROWNS),
        ),
        ProfileGame(
            PuzzleType.SUDOKU,
            statistics.sudoku.solved,
            statistics.sudoku.played,
            statistics.sudoku.failed,
            statistics.sudoku.solvedByDifficulty,
            hintsUsed = statistics.sudoku.hintsUsed,
            stars = stars.forGame(PuzzleType.SUDOKU),
        ),
        ProfileGame(
            PuzzleType.WORD,
            statistics.word.solved,
            statistics.word.played,
            statistics.word.failed,
            ProfileDifficultyCounts(0, 0, 0, 0),
            word = statistics.word,
            stars = stars.forGame(PuzzleType.WORD),
        ),
        ProfileGame(
            PuzzleType.GAME_2048,
            statistics.game2048.solved,
            statistics.game2048.played,
            statistics.game2048.failed,
            statistics.game2048.solvedByDifficulty,
        ),
    )
}

/** A game line that opens to its details; a game never finished stays one quiet line. */
@Composable
private fun GameRow(game: ProfileGame) {
    var expanded by rememberSaveable(game.puzzleType) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .then(if (game.isPlayed) Modifier.clickable { expanded = !expanded } else Modifier)
                    .padding(vertical = GAME_ROW_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier
                    .size(ACCENT_DOT_SIZE)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(game.puzzleType.profileAccentColor()),
            )
            Spacer(Modifier.size(LogicaSpacing.action))
            Text(
                stringResource(game.puzzleType.catalogTitleResource()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (game.isPlayed) {
                Text(
                    stringResource(Res.string.profile_solved_count, game.headline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(Res.string.profile_not_played),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (expanded && game.isPlayed) GameDetails(game)
    }
}

@Composable
private fun GameDetails(game: ProfileGame) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = LogicaSpacing.item),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        if (game.played != null) {
            Row(Modifier.fillMaxWidth()) {
                DetailCell(stringResource(Res.string.sudoku_played), game.played.toString(), Modifier.weight(1f))
                DetailCell(stringResource(Res.string.sudoku_failed_count), (game.failed ?: 0L).toString(), Modifier.weight(1f))
                val rate = if (game.played == 0L) 0L else game.headline * 100L / game.played
                DetailCell(
                    stringResource(Res.string.word_win_rate),
                    stringResource(Res.string.word_percent_value, rate),
                    Modifier.weight(1f),
                )
                game.hintsUsed?.let {
                    DetailCell(stringResource(Res.string.profile_hints_short), it.toString(), Modifier.weight(1f))
                }
            }
        }
        if (game.word == null) {
            Row(Modifier.fillMaxWidth()) {
                Difficulty.entries.forEach { difficulty ->
                    DetailCell(
                        stringResource(difficulty.profileLabelResource()),
                        game.byDifficulty[difficulty].toString(),
                        Modifier.weight(1f),
                    )
                }
            }
        } else if (game.word.solved > 0L) {
            SupportingText(stringResource(Res.string.word_attempt_distribution))
            AttemptDistributionBars(game.word.solvedAttemptDistribution)
        }
        if (game.stars > 0L) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = LocalLogicaPalette.current.star, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.difficulty_stars, game.stars), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DetailCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun ProfileCard(
    modifier: Modifier = Modifier,
    verticalSpacing: androidx.compose.ui.unit.Dp = LogicaSpacing.cardContent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(LogicaSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

@Composable
private fun AttemptDistributionBars(distribution: ProfileAttemptDistribution) {
    val maximum = distribution.counts.maxOrNull() ?: 0L
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text * 2)) {
        for (attempt in 1..WordRules.MAXIMUM_ATTEMPTS) {
            val count = distribution[attempt]
            val fraction by
                animateFloatAsState(
                    if (maximum == 0L) 0f else (count.toDouble() / maximum.toDouble()).toFloat(),
                    label = "attemptBar",
                )
            val description = stringResource(Res.string.word_attempt_bar_description, attempt, count)
            Row(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
            ) {
                Text(attempt.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(16.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(BAR_HEIGHT)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(24.dp).padding(start = LogicaSpacing.text),
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    CenteredState(modifier, verticalSpacing = LogicaSpacing.item) {
        Text(
            stringResource(Res.string.profile_load_error),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    onOpenGames: (() -> Unit)?,
) {
    CenteredState(modifier, verticalSpacing = LogicaSpacing.item) {
        Box(
            modifier =
                Modifier
                    .size(EMPTY_MARK_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(EMPTY_ICON_SIZE),
            )
        }
        Text(
            stringResource(Res.string.profile_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        SupportingText(stringResource(Res.string.profile_empty_body), textAlign = TextAlign.Center)
        onOpenGames?.let { Button(onClick = it) { Text(stringResource(Res.string.profile_to_games)) } }
    }
}

@Composable
private fun CenteredState(
    modifier: Modifier,
    verticalSpacing: androidx.compose.ui.unit.Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier.fillMaxSize().padding(LogicaSpacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SupportingText(
    text: String,
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
    )
}

private fun Difficulty.profileLabelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

@Composable
private fun PuzzleType.profileAccentColor(): Color =
    when (this) {
        PuzzleType.BALANCE, PuzzleType.SUDOKU -> MaterialTheme.colorScheme.primary
        PuzzleType.CROWNS, PuzzleType.GAME_2048 -> MaterialTheme.colorScheme.tertiary
        PuzzleType.WORD -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

private const val SCREEN_MILLIS = 220
private const val SHORT_MILLIS = 140
private val ACCENT_DOT_SIZE = 10.dp
private val BAR_HEIGHT = 12.dp
private val GAME_ROW_VERTICAL_PADDING = 14.dp
private val EMPTY_MARK_SIZE = 72.dp
private val EMPTY_ICON_SIZE = 36.dp
private const val DIVIDER_ALPHA = 0.6f

private const val DAYS_IN_WEEK = 7
private val CALENDAR_CELL_HEIGHT = 40.dp
private val CALENDAR_DAY_SIZE = 36.dp
private const val FUTURE_DAY_ALPHA = 0.45f
