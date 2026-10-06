package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/**
 * The weekly star tournament on Yandex Games: the stars of first Expert solves in the starred
 * Catalog games, summed per week, one table for everyone, prizes for the 20 places a table shows.
 *
 * Yandex tables never reset and keep one value per Player, so two tables take turns: an even week
 * writes `weeklyStarsA`, an odd one `weeklyStarsB`, and each score is `week * 1 000 000 + stars`.
 * An entry from an older cycle is always below every entry of the running week, so it sorts under
 * them and is dropped when shown. Last week's table is written by nobody this week, so its places
 * are final when the prize is read from it.
 *
 * Weeks start on Monday 00:00 Moscow time (UTC+3 all year) and are counted from the tournament's
 * own epoch rather than 1970, so `week * 1 000 000` stays inside a table's 32-bit score for decades.
 */
internal object WebWeeklyTournament {
    const val BOARD_EVEN = "weeklyStarsA"
    const val BOARD_ODD = "weeklyStarsB"
    const val WEEK_SCORE_BASE = 1_000_000

    /** Monday, 5 January 2026, 00:00 in Moscow (UTC+3): week 0. */
    const val EPOCH_MS = 1_767_560_400_000L
    const val WEEK_MS = 7L * 24L * 60L * 60L * 1_000L

    /** The games whose solves earn stars; 2048 and Block Sudoku have none. */
    val STARRED_GAMES = setOf(PuzzleType.BALANCE, PuzzleType.CROWNS, PuzzleType.SUDOKU, PuzzleType.NONOGRAM, PuzzleType.WORD)

    /** The tournament week of [epochMillis]. */
    fun week(epochMillis: Long): Int = (epochMillis - EPOCH_MS).floorDiv(WEEK_MS).toInt()

    /** The table [week] writes: A for an even week, B for an odd one. */
    fun board(week: Int): String = if (week.mod(2) == 0) BOARD_EVEN else BOARD_ODD

    /** The table score of [stars] in [week]. */
    fun score(
        week: Int,
        stars: Int,
    ): Int = week * WEEK_SCORE_BASE + stars.coerceIn(0, WEEK_SCORE_BASE - 1)

    /** The stars a table [score] holds for [week], or null when the score belongs to another week. */
    fun starsIn(
        score: Int,
        week: Int,
    ): Int? = if (score / WEEK_SCORE_BASE == week) score % WEEK_SCORE_BASE else null

    /** Milliseconds until the week of [epochMillis] ends. */
    fun millisUntilWeekEnd(epochMillis: Long): Long = EPOCH_MS + (week(epochMillis) + 1L) * WEEK_MS - epochMillis

    /**
     * The stars an attempt adds to the week: only a first Catalog solve (no replay) of an Expert
     * level in a starred game, worth the stars that solve earned.
     */
    fun starsForSolve(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        replay: Boolean,
        stars: Int?,
    ): Int = if (!replay && difficulty == Difficulty.EXPERT && puzzleType in STARRED_GAMES && stars != null) stars.coerceIn(0, 3) else 0

    /**
     * The prize of last week's table entry ([place], [score]) in [week], which must be the week just
     * ended: nothing for another week's score, no stars, or a place beyond the prize places.
     */
    fun prize(
        week: Int,
        place: Int,
        score: Int,
    ): Int {
        val stars = starsIn(score, week) ?: return 0
        return if (stars > 0 && place >= 1) EconomyPolicy.weeklyPrize(place) else 0
    }

    /** Table rows of the running [week] only, their scores turned back into stars. */
    fun rowsOfWeek(
        snapshot: WebLeaderboardSnapshot,
        week: Int,
    ): WebLeaderboardSnapshot {
        val rows = snapshot.entries.mapNotNull { entry -> starsIn(entry.score, week)?.let { entry.copy(score = it) } }
        val ownRank = snapshot.playerRank?.takeIf { rank -> rows.any { it.rank == rank } }
        return WebLeaderboardSnapshot(rows, ownRank)
    }
}
