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
) {
    init {
        require(totalSolved >= 0L && totalHintsUsed >= 0L && completedTerminalResults >= 0L)
    }

    fun toUiState(): ProfileUiState = if (completedTerminalResults == 0L) ProfileUiState.Empty else ProfileUiState.Ready(this)
}

data class DailyProfileMetrics(
    val completedCount: Long,
    val currentStreak: Long,
    val bestStreak: Long,
) {
    init {
        require(completedCount >= 0L && currentStreak >= 0L && bestStreak >= 0L)
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
