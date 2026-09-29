package com.stanisryz.logica.ui.rating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleRating
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.gallery_action
import com.stanisryz.logica.shared.ui.generated.resources.rating_action
import com.stanisryz.logica.shared.ui.generated.resources.rating_best_explained
import com.stanisryz.logica.shared.ui.generated.resources.rating_best_score
import com.stanisryz.logica.shared.ui.generated.resources.rating_leaderboard
import com.stanisryz.logica.shared.ui.generated.resources.rating_levels_count
import com.stanisryz.logica.shared.ui.generated.resources.rating_levels_explained
import com.stanisryz.logica.shared.ui.generated.resources.rating_points
import com.stanisryz.logica.shared.ui.generated.resources.rating_title
import com.stanisryz.logica.shared.ui.generated.resources.rules_done
import com.stanisryz.logica.ui.components.catalogTitleResource
import com.stanisryz.logica.ui.game2048.formatGame2048Number
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What one game's rating is made of; hosts derive it from durable progress, never store it. */
sealed interface GameRating {
    /** The value a leaderboard records for this game. */
    val value: Long

    /** Catalog levels cleared per difficulty, weighed by [PuzzleRating]. */
    data class Levels(
        val clearedLevels: Map<Difficulty, Long>,
    ) : GameRating {
        override val value: Long get() = PuzzleRating.levelPoints(clearedLevels)
    }

    /** 2048: the best score of any single game. */
    data class BestScore(
        val score: Long,
    ) : GameRating {
        override val value: Long get() = score
    }
}

/**
 * The actions above the difficulty cards: how to play, the game's rating, and, for games that
 * keep something to look back at, a gallery. Hosts pass their own "how to play" label.
 */
@Composable
fun DifficultyScreenActions(
    howToPlayLabel: String,
    onHowToPlay: () -> Unit,
    onRating: () -> Unit,
    modifier: Modifier = Modifier,
    onGallery: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ACTION_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(howToPlayLabel, onHowToPlay)
        ActionButton(stringResource(Res.string.rating_action), onRating)
        onGallery?.let { ActionButton(stringResource(Res.string.gallery_action), it) }
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp)) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One game's rating: the player's points (or best 2048 score) with what they are made of, and
 * below it whatever table the host can show — Web the Yandex leaderboard, Android nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameRatingSheet(
    puzzleType: PuzzleType,
    rating: GameRating,
    onDismiss: () -> Unit,
    leaderboard: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LogicaSpacing.screenHorizontal)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            Text(
                text = stringResource(Res.string.rating_title, stringResource(puzzleType.catalogTitleResource())),
                style = MaterialTheme.typography.titleLarge,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(LogicaSpacing.cardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(
                            if (rating is GameRating.BestScore) Res.string.rating_best_score else Res.string.rating_points,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(formatGame2048Number(rating.value), style = MaterialTheme.typography.displaySmall)
                }
            }
            when (rating) {
                is GameRating.Levels -> {
                    Difficulty.entries.forEach { difficulty ->
                        LevelRow(difficulty, rating.clearedLevels[difficulty] ?: 0L)
                    }
                    Explanation(stringResource(Res.string.rating_levels_explained))
                }
                is GameRating.BestScore -> Explanation(stringResource(Res.string.rating_best_explained))
            }
            if (leaderboard != null) {
                Text(
                    stringResource(Res.string.rating_leaderboard),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = LogicaSpacing.text),
                )
                leaderboard()
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = LogicaSpacing.text)) {
                Text(stringResource(Res.string.rules_done))
            }
        }
    }
}

@Composable
private fun LevelRow(
    difficulty: Difficulty,
    cleared: Long,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = LogicaSpacing.text),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(difficulty.labelResource()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            stringResource(Res.string.rating_levels_count, formatGame2048Number(cleared)),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(end = LogicaSpacing.item),
        )
        Text(
            "+" + formatGame2048Number(cleared * PuzzleRating.pointsPerLevel(difficulty)),
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary,
        )
    }
}

@Composable
private fun Explanation(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val ACTION_GAP = 8.dp
