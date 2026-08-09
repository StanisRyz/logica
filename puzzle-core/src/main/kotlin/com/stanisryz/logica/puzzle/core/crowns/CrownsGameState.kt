package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.model.PuzzleId

enum class CrownsPlayerCell {
    EMPTY,
    CROWN,
    MARKED,
}

enum class CrownsGameStatus {
    IN_PROGRESS,
    SOLVED,
}

data class CrownsMove(
    val position: CrownsPosition,
    val previousCell: CrownsPlayerCell,
    val newCell: CrownsPlayerCell,
) {
    init {
        require(previousCell != newCell) { "A move must change the player cell." }
    }
}

class CrownsGameState internal constructor(
    val puzzleId: PuzzleId,
    val board: CrownsState,
    userMarks: Iterable<CrownsPosition>,
    val status: CrownsGameStatus,
    moveHistory: Iterable<CrownsMove>,
    val hintsUsed: Int,
    val currentHint: CrownsHint?,
    violations: Iterable<CrownsViolation>,
) {
    val userMarks: Set<CrownsPosition> = userMarks.toSet()
    val moveHistory: List<CrownsMove> = moveHistory.toList()
    val violations: List<CrownsViolation> = violations.toList()

    init {
        require(board.crowns.intersect(this.userMarks).isEmpty()) { "A cell cannot contain both a crown and a mark." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
    }

    fun cellAt(position: CrownsPosition): CrownsPlayerCell =
        when (position) {
            in board.crowns -> CrownsPlayerCell.CROWN
            in userMarks -> CrownsPlayerCell.MARKED
            else -> CrownsPlayerCell.EMPTY
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is CrownsGameState &&
            puzzleId == other.puzzleId &&
            board == other.board &&
            userMarks == other.userMarks &&
            status == other.status &&
            moveHistory == other.moveHistory &&
            hintsUsed == other.hintsUsed &&
            currentHint == other.currentHint &&
            violations == other.violations

    override fun hashCode(): Int {
        var result = puzzleId.hashCode()
        result = 31 * result + board.hashCode()
        result = 31 * result + userMarks.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + moveHistory.hashCode()
        result = 31 * result + hintsUsed
        result = 31 * result + (currentHint?.hashCode() ?: 0)
        result = 31 * result + violations.hashCode()
        return result
    }

    override fun toString(): String =
        "CrownsGameState(puzzleId=$puzzleId, board=$board, userMarks=$userMarks, status=$status, " +
            "moveHistory=$moveHistory, hintsUsed=$hintsUsed, currentHint=$currentHint, violations=$violations)"
}
