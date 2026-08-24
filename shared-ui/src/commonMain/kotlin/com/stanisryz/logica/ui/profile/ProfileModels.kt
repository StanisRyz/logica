package com.stanisryz.logica.ui.profile

import com.stanisryz.logica.puzzle.core.model.Difficulty
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
    val economy: ProfileEconomyMetrics? = null,
) {
    init {
        require(totalSolved >= 0L && totalHintsUsed >= 0L && completedTerminalResults >= 0L)
    }

    fun toUiState(): ProfileUiState = if (completedTerminalResults == 0L) ProfileUiState.Empty else ProfileUiState.Ready(this)
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

data class DailyProfileMetrics(
    val completedCount: Long,
    val currentStreak: Long,
    val bestStreak: Long,
    val recentDays: List<DailyRecentDay> = emptyList(),
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
