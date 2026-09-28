package com.stanisryz.logica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.result_daily_failed
import com.stanisryz.logica.shared.ui.generated.resources.result_daily_solved
import com.stanisryz.logica.shared.ui.generated.resources.result_failed
import com.stanisryz.logica.shared.ui.generated.resources.result_hints
import com.stanisryz.logica.shared.ui.generated.resources.result_level_failed
import com.stanisryz.logica.shared.ui.generated.resources.result_level_solved
import com.stanisryz.logica.shared.ui.generated.resources.result_life
import com.stanisryz.logica.shared.ui.generated.resources.result_mistakes
import com.stanisryz.logica.shared.ui.generated.resources.result_next_level
import com.stanisryz.logica.shared.ui.generated.resources.result_retry
import com.stanisryz.logica.shared.ui.generated.resources.result_retry_save
import com.stanisryz.logica.shared.ui.generated.resources.result_reward
import com.stanisryz.logica.shared.ui.generated.resources.result_save_error
import com.stanisryz.logica.shared.ui.generated.resources.result_saving
import com.stanisryz.logica.shared.ui.generated.resources.result_solved
import com.stanisryz.logica.shared.ui.generated.resources.result_to_difficulty
import com.stanisryz.logica.shared.ui.generated.resources.result_to_games
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** Where the durable result of a finished attempt stands; hosts map their own completion states. */
enum class GameResultSaveState {
    SAVING,
    SAVED,
    ERROR,
}

/**
 * What a finished attempt earned or cost in the wallet. It is shown only once the result is saved,
 * so the card never promises a reward that did not land.
 */
data class GameResultEconomy(
    val gemsEarned: Int = 0,
    val livesLost: Int = 0,
)

/**
 * The one result card for every game on both platforms: an outcome mark, the level (or Daily)
 * title and difficulty, a host detail line (score, answer, attempts), a row of tiles for the
 * reward, lost life, mistakes, and hints, then one full-width primary action and the way out.
 *
 * The primary action follows the state: saving waits, a save error retries that save, a failure
 * retries the same level while a life allows it, a solved Catalog level moves on, and a solved
 * Daily has nothing left but the exit.
 */
@Composable
fun GameResultCard(
    solved: Boolean,
    levelNumber: Int?,
    isDaily: Boolean,
    difficultyLabel: String,
    saveState: GameResultSaveState,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    saveErrorDetail: String? = null,
    economy: GameResultEconomy? = null,
    mistakesUsed: Int? = null,
    maxMistakes: Int = PuzzleMistakes.MAX_MISTAKES,
    hintsUsed: Int? = null,
    retryAllowed: Boolean = true,
    exitToDifficulty: Boolean = false,
    title: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalLogicaPalette.current
    val saveError = saveState == GameResultSaveState.ERROR
    val positive = solved && !saveError
    val resolvedTitle =
        title ?: when {
            saveError -> stringResource(Res.string.result_save_error)
            isDaily -> stringResource(if (solved) Res.string.result_daily_solved else Res.string.result_daily_failed)
            levelNumber != null ->
                stringResource(if (solved) Res.string.result_level_solved else Res.string.result_level_failed, levelNumber)
            else -> stringResource(if (solved) Res.string.result_solved else Res.string.result_failed)
        }
    val bodyLine = if (saveError) saveErrorDetail else detail
    val sounds = LocalGameSounds.current
    // The coin rings once the reward has actually landed, a beat after the win sound.
    val rewardLanded = saveState == GameResultSaveState.SAVED && (economy?.gemsEarned ?: 0) > 0
    LaunchedEffect(rewardLanded) {
        if (rewardLanded) {
            delay(REWARD_SOUND_DELAY_MILLIS)
            sounds.play(GameSound.REWARD)
        }
    }
    Surface(
        modifier = modifier.widthIn(max = CARD_MAX_WIDTH).semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(MARK_SIZE)
                        .clip(CircleShape)
                        .background(if (positive) palette.successContainer else colors.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (positive) Icons.Filled.TaskAlt else Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = if (positive) palette.onSuccessContainer else colors.onErrorContainer,
                    modifier = Modifier.size(MARK_ICON_SIZE),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = difficultyLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            bodyLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (saveError) colors.error else colors.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            ResultTiles(
                economy = economy.takeIf { saveState == GameResultSaveState.SAVED },
                mistakesUsed = mistakesUsed,
                maxMistakes = maxMistakes,
                hintsUsed = hintsUsed,
            )
            val primaryModifier = Modifier.fillMaxWidth().padding(top = LogicaSpacing.text)
            when {
                saveError ->
                    Button(onClick = onRetrySave, modifier = primaryModifier) {
                        Text(stringResource(Res.string.result_retry_save))
                    }
                saveState == GameResultSaveState.SAVING ->
                    Button(onClick = {}, enabled = false, modifier = primaryModifier) {
                        Text(stringResource(Res.string.result_saving))
                    }
                !solved ->
                    Button(onClick = onRetry, enabled = retryAllowed, modifier = primaryModifier) {
                        Text(stringResource(Res.string.result_retry))
                    }
                !isDaily ->
                    Button(onClick = onNextLevel, modifier = primaryModifier) {
                        Text(stringResource(Res.string.result_next_level))
                    }
                else -> Unit
            }
            val exitLabel = stringResource(if (exitToDifficulty) Res.string.result_to_difficulty else Res.string.result_to_games)
            if (solved && isDaily && saveState == GameResultSaveState.SAVED) {
                // A solved Daily entry is done for the day: leaving is the one action, so it leads.
                Button(onClick = onExit, modifier = primaryModifier) { Text(exitLabel) }
            } else {
                TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text(exitLabel) }
            }
        }
    }
}

/** [GameResultCard] over the finished board; only its actions close it. */
@Composable
fun GameResultDialog(
    solved: Boolean,
    levelNumber: Int?,
    isDaily: Boolean,
    difficultyLabel: String,
    saveState: GameResultSaveState,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onExit: () -> Unit,
    detail: String? = null,
    saveErrorDetail: String? = null,
    economy: GameResultEconomy? = null,
    mistakesUsed: Int? = null,
    maxMistakes: Int = PuzzleMistakes.MAX_MISTAKES,
    hintsUsed: Int? = null,
    retryAllowed: Boolean = true,
    exitToDifficulty: Boolean = false,
    title: String? = null,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        GameResultCard(
            solved = solved,
            levelNumber = levelNumber,
            isDaily = isDaily,
            difficultyLabel = difficultyLabel,
            saveState = saveState,
            onNextLevel = onNextLevel,
            onRetry = onRetry,
            onRetrySave = onRetrySave,
            onExit = onExit,
            detail = detail,
            saveErrorDetail = saveErrorDetail,
            economy = economy,
            mistakesUsed = mistakesUsed,
            maxMistakes = maxMistakes,
            hintsUsed = hintsUsed,
            retryAllowed = retryAllowed,
            exitToDifficulty = exitToDifficulty,
            title = title,
        )
    }
}

@Composable
private fun ResultTiles(
    economy: GameResultEconomy?,
    mistakesUsed: Int?,
    maxMistakes: Int,
    hintsUsed: Int?,
) {
    val colors = MaterialTheme.colorScheme
    val tiles =
        buildList {
            economy?.gemsEarned?.takeIf { it > 0 }?.let {
                add(ResultTile(Icons.Filled.Diamond, "+$it", stringResource(Res.string.result_reward), colors.primary))
            }
            economy?.livesLost?.takeIf { it > 0 }?.let {
                add(ResultTile(Icons.Filled.HeartBroken, "−$it", stringResource(Res.string.result_life), colors.error))
            }
            mistakesUsed?.let {
                add(
                    ResultTile(
                        Icons.Filled.Close,
                        "$it/$maxMistakes",
                        stringResource(Res.string.result_mistakes),
                        if (it > 0) colors.error else colors.onSurfaceVariant,
                    ),
                )
            }
            hintsUsed?.let {
                add(ResultTile(Icons.Filled.Lightbulb, "$it", stringResource(Res.string.result_hints), colors.onSurfaceVariant))
            }
        }
    if (tiles.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
    ) {
        tiles.forEach { tile ->
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(colors.surfaceContainerLowest)
                        .padding(vertical = TILE_VERTICAL_PADDING)
                        .clearAndSetSemantics { contentDescription = "${tile.label}: ${tile.value}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(tile.icon, contentDescription = null, tint = tile.tint, modifier = Modifier.size(TILE_ICON_SIZE))
                    Text(tile.value, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                }
                Text(
                    text = tile.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private class ResultTile(
    val icon: ImageVector,
    val value: String,
    val label: String,
    val tint: Color,
)

private const val REWARD_SOUND_DELAY_MILLIS = 450L
private val CARD_MAX_WIDTH = 400.dp
private val CARD_PADDING = 24.dp
private val MARK_SIZE = 56.dp
private val MARK_ICON_SIZE = 32.dp
private val TILE_ICON_SIZE = 18.dp
private val TILE_VERTICAL_PADDING = 10.dp
