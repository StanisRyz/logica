package com.stanisryz.logica.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.EconomyRules
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
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
    puzzleType: PuzzleType,
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
    isReplay: Boolean = LocalLevelReplay.current,
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
        // A solved replay pays nothing; a failed one costs a life like any failure.
        economy = if (isReplay && isSolved) GameResultEconomy(gemsEarned = 0) else resultEconomy(isSolved, puzzleType, difficulty),
        mistakesUsed = mistakesUsed,
        maxMistakes = maxMistakes,
        hintsUsed = hintsUsed,
        retryAllowed = isRetryAllowed,
        stars = if (isSolved) starsForMistakes(mistakesUsed) else null,
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

/** A solved attempt pays its game and difficulty reward, a failed one costs one life (`EconomyRules`). */
internal fun resultEconomy(
    isSolved: Boolean,
    puzzleType: PuzzleType,
    difficulty: Difficulty,
): GameResultEconomy =
    if (isSolved) {
        GameResultEconomy(gemsEarned = EconomyRules.solvedGemReward(puzzleType, difficulty))
    } else {
        GameResultEconomy(livesLost = EconomyRules.FAILED_LIFE_PENALTY)
    }

/** True while the game on screen replays a cleared level from the level map. */
internal val LocalLevelReplay = staticCompositionLocalOf { false }
