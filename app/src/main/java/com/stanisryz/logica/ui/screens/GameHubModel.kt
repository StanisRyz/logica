package com.stanisryz.logica.ui.screens

import com.stanisryz.logica.R
import com.stanisryz.logica.daily.DailyEntryState
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/**
 * One regular (non-Daily) game as the Game hub catalog presents it. There is no saved-game state to
 * show any more: a card simply leads to the game's difficulty screen, where each difficulty carries
 * its own current level.
 */
internal data class GameCatalogEntry(
    val puzzleType: PuzzleType,
    val descriptionResource: Int,
    val onPlay: () -> Unit,
)

/**
 * The catalog of regular games, in the order the hub lists them. A sixth game is one row here and
 * nothing else: neither the list nor its cards assume a fixed count.
 */
private val CATALOG_PUZZLES: List<Pair<PuzzleType, Int>> =
    listOf(
        PuzzleType.BALANCE to R.string.balance_catalog_description,
        PuzzleType.CROWNS to R.string.crowns_catalog_description,
        PuzzleType.WORD to R.string.word_catalog_description,
        PuzzleType.SUDOKU to R.string.sudoku_catalog_description,
        PuzzleType.GAME_2048 to R.string.game_2048_catalog_description,
    )

internal fun gameCatalogEntries(onPlay: (PuzzleType) -> Unit): List<GameCatalogEntry> =
    CATALOG_PUZZLES.map { (puzzleType, description) ->
        GameCatalogEntry(
            puzzleType = puzzleType,
            descriptionResource = description,
            onPlay = { onPlay(puzzleType) },
        )
    }

/**
 * The one action a Daily card performs when it is tapped, or `null` for an entry that is done. The
 * card itself is the target, so a state maps to exactly one label. An unfinished Daily attempt is
 * transient, so there is no Continue.
 */
internal fun DailyEntryState.primaryActionResource(): Int? =
    when (this) {
        DailyEntryState.AVAILABLE -> R.string.start
        DailyEntryState.RETRY -> R.string.retry_puzzle
        DailyEntryState.COMPLETED -> null
    }

/** Whether tapping the card does anything. A finished entry never acts; starting needs a life. */
internal fun DailyEntryState.isActionable(gameplayAllowed: Boolean): Boolean =
    when (this) {
        DailyEntryState.COMPLETED -> false
        DailyEntryState.AVAILABLE, DailyEntryState.RETRY -> gameplayAllowed
    }
