package com.stanisryz.logica.puzzle.core.balance

enum class BalanceGameStatus {
    IN_PROGRESS,
    SOLVED,
}

data class BalanceMove(
    val position: BalancePosition,
    val previousValue: BalanceCell,
    val newValue: BalanceCell,
) {
    init {
        require(previousValue != newValue) { "A move must change the cell value." }
    }
}

class BalanceGameState internal constructor(
    val board: BalanceState,
    val status: BalanceGameStatus,
    moveHistory: Iterable<BalanceMove>,
    val hintsUsed: Int,
    val currentHint: BalanceHint?,
    violations: Iterable<BalanceViolation>,
) {
    val moveHistory: List<BalanceMove> = moveHistory.toList()
    val violations: List<BalanceViolation> = violations.toList()

    init {
        require(hintsUsed >= 0) { "Hints used must not be negative." }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is BalanceGameState &&
            board == other.board &&
            status == other.status &&
            moveHistory == other.moveHistory &&
            hintsUsed == other.hintsUsed &&
            currentHint == other.currentHint &&
            violations == other.violations

    override fun hashCode(): Int {
        var result = board.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + moveHistory.hashCode()
        result = 31 * result + hintsUsed
        result = 31 * result + (currentHint?.hashCode() ?: 0)
        result = 31 * result + violations.hashCode()
        return result
    }

    override fun toString(): String =
        "BalanceGameState(board=$board, status=$status, moveHistory=$moveHistory, " +
            "hintsUsed=$hintsUsed, currentHint=$currentHint, violations=$violations)"
}
