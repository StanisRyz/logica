package com.stanisryz.logica.ui.screens

import com.stanisryz.logica.R
import com.stanisryz.logica.daily.DailyEntryState
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/** One regular (non-Daily) game as the Game hub catalog presents it. */
internal data class GameCatalogEntry(
    val puzzleType: PuzzleType,
    val descriptionResource: Int,
    val hasActiveSession: Boolean,
    val onContinue: () -> Unit,
    val onNew: () -> Unit,
)

/**
 * The catalog of regular games, in the order the hub lists them. A fourth game is one row here and
 * nothing else: neither the list nor its cards assume there are exactly three.
 */
private val CATALOG_PUZZLES: List<Pair<PuzzleType, Int>> =
    listOf(
        PuzzleType.BALANCE to R.string.balance_catalog_description,
        PuzzleType.CROWNS to R.string.crowns_catalog_description,
        PuzzleType.WORD to R.string.word_catalog_description,
    )

internal fun gameCatalogEntries(
    hasActiveSession: (PuzzleType) -> Boolean,
    onContinue: (PuzzleType) -> Unit,
    onNew: (PuzzleType) -> Unit,
): List<GameCatalogEntry> =
    CATALOG_PUZZLES.map { (puzzleType, description) ->
        GameCatalogEntry(
            puzzleType = puzzleType,
            descriptionResource = description,
            hasActiveSession = hasActiveSession(puzzleType),
            onContinue = { onContinue(puzzleType) },
            onNew = { onNew(puzzleType) },
        )
    }

/**
 * The one action a Daily card performs when it is tapped, or `null` for an entry that is done. The
 * card itself is the target, so a state maps to exactly one label.
 */
internal fun DailyEntryState.primaryActionResource(): Int? =
    when (this) {
        DailyEntryState.AVAILABLE -> R.string.start
        DailyEntryState.IN_PROGRESS -> R.string.continue_game
        DailyEntryState.RETRY -> R.string.retry_puzzle
        DailyEntryState.COMPLETED -> null
    }

/**
 * Whether tapping the card does anything. A finished entry never acts, a saved one always opens —
 * at zero lives too, with its gameplay actions disabled — and starting an attempt needs a life.
 */
internal fun DailyEntryState.isActionable(gameplayAllowed: Boolean): Boolean =
    when (this) {
        DailyEntryState.COMPLETED -> false
        DailyEntryState.IN_PROGRESS -> true
        DailyEntryState.AVAILABLE, DailyEntryState.RETRY -> gameplayAllowed
    }
