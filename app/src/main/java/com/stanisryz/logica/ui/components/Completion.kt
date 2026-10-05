package com.stanisryz.logica.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.ads.RewardedAdState
import com.stanisryz.logica.economy.EconomyClock
import com.stanisryz.logica.economy.EconomyRules
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.result.CompletionPersistence

/**
 * Balance, Crowns, and Sudoku show the shared result dialog for both endings. A failed attempt
 * leads with Retry on the very same level; a solved Catalog level leads to the next level, and a
 * solved Daily returns to the Game hub. The board stays visible behind it.
 *
 * The wallet effect is reported only once the result is durably stored, and a retry is offered only
 * while a life is available.
 */
@Composable
internal fun PuzzleTerminalDialog(
    isSolved: Boolean,
    completionPersistence: CompletionPersistence,
    levelNumber: Int?,
    mistakesUsed: Int,
    hintsUsed: Int,
    maxMistakes: Int,
    difficulty: Difficulty,
    isRetryAllowed: Boolean,
    isDaily: Boolean,
    onRetryCompletion: () -> Unit,
    onRetryLevel: () -> Unit,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    gemsEarned: Int,
) {
    if (LocalSecondChancePending.current) return
    GameResultDialog(
        solved = isSolved,
        levelNumber = levelNumber,
        isDaily = isDaily,
        difficultyLabel = difficulty.russianLabel(),
        saveState = completionPersistence.toResultSaveState(),
        onNextLevel = onNextLevel,
        onRetry = onRetryLevel,
        onRetrySave = onRetryCompletion,
        onExit = onGameHub,
        saveErrorDetail = stringResource(R.string.completion_save_error_body),
        economy = resultEconomy(isSolved, gemsEarned),
        mistakesUsed = mistakesUsed,
        maxMistakes = maxMistakes,
        hintsUsed = hintsUsed,
        retryAllowed = isRetryAllowed,
        stars = if (isSolved) starsForMistakes(mistakesUsed) else null,
        lifeOffer = resultLifeOffer(),
    )
}

/** The wallet and the rewarded life as the result card sees them; the shell provides it to every game. */
internal class ResultLives(
    val economy: PlayerEconomy,
    val ad: RewardedAdState,
    val watch: () -> Unit,
    val retry: () -> Unit,
)

internal val LocalResultLives = compositionLocalOf<ResultLives?> { null }

/**
 * At zero lives the result card offers the next life's countdown and one life for a rewarded ad,
 * the same `RewardedLifeController` path as the Lives dialog; with a life it offers nothing extra.
 */
@Composable
internal fun resultLifeOffer(): GameResultLifeOffer? {
    val lives = LocalResultLives.current ?: return null
    if (lives.economy.isGameplayAllowed) return null
    return GameResultLifeOffer(
        nextLifeAtEpochMs = lives.economy.nextLifeAtEpochMillis,
        nowEpochMs = EconomyClock.SYSTEM::nowEpochMillis,
        ad =
            when (lives.ad) {
                RewardedAdState.READY -> ContinueAdAvailability.READY
                RewardedAdState.UNAVAILABLE -> ContinueAdAvailability.UNAVAILABLE
                else -> ContinueAdAvailability.LOADING
            },
        onWatchAd = lives.watch,
        onRetryAd = lives.retry,
    )
}

/** Android results always reach durable storage; before that they are still being saved. */
internal fun CompletionPersistence.toResultSaveState(): GameResultSaveState =
    when (this) {
        CompletionPersistence.Saved -> GameResultSaveState.SAVED
        CompletionPersistence.Error -> GameResultSaveState.ERROR
        CompletionPersistence.Saving,
        CompletionPersistence.NotRequired,
        -> GameResultSaveState.SAVING
    }

/**
 * A solved attempt shows the gems its saved result actually credited ([gemsEarned], from the ledger
 * row of that result); a failed one costs one life (`EconomyRules`).
 */
internal fun resultEconomy(
    isSolved: Boolean,
    gemsEarned: Int,
): GameResultEconomy =
    if (isSolved) {
        GameResultEconomy(gemsEarned = gemsEarned)
    } else {
        GameResultEconomy(livesLost = EconomyRules.FAILED_LIFE_PENALTY)
    }
