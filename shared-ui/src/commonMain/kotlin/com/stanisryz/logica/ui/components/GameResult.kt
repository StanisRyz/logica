package com.stanisryz.logica.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.stanisryz.logica.puzzle.core.model.PuzzleStars
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.result_daily_failed
import com.stanisryz.logica.shared.ui.generated.resources.result_daily_solved
import com.stanisryz.logica.shared.ui.generated.resources.result_failed
import com.stanisryz.logica.shared.ui.generated.resources.result_hints
import com.stanisryz.logica.shared.ui.generated.resources.result_level_failed
import com.stanisryz.logica.shared.ui.generated.resources.result_level_solved
import com.stanisryz.logica.shared.ui.generated.resources.result_life
import com.stanisryz.logica.shared.ui.generated.resources.result_life_for_ad
import com.stanisryz.logica.shared.ui.generated.resources.result_mistakes
import com.stanisryz.logica.shared.ui.generated.resources.result_next_level
import com.stanisryz.logica.shared.ui.generated.resources.result_next_life_in
import com.stanisryz.logica.shared.ui.generated.resources.result_no_lives
import com.stanisryz.logica.shared.ui.generated.resources.result_retry
import com.stanisryz.logica.shared.ui.generated.resources.result_retry_save
import com.stanisryz.logica.shared.ui.generated.resources.result_reward
import com.stanisryz.logica.shared.ui.generated.resources.result_save_error
import com.stanisryz.logica.shared.ui.generated.resources.result_saving
import com.stanisryz.logica.shared.ui.generated.resources.result_solved
import com.stanisryz.logica.shared.ui.generated.resources.result_stars
import com.stanisryz.logica.shared.ui.generated.resources.result_to_difficulty
import com.stanisryz.logica.shared.ui.generated.resources.result_to_games
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_loading
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_retry
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_unavailable
import com.stanisryz.logica.ui.profile.ResultCardAchievements
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

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
 * What the card offers instead of Retry or Next level when no life is left: the countdown to the
 * next life ([nextLifeAtEpochMs] read against the host's own [nowEpochMs]) and one rewarded life,
 * through the host's ordinary rewarded-life placement. Hosts pass it only at zero lives, so a life
 * that comes back (by time or by the ad) turns the primary action back into Retry.
 */
class GameResultLifeOffer(
    val nextLifeAtEpochMs: Long?,
    val nowEpochMs: () -> Long,
    val ad: ContinueAdAvailability,
    val onWatchAd: () -> Unit,
    val onRetryAd: () -> Unit,
)

/** The result card's one full-width primary action. */
enum class GameResultPrimaryAction {
    RETRY_SAVE,
    SAVING,
    RETRY,
    NEXT_LEVEL,
    LIFE_OFFER,
    NONE,
}

/**
 * The primary action follows the state: saving waits, a save error retries that save, a failure
 * retries and a solved Catalog level moves on (both only with a life, otherwise the card offers
 * one), and a solved Daily has nothing left but the exit.
 */
fun gameResultPrimaryAction(
    saveState: GameResultSaveState,
    solved: Boolean,
    isDaily: Boolean,
    livesOut: Boolean,
): GameResultPrimaryAction =
    when {
        saveState == GameResultSaveState.ERROR -> GameResultPrimaryAction.RETRY_SAVE
        saveState == GameResultSaveState.SAVING -> GameResultPrimaryAction.SAVING
        !solved -> if (livesOut) GameResultPrimaryAction.LIFE_OFFER else GameResultPrimaryAction.RETRY
        !isDaily -> if (livesOut) GameResultPrimaryAction.LIFE_OFFER else GameResultPrimaryAction.NEXT_LEVEL
        else -> GameResultPrimaryAction.NONE
    }

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
    stars: Int? = null,
    lifeOffer: GameResultLifeOffer? = null,
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
        Box {
            // A solved attempt bursts a little confetti from the stars, behind the card's content.
            if (positive) ResultConfetti(Modifier.matchParentSize())
            Column(
                modifier = Modifier.padding(CARD_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            ) {
                if (positive && stars != null) {
                    ResultStars(stars)
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(MARK_SIZE)
                                .clip(CircleShape)
                                .background(if (positive) palette.successContainer else colors.errorContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (positive) Icons.Rounded.TaskAlt else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = if (positive) palette.onSuccessContainer else colors.onErrorContainer,
                            modifier = Modifier.size(MARK_ICON_SIZE),
                        )
                    }
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
                ResultCardAchievements()
                ResultTiles(
                    economy = economy.takeIf { saveState == GameResultSaveState.SAVED },
                    mistakesUsed = mistakesUsed,
                    maxMistakes = maxMistakes,
                    hintsUsed = hintsUsed,
                )
                val primaryModifier = Modifier.fillMaxWidth().padding(top = LogicaSpacing.text)
                when (gameResultPrimaryAction(saveState, solved, isDaily, livesOut = lifeOffer != null)) {
                    GameResultPrimaryAction.RETRY_SAVE ->
                        Button(onClick = onRetrySave, modifier = primaryModifier) {
                            Text(stringResource(Res.string.result_retry_save))
                        }
                    GameResultPrimaryAction.SAVING ->
                        Button(onClick = {}, enabled = false, modifier = primaryModifier) {
                            Text(stringResource(Res.string.result_saving))
                        }
                    GameResultPrimaryAction.RETRY ->
                        Button(onClick = onRetry, enabled = retryAllowed, modifier = primaryModifier) {
                            Text(stringResource(Res.string.result_retry))
                        }
                    GameResultPrimaryAction.NEXT_LEVEL ->
                        Button(onClick = onNextLevel, modifier = primaryModifier) {
                            Text(stringResource(Res.string.result_next_level))
                        }
                    GameResultPrimaryAction.LIFE_OFFER -> lifeOffer?.let { ResultLifeOffer(it, primaryModifier) }
                    GameResultPrimaryAction.NONE -> Unit
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
}

/** At zero lives: why the card cannot go on, the wait for the next life, and one life for an ad. */
@Composable
private fun ResultLifeOffer(
    offer: GameResultLifeOffer,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dueAt = offer.nextLifeAtEpochMs
    var now by remember(offer) { mutableLongStateOf(offer.nowEpochMs()) }
    LaunchedEffect(offer, dueAt) {
        while (dueAt != null) {
            now = offer.nowEpochMs()
            delay(COUNTDOWN_TICK_MILLIS)
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
            GameIconImage(GameIcon.HEART_BROKEN, size = TILE_ICON_SIZE)
            Text(
                text = stringResource(Res.string.result_no_lives),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                textAlign = TextAlign.Center,
            )
        }
        dueAt?.let {
            Text(
                text = stringResource(Res.string.result_next_life_in, formatLifeCountdown(it - now)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        when (offer.ad) {
            ContinueAdAvailability.READY ->
                Button(onClick = offer.onWatchAd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(LIFE_AD_ICON_SIZE))
                    Spacer(Modifier.width(LogicaSpacing.text))
                    Text(stringResource(Res.string.result_life_for_ad))
                }
            ContinueAdAvailability.LOADING ->
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.size(LIFE_AD_ICON_SIZE), strokeWidth = 2.dp)
                    Spacer(Modifier.width(LogicaSpacing.text))
                    Text(stringResource(Res.string.second_chance_loading))
                }
            ContinueAdAvailability.UNAVAILABLE -> {
                Text(
                    text = stringResource(Res.string.second_chance_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                TextButton(onClick = offer.onRetryAd) { Text(stringResource(Res.string.second_chance_retry)) }
            }
        }
    }
}

/** Minutes and seconds left, rounded up, like the lives dialogs. */
internal fun formatLifeCountdown(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis.coerceAtLeast(0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    return "${totalSeconds / SECONDS_PER_MINUTE}:${(totalSeconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

private const val COUNTDOWN_TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private val LIFE_AD_ICON_SIZE = 20.dp

/** One short confetti burst: pieces fly up and out from the stars, then fall and fade. */
@Composable
private fun ResultConfetti(modifier: Modifier) {
    val palette = LocalLogicaPalette.current
    val colors = MaterialTheme.colorScheme
    val tints = listOf(palette.star, colors.primary, colors.tertiary, palette.success, colors.secondary)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(CONFETTI_MILLIS, easing = LinearEasing)) }
    val pieces = remember { List(CONFETTI_COUNT) { ConfettiPiece(Random(it * 7919 + 13)) } }
    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val seconds = t * CONFETTI_MILLIS / 1000f
        val originY = CONFETTI_ORIGIN.toPx()
        val pieceWidth = CONFETTI_PIECE.toPx()
        pieces.forEach { piece ->
            val x = size.width / 2 + piece.spread * size.width * 0.1f + piece.velocityX * size.width * seconds
            val y = originY - piece.velocityY * size.height * seconds + CONFETTI_GRAVITY * size.height * seconds * seconds
            val alpha = if (t > CONFETTI_FADE_FROM) (1f - t) / (1f - CONFETTI_FADE_FROM) else 1f
            rotate(piece.spin * seconds, pivot = Offset(x, y)) {
                drawRect(
                    color = tints[piece.tint % tints.size],
                    topLeft = Offset(x - pieceWidth / 2, y - pieceWidth * piece.aspect / 2),
                    size = Size(pieceWidth, pieceWidth * piece.aspect),
                    alpha = alpha,
                )
            }
        }
    }
}

private class ConfettiPiece(
    random: Random,
) {
    val spread = random.nextFloat() - 0.5f
    val velocityX = (random.nextFloat() - 0.5f) * 1.1f
    val velocityY = 0.5f + random.nextFloat() * 0.7f
    val spin = (random.nextFloat() - 0.5f) * 900f
    val aspect = 0.5f + random.nextFloat() * 1.2f
    val tint = random.nextInt(0, 100)
}

/**
 * Stars earned by a solved attempt: three with no mistakes, two with one, one with more. A failed
 * attempt earns none, so hosts pass null for it.
 */
fun starsForMistakes(mistakesUsed: Int): Int = PuzzleStars.forMistakes(mistakesUsed)

/** Word's stars follow the guesses used: one or two earn three, three or four earn two. */
fun starsForWordAttempts(attemptsUsed: Int): Int = PuzzleStars.forWordAttempts(attemptsUsed)

/** The earned stars pop in one after another; the rest stay as quiet outlines. */
@Composable
private fun ResultStars(stars: Int) {
    val palette = LocalLogicaPalette.current
    val colors = MaterialTheme.colorScheme
    val description = stringResource(Res.string.result_stars, stars, MAX_STARS)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(STAR_SPACING),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(MAX_STARS) { index ->
            val earned = index < stars
            val scale = remember { Animatable(if (earned) 0f else 1f) }
            LaunchedEffect(earned) {
                if (earned) {
                    delay(STAR_STAGGER_MILLIS * index)
                    scale.animateTo(1f, spring(dampingRatio = STAR_SPRING_DAMPING, stiffness = Spring.StiffnessMediumLow))
                }
            }
            Icon(
                imageVector = if (earned) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = null,
                // Outlined, not only paler, and in the 3:1 outline role.
                tint = if (earned) palette.star else colors.outline,
                modifier =
                    Modifier
                        // The middle star stands a little taller, like a podium.
                        .size(if (index == 1) STAR_SIZE_MIDDLE else STAR_SIZE)
                        .scale(scale.value),
            )
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
    stars: Int? = null,
    lifeOffer: GameResultLifeOffer? = null,
) {
    // Back (Esc on Web) is the card's own exit, with all of its rules, once the result is saved;
    // before that it does nothing, and a tap outside never decides anything.
    Dialog(
        onDismissRequest = { if (saveState == GameResultSaveState.SAVED) onExit() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
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
            stars = stars,
            lifeOffer = lifeOffer,
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
                add(ResultTile(Icons.Rounded.Diamond, "+$it", stringResource(Res.string.result_reward), colors.primary, GameIcon.GEM))
            }
            economy?.livesLost?.takeIf { it > 0 }?.let {
                add(
                    ResultTile(
                        Icons.Rounded.HeartBroken,
                        "−$it",
                        stringResource(Res.string.result_life),
                        colors.error,
                        GameIcon.HEART_BROKEN,
                    ),
                )
            }
            mistakesUsed?.let {
                add(
                    ResultTile(
                        Icons.Rounded.Close,
                        "$it/$maxMistakes",
                        stringResource(Res.string.result_mistakes),
                        if (it > 0) colors.error else colors.onSurfaceVariant,
                    ),
                )
            }
            hintsUsed?.let {
                add(
                    ResultTile(
                        Icons.Rounded.Lightbulb,
                        "$it",
                        stringResource(Res.string.result_hints),
                        colors.onSurfaceVariant,
                        GameIcon.HINT,
                    ),
                )
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
                    tile.artwork?.let { GameIconImage(it, size = TILE_ICON_SIZE) }
                        ?: Icon(tile.icon, contentDescription = null, tint = tile.tint, modifier = Modifier.size(TILE_ICON_SIZE))
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

/** A result tile: a picture for a currency ([artwork]), otherwise the tinted [icon]. */
private class ResultTile(
    val icon: ImageVector,
    val value: String,
    val label: String,
    val tint: Color,
    val artwork: GameIcon? = null,
)

private const val REWARD_SOUND_DELAY_MILLIS = 450L
private const val MAX_STARS = PuzzleStars.MAX_STARS
private const val CONFETTI_MILLIS = 1800
private const val CONFETTI_COUNT = 44
private const val CONFETTI_GRAVITY = 0.9f
private const val CONFETTI_FADE_FROM = 0.7f
private val CONFETTI_ORIGIN = 56.dp
private val CONFETTI_PIECE = 7.dp
private const val STAR_STAGGER_MILLIS = 160L
private const val STAR_SPRING_DAMPING = 0.45f
private val STAR_SIZE = 44.dp
private val STAR_SIZE_MIDDLE = 56.dp
private val STAR_SPACING = 6.dp
private val CARD_MAX_WIDTH = 400.dp
private val CARD_PADDING = 24.dp
private val MARK_SIZE = 56.dp
private val MARK_ICON_SIZE = 32.dp
private val TILE_ICON_SIZE = 18.dp
private val TILE_VERTICAL_PADDING = 10.dp
