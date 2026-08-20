package com.stanisryz.logica.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.best_daily_streak
import com.stanisryz.logica.shared.ui.generated.resources.current_daily_streak
import com.stanisryz.logica.shared.ui.generated.resources.daily_completed_count
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_failed_count
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_played
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_solved_count
import com.stanisryz.logica.shared.ui.generated.resources.profile_empty_body
import com.stanisryz.logica.shared.ui.generated.resources.profile_empty_title
import com.stanisryz.logica.shared.ui.generated.resources.profile_load_error
import com.stanisryz.logica.shared.ui.generated.resources.profile_overall
import com.stanisryz.logica.shared.ui.generated.resources.profile_statistics
import com.stanisryz.logica.shared.ui.generated.resources.retry
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_failed_count
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_played
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_solved_count
import com.stanisryz.logica.shared.ui.generated.resources.total_hints_used
import com.stanisryz.logica.shared.ui.generated.resources.total_solved
import com.stanisryz.logica.shared.ui.generated.resources.word_attempt_bar_description
import com.stanisryz.logica.shared.ui.generated.resources.word_attempt_distribution
import com.stanisryz.logica.shared.ui.generated.resources.word_failed_count
import com.stanisryz.logica.shared.ui.generated.resources.word_percent_value
import com.stanisryz.logica.shared.ui.generated.resources.word_played
import com.stanisryz.logica.shared.ui.generated.resources.word_solved_count
import com.stanisryz.logica.shared.ui.generated.resources.word_win_rate
import com.stanisryz.logica.ui.components.catalogTitleResource
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Shared scrolling Profile presentation used by both platform hosts. */
@Composable
fun ProfileContent(
    uiState: ProfileUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
                ProfileUiState.Empty -> EmptyState(modifier)
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
        ProfileSection(stringResource(Res.string.profile_overall)) {
            val metrics =
                buildList {
                    add(ProfileMetric(stringResource(Res.string.total_solved), statistics.totalSolved.toString()))
                    statistics.dailyMetrics?.let { daily ->
                        add(
                            ProfileMetric(
                                stringResource(Res.string.daily_completed_count),
                                daily.completedCount.toString(),
                            ),
                        )
                        add(
                            ProfileMetric(
                                stringResource(Res.string.current_daily_streak),
                                daily.currentStreak.toString(),
                            ),
                        )
                        add(
                            ProfileMetric(
                                stringResource(Res.string.best_daily_streak),
                                daily.bestStreak.toString(),
                            ),
                        )
                    }
                    add(ProfileMetric(stringResource(Res.string.total_hints_used), statistics.totalHintsUsed.toString()))
                }
            ProfileMetricGrid(metrics)
        }
        ProfileSection(stringResource(Res.string.profile_statistics)) {
            SolvedPuzzleCard(PuzzleType.BALANCE, statistics.balance)
            SolvedPuzzleCard(PuzzleType.CROWNS, statistics.crowns)
            SudokuCard(statistics.sudoku)
            Game2048Card(statistics.game2048)
            WordCard(statistics.word)
        }
    }
}

@Composable
private fun SolvedPuzzleCard(
    puzzleType: PuzzleType,
    statistics: SolvedPuzzleProfileStatistics,
) {
    ProfileCard {
        ProfilePuzzleTitle(puzzleType)
        LabelledValue(stringResource(Res.string.total_solved), statistics.totalSolved)
        Difficulty.entries.forEach { difficulty ->
            LabelledValue(stringResource(difficulty.profileLabelResource()), statistics.solvedByDifficulty[difficulty])
        }
    }
}

@Composable
private fun SudokuCard(statistics: SudokuProfileStatistics) {
    ProfileCard {
        ProfilePuzzleTitle(PuzzleType.SUDOKU)
        LabelledValue(stringResource(Res.string.sudoku_played), statistics.played)
        LabelledValue(stringResource(Res.string.sudoku_solved_count), statistics.solved)
        LabelledValue(stringResource(Res.string.sudoku_failed_count), statistics.failed)
        LabelledValue(stringResource(Res.string.total_hints_used), statistics.hintsUsed)
        Difficulty.entries.forEach { difficulty ->
            LabelledValue(stringResource(difficulty.profileLabelResource()), statistics.solvedByDifficulty[difficulty])
        }
    }
}

@Composable
private fun Game2048Card(statistics: Game2048ProfileStatistics) {
    ProfileCard {
        ProfilePuzzleTitle(PuzzleType.GAME_2048)
        LabelledValue(stringResource(Res.string.game_2048_played), statistics.played)
        LabelledValue(stringResource(Res.string.game_2048_solved_count), statistics.solved)
        LabelledValue(stringResource(Res.string.game_2048_failed_count), statistics.failed)
        Difficulty.entries.forEach { difficulty ->
            LabelledValue(stringResource(difficulty.profileLabelResource()), statistics.solvedByDifficulty[difficulty])
        }
    }
}

@Composable
private fun WordCard(statistics: WordProfileStatistics) {
    ProfileCard {
        ProfilePuzzleTitle(PuzzleType.WORD)
        LabelledValue(stringResource(Res.string.word_played), statistics.played)
        LabelledValue(stringResource(Res.string.word_solved_count), statistics.solved)
        LabelledValue(stringResource(Res.string.word_failed_count), statistics.failed)
        LabelledValue(
            stringResource(Res.string.word_win_rate),
            stringResource(Res.string.word_percent_value, statistics.winRatePercent),
        )
        Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
            SectionTitle(stringResource(Res.string.word_attempt_distribution))
            AttemptDistributionBars(statistics.solvedAttemptDistribution)
        }
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

private data class ProfileMetric(
    val label: String,
    val value: String,
)

@Composable
private fun ProfileMetricGrid(metrics: List<ProfileMetric>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
                rowMetrics.forEach { metric ->
                    ProfileCard(
                        modifier = Modifier.weight(1f),
                        verticalSpacing = LogicaSpacing.text,
                    ) {
                        Text(
                            metric.value,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        SupportingText(metric.label)
                    }
                }
                if (rowMetrics.size == 1) Spacer(Modifier.weight(1f))
            }
        }
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
private fun ProfilePuzzleTitle(puzzleType: PuzzleType) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(ACCENT_DOT_SIZE)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(puzzleType.profileAccentColor()),
        )
        Spacer(Modifier.size(LogicaSpacing.action))
        Text(
            stringResource(puzzleType.catalogTitleResource()),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun LabelledValue(
    label: String,
    value: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LabelledValue(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
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
private fun EmptyState(modifier: Modifier) {
    CenteredState(modifier, verticalSpacing = LogicaSpacing.text) {
        Text(
            stringResource(Res.string.profile_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        SupportingText(stringResource(Res.string.profile_empty_body), textAlign = TextAlign.Center)
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
) {
    Text(
        text,
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
private val BAR_HEIGHT = 18.dp
