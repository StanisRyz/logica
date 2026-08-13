package com.stanisryz.logica.ui.screens

import com.stanisryz.logica.R
import com.stanisryz.logica.daily.DailyEntryState
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/**
 * One regular (non-Daily) game as the Game hub catalog presents it. Its complete card leads to the
 * game's shared direct-launch difficulty screen.
 */
internal data class GameCatalogEntry(
    val puzzleType: PuzzleType,
    val onPlay: () -> Unit,
)

/**
 * The catalog of regular games, in the order the hub lists them. A sixth game is one row here and
 * nothing else: neither the list nor its cards assume a fixed count.
 */
private val CATALOG_PUZZLES: List<PuzzleType> =
    listOf(
        PuzzleType.BALANCE,
        PuzzleType.CROWNS,
        PuzzleType.WORD,
        PuzzleType.SUDOKU,
        PuzzleType.GAME_2048,
    )

internal fun gameCatalogEntries(onPlay: (PuzzleType) -> Unit): List<GameCatalogEntry> =
    CATALOG_PUZZLES.map { puzzleType ->
        GameCatalogEntry(
            puzzleType = puzzleType,
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
