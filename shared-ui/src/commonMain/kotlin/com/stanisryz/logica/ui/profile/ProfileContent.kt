package com.stanisryz.logica.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
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
import com.stanisryz.logica.shared.ui.generated.resources.profile_best_streak
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
import org.jetbrains.compose.resources.stringResource

/** Shared scrolling Profile presentation used by both platform hosts. */
@Composable
fun ProfileContent(
    uiState: ProfileUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGames: (() -> Unit)? = null,
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
                is ProfileUiState.Ready -> ReadyProfileContent(state.statistics, modifier)
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
        statistics.dailyMetrics?.recentDays?.takeIf { it.isNotEmpty() }?.let { days ->
            ProfileSection(stringResource(Res.string.profile_recent_days)) { RecentDaysRow(days) }
        }
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
            if (daily != null) {
                SummaryMetric(
                    value = daily.currentStreak.toString(),
                    label = stringResource(Res.string.profile_streak),
                    caption = stringResource(Res.string.profile_best_streak, daily.bestStreak),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Bolt,
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
                InlineMetric(Icons.Filled.Diamond, economy.gems.toString(), stringResource(Res.string.profile_gems))
                InlineMetric(
                    Icons.Filled.Favorite,
                    "${economy.lives}/${economy.maximumLives}",
                    stringResource(Res.string.profile_lives),
                )
            }
            InlineMetric(Icons.Filled.Lightbulb, statistics.totalHintsUsed.toString(), stringResource(Res.string.total_hints_used))
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
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
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
) {
    val isPlayed: Boolean get() = (played ?: headline) > 0L
}

private fun profileGames(statistics: ProfileStatistics): List<ProfileGame> =
    listOf(
        ProfileGame(PuzzleType.BALANCE, statistics.balance.totalSolved, null, null, statistics.balance.solvedByDifficulty),
        ProfileGame(PuzzleType.CROWNS, statistics.crowns.totalSolved, null, null, statistics.crowns.solvedByDifficulty),
        ProfileGame(
            PuzzleType.SUDOKU,
            statistics.sudoku.solved,
            statistics.sudoku.played,
            statistics.sudoku.failed,
            statistics.sudoku.solvedByDifficulty,
            hintsUsed = statistics.sudoku.hintsUsed,
        ),
        ProfileGame(
            PuzzleType.WORD,
            statistics.word.solved,
            statistics.word.played,
            statistics.word.failed,
            ProfileDifficultyCounts(0, 0, 0, 0),
            word = statistics.word,
        ),
        ProfileGame(
            PuzzleType.GAME_2048,
            statistics.game2048.solved,
            statistics.game2048.played,
            statistics.game2048.failed,
            statistics.game2048.solvedByDifficulty,
        ),
    )

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
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
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
                Icons.Filled.BarChart,
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
