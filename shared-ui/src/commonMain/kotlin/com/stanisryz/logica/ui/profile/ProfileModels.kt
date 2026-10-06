package com.stanisryz.logica.ui.profile

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleStars
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules

/** Platform-neutral state consumed by the shared Profile presentation. */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data object Error : ProfileUiState

    data object Empty : ProfileUiState

    data class Ready(
        val statistics: ProfileStatistics,
    ) : ProfileUiState
}

/** Lifetime gameplay statistics shaped for presentation rather than persistence. */
data class ProfileStatistics(
    val totalSolved: Long,
    val totalHintsUsed: Long,
    val completedTerminalResults: Long,
    val balance: SolvedPuzzleProfileStatistics,
    val crowns: SolvedPuzzleProfileStatistics,
    val sudoku: SudokuProfileStatistics,
    val game2048: Game2048ProfileStatistics,
    val word: WordProfileStatistics,
    val dailyMetrics: DailyProfileMetrics?,
    /** Nonogram shares Sudoku's shape: played, solved, failed, hints, and solves per difficulty. */
    val nonogram: SudokuProfileStatistics = SudokuProfileStatistics.EMPTY,
    /** Block Sudoku shares 2048's shape: played, solved (target reached), failed, per difficulty. */
    val blockSudoku: Game2048ProfileStatistics = Game2048ProfileStatistics.EMPTY,
    val economy: ProfileEconomyMetrics? = null,
    /** Best stars per Catalog level; null when the host keeps no star history. */
    val stars: ProfileStarSummary? = null,
) {
    init {
        require(totalSolved >= 0L && totalHintsUsed >= 0L && completedTerminalResults >= 0L)
    }

    fun toUiState(): ProfileUiState = if (completedTerminalResults == 0L) ProfileUiState.Empty else ProfileUiState.Ready(this)
}

/** The best stars one Catalog level has earned, as each host stores them. */
data class LevelStarRecord(
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val stars: Int,
    /** The Catalog level number, for views that show one level (0 when not known). */
    val level: Int = 0,
)

/**
 * Stars summed over Catalog levels, each level counting its best attempt once: per game and
 * difficulty, and how many levels earned all three.
 */
data class ProfileStarSummary(
    val byGame: Map<PuzzleType, ProfileDifficultyCounts>,
    val perfectLevels: Long,
) {
    val total: Long get() = byGame.values.sumOf { counts -> Difficulty.entries.sumOf { counts[it] } }

    fun forGame(puzzleType: PuzzleType): Long = byGame[puzzleType]?.let { counts -> Difficulty.entries.sumOf { counts[it] } } ?: 0L

    companion object {
        val EMPTY = ProfileStarSummary(emptyMap(), 0L)

        fun from(levels: Iterable<LevelStarRecord>): ProfileStarSummary {
            val sums = mutableMapOf<PuzzleType, MutableMap<Difficulty, Long>>()
            var perfect = 0L
            levels.forEach { level ->
                val byDifficulty = sums.getOrPut(level.puzzleType) { mutableMapOf() }
                byDifficulty[level.difficulty] = (byDifficulty[level.difficulty] ?: 0L) + level.stars
                if (level.stars >= PuzzleStars.MAX_STARS) perfect++
            }
            return ProfileStarSummary(
                byGame = sums.mapValues { (_, counts) -> ProfileDifficultyCounts.from(counts) },
                perfectLevels = perfect,
            )
        }
    }
}

/** Compact wallet display for the Profile; restore text is a pre-localized host string. */
data class ProfileEconomyMetrics(
    val gems: Long,
    val lives: Long,
    val maximumLives: Long,
    val restoreLabel: String? = null,
) {
    init {
        require(gems >= 0L && lives >= 0L && maximumLives > 0L && lives <= maximumLives)
    }
}

/**
 * One compact, spoiler-free durable Daily day for the optional recent-history rows. The date
 * label is host-formatted; only solved/required counts and full completion are exposed.
 */
data class DailyRecentDay(
    val dateLabel: String,
    val solvedCount: Int,
    val totalCount: Int,
    val fullyCompleted: Boolean,
) {
    init {
        require(solvedCount in 0..totalCount && totalCount > 0)
    }
}

/** How far one calendar date got: nothing, at least one solved entry, or the full Daily. */
enum class DailyCalendarDayState {
    NONE,
    PARTIAL,
    COMPLETED,

    /** A missed day saved for the streak only: it is no played or completed Daily. */
    STREAK_SAVED,
}

/**
 * One month of spoiler-free Daily history for the Profile calendar. [title] is host-formatted
 * ("Сентябрь 2026"); the grid itself is plain calendar arithmetic, Monday first.
 */
data class DailyCalendarMonth(
    val title: String,
    val year: Int,
    val month: Int,
    val today: Int?,
    val days: Map<Int, DailyCalendarDayState>,
) {
    init {
        require(month in 1..12)
    }

    val daysInMonth: Int
        get() =
            when (month) {
                2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }

    /** Blank cells before day 1 in a Monday-first week (0 for Monday .. 6 for Sunday). */
    val leadingBlankDays: Int
        get() {
            // Sakamoto's day-of-week (0 = Sunday), shifted so Monday is 0.
            val offsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
            val y = if (month < 3) year - 1 else year
            val sunday = (y + y / 4 - y / 100 + y / 400 + offsets[month - 1] + 1) % 7
            return (sunday + 6) % 7
        }
}

data class DailyProfileMetrics(
    val completedCount: Long,
    val currentStreak: Long,
    val bestStreak: Long,
    val recentDays: List<DailyRecentDay> = emptyList(),
    /** The current month of Daily history; when present it replaces the recent-days row. */
    val calendar: DailyCalendarMonth? = null,
) {
    init {
        require(completedCount >= 0L && currentStreak >= 0L && bestStreak >= 0L)
        require(recentDays.size <= MAXIMUM_RECENT_DAYS)
    }

    companion object {
        /** The recent-history block stays compact; hosts show at most a few last durable days. */
        const val MAXIMUM_RECENT_DAYS = 5
    }
}

/** Balance/Crowns intentionally expose only solved metrics in the current shared design. */
data class SolvedPuzzleProfileStatistics(
    val totalSolved: Long,
    val solvedByDifficulty: ProfileDifficultyCounts,
) {
    init {
        require(totalSolved >= 0L)
    }
}

data class SudokuProfileStatistics(
    val played: Long,
    val solved: Long,
    val failed: Long,
    val hintsUsed: Long,
    val solvedByDifficulty: ProfileDifficultyCounts,
) {
    init {
        require(played >= 0L && solved >= 0L && failed >= 0L && hintsUsed >= 0L)
    }

    companion object {
        val EMPTY = SudokuProfileStatistics(0L, 0L, 0L, 0L, ProfileDifficultyCounts(0L, 0L, 0L, 0L))
    }
}

data class Game2048ProfileStatistics(
    val played: Long,
    val solved: Long,
    val failed: Long,
    val solvedByDifficulty: ProfileDifficultyCounts,
) {
    init {
        require(played >= 0L && solved >= 0L && failed >= 0L)
    }

    companion object {
        val EMPTY = Game2048ProfileStatistics(0L, 0L, 0L, ProfileDifficultyCounts(0L, 0L, 0L, 0L))
    }
}

data class WordProfileStatistics(
    val played: Long,
    val solved: Long,
    val failed: Long,
    val solvedAttemptDistribution: ProfileAttemptDistribution,
) {
    init {
        require(played >= 0L && solved >= 0L && failed >= 0L)
    }

    val winRatePercent: Long
        get() =
            when {
                played == 0L -> 0L
                solved <= Long.MAX_VALUE / 100L -> solved * 100L / played
                else -> ((solved.toDouble() / played.toDouble()) * 100.0).toLong()
            }
}

/** An explicit four-value shape keeps incomplete platform maps out of shared presentation. */
data class ProfileDifficultyCounts(
    val easy: Long,
    val medium: Long,
    val hard: Long,
    val expert: Long,
) {
    init {
        require(easy >= 0L && medium >= 0L && hard >= 0L && expert >= 0L)
    }

    operator fun get(difficulty: Difficulty): Long =
        when (difficulty) {
            Difficulty.EASY -> easy
            Difficulty.MEDIUM -> medium
            Difficulty.HARD -> hard
            Difficulty.EXPERT -> expert
        }

    companion object {
        fun from(source: Map<Difficulty, Long>): ProfileDifficultyCounts =
            ProfileDifficultyCounts(
                easy = source[Difficulty.EASY] ?: 0L,
                medium = source[Difficulty.MEDIUM] ?: 0L,
                hard = source[Difficulty.HARD] ?: 0L,
                expert = source[Difficulty.EXPERT] ?: 0L,
            )
    }
}

/** Counts are always normalized to attempts 1 through the current Word maximum. */
data class ProfileAttemptDistribution(
    val counts: List<Long>,
) {
    init {
        require(counts.size == WordRules.MAXIMUM_ATTEMPTS)
        require(counts.all { it >= 0L })
    }

    operator fun get(attempt: Int): Long {
        require(attempt in 1..WordRules.MAXIMUM_ATTEMPTS)
        return counts[attempt - 1]
    }

    companion object {
        fun from(source: Map<Int, Long>): ProfileAttemptDistribution =
            ProfileAttemptDistribution(
                (1..WordRules.MAXIMUM_ATTEMPTS).map { source[it] ?: 0L },
            )
    }
}
