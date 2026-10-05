package com.stanisryz.logica.puzzle.core.quest

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/** What one daily quest asks for. Every kind is counted from the day's terminal attempts only. */
enum class DailyQuestKind {
    /** Finish any attempts, solved or failed. */
    PLAY,

    /** Solve any puzzles: Catalog levels, replays, and Daily entries alike. */
    SOLVE,

    /** Solve one puzzle of [DailyQuest.puzzleType]. */
    SOLVE_GAME,

    /** Solve one puzzle on Hard or Expert. */
    SOLVE_HARD,

    /** Solve one Daily entry. */
    SOLVE_DAILY,

    /** Solve puzzles in this many different games. */
    DIFFERENT_GAMES,
}

/** One of the day's three quests: what it asks, how much, and the gems it pays once claimed. */
data class DailyQuest(
    val index: Int,
    val kind: DailyQuestKind,
    val target: Int,
    val gems: Int,
    val puzzleType: PuzzleType? = null,
) {
    fun progress(activity: DailyQuestActivity): Int =
        when (kind) {
            DailyQuestKind.PLAY -> activity.played
            DailyQuestKind.SOLVE -> activity.solved
            DailyQuestKind.SOLVE_GAME -> activity.solvedByType[puzzleType] ?: 0
            DailyQuestKind.SOLVE_HARD -> activity.solvedHard
            DailyQuestKind.SOLVE_DAILY -> activity.dailySolved
            DailyQuestKind.DIFFERENT_GAMES -> activity.solvedByType.count { it.value > 0 }
        }.coerceIn(0, target)

    fun isComplete(activity: DailyQuestActivity): Boolean = progress(activity) >= target
}

/**
 * What the player did on one local calendar day, counted from terminal attempts: [played] all of
 * them, [solvedByType] the solved ones per game, [solvedHard] solved on Hard or Expert, and
 * [dailySolved] solved Daily entries. Hosts derive it from durable results (Android) or a small
 * per-day counter (Web); nothing unfinished ever counts.
 */
data class DailyQuestActivity(
    val played: Int = 0,
    val solvedByType: Map<PuzzleType, Int> = emptyMap(),
    val solvedHard: Int = 0,
    val dailySolved: Int = 0,
) {
    val solved: Int
        get() = solvedByType.values.sum()

    /** The activity after one more terminal attempt. */
    fun plus(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        solved: Boolean,
        daily: Boolean,
    ): DailyQuestActivity =
        if (!solved) {
            copy(played = played + 1)
        } else {
            DailyQuestActivity(
                played = played + 1,
                solvedByType = solvedByType + (puzzleType to (solvedByType[puzzleType] ?: 0) + 1),
                solvedHard = solvedHard + if (difficulty >= Difficulty.HARD) 1 else 0,
                dailySolved = dailySolved + if (daily) 1 else 0,
            )
        }
}

/**
 * The one rule for daily quests on every platform: three quests a day, an easy one, a game or Daily
 * one, and a harder one, picked deterministically from the local calendar day ([epochDay], days
 * since 1970-01-01) so every device shows the same three. Claiming is the host's job and pays each
 * quest once.
 */
object DailyQuests {
    const val COUNT = 3

    /**
     * The first local day (2026-10-12) whose quests may name Block Sudoku. Claimed quests are kept by
     * their index, so every day before it keeps exactly the quests it was shown with.
     */
    const val BLOCK_SUDOKU_QUESTS_FROM_EPOCH_DAY = 20_738L

    /** Games a SOLVE_GAME quest may name before [BLOCK_SUDOKU_QUESTS_FROM_EPOCH_DAY]; the Catalog order. */
    private val GAMES_BEFORE_BLOCK_SUDOKU =
        listOf(
            PuzzleType.BALANCE,
            PuzzleType.CROWNS,
            PuzzleType.WORD,
            PuzzleType.SUDOKU,
            PuzzleType.GAME_2048,
            PuzzleType.NONOGRAM,
        )

    /** Games a SOLVE_GAME quest may name from [BLOCK_SUDOKU_QUESTS_FROM_EPOCH_DAY] on. */
    private val GAMES = GAMES_BEFORE_BLOCK_SUDOKU + PuzzleType.BLOCK_SUDOKU

    fun forDay(epochDay: Long): List<DailyQuest> {
        val games = if (epochDay >= BLOCK_SUDOKU_QUESTS_FROM_EPOCH_DAY) GAMES else GAMES_BEFORE_BLOCK_SUDOKU
        val first =
            when (pick(epochDay, 0, 2)) {
                0 -> DailyQuest(0, DailyQuestKind.PLAY, target = 3, gems = 1)
                else -> DailyQuest(0, DailyQuestKind.SOLVE, target = 2, gems = 1)
            }
        val gameChoice = pick(epochDay, 1, games.size + 1)
        val second =
            if (gameChoice < games.size) {
                DailyQuest(1, DailyQuestKind.SOLVE_GAME, target = 1, gems = 1, puzzleType = games[gameChoice])
            } else {
                DailyQuest(1, DailyQuestKind.SOLVE_DAILY, target = 1, gems = 1)
            }
        val third =
            when (pick(epochDay, 2, 3)) {
                0 -> DailyQuest(2, DailyQuestKind.SOLVE, target = 5, gems = 2)
                1 -> DailyQuest(2, DailyQuestKind.SOLVE_HARD, target = 1, gems = 2)
                else -> DailyQuest(2, DailyQuestKind.DIFFERENT_GAMES, target = 3, gems = 2)
            }
        return listOf(first, second, third)
    }

    /** A stable pseudo-random choice in `0 until bound` for one quest slot of one day. */
    private fun pick(
        epochDay: Long,
        slot: Int,
        bound: Int,
    ): Int {
        var mixed = epochDay * 0x9e3779b97f4a7c15uL.toLong() + (slot + 1) * 0xd1b54a32d192ed03uL.toLong()
        mixed = (mixed xor (mixed ushr 30)) * 0xbf58476d1ce4e5b9uL.toLong()
        mixed = (mixed xor (mixed ushr 27)) * 0x94d049bb133111ebuL.toLong()
        mixed = mixed xor (mixed ushr 31)
        return ((mixed ushr 1) % bound).toInt()
    }
}

/**
 * The daily login gift: one claim per local calendar day, a seven-day cycle of growing gem gifts.
 * Claiming on consecutive days walks the cycle, a missed day starts it over at day one, and the
 * day after the seventh begins a new cycle.
 */
object LoginGift {
    val GEMS: List<Int> = listOf(1, 1, 2, 1, 1, 2, 3)

    val CYCLE_DAYS: Int
        get() = GEMS.size

    fun gemsFor(streakDay: Int): Int = GEMS[(streakDay - 1).coerceIn(0, GEMS.size - 1)]

    /**
     * The cycle day [today]'s gift stands at, given the last claim ([lastClaimEpochDay] at cycle
     * day [lastStreakDay]); when today's gift is already claimed that is the claimed day itself.
     */
    fun streakDay(
        lastClaimEpochDay: Long?,
        lastStreakDay: Int,
        today: Long,
    ): Int =
        when (lastClaimEpochDay) {
            today -> lastStreakDay.coerceIn(1, CYCLE_DAYS)
            today - 1 -> if (lastStreakDay >= CYCLE_DAYS) 1 else lastStreakDay.coerceAtLeast(0) + 1
            else -> 1
        }
}
