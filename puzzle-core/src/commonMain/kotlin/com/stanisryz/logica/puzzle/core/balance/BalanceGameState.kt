package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes

enum class BalanceGameStatus {
    IN_PROGRESS,
    SOLVED,
    FAILED,
    ;

    val isTerminal: Boolean get() = this != IN_PROGRESS
}

/**
 * How one cell of the board stands right now. A committed value is checked against the puzzle's own
 * answer as soon as it is placed: a correct value is final, a wrong one stays on the board until the
 * player fixes it. `UNVERIFIED` covers the rare board that has no single answer to check against.
 */
enum class BalanceCellStatus {
    EMPTY,
    FIXED,
    CORRECT,
    INCORRECT,
    UNVERIFIED,
}

class BalanceGameState internal constructor(
    val board: BalanceState,
    val status: BalanceGameStatus,
    pencilMarks: Map<BalancePosition, Set<BalanceCell>>,
    cellStatuses: Map<BalancePosition, BalanceCellStatus>,
    /** How many incorrect values this attempt has committed, counted as events rather than red cells. */
    val mistakesUsed: Int,
    val hintsUsed: Int,
    val currentHint: BalanceHint?,
    violations: Iterable<BalanceViolation>,
) {
    /** Unvalidated player hypotheses; a cell keeps an entry only while it holds at least one mark. */
    val pencilMarks: Map<BalancePosition, Set<BalanceCell>> = pencilMarks.mapValues { (_, marks) -> marks.toSet() }
    val cellStatuses: Map<BalancePosition, BalanceCellStatus> = cellStatuses.toMap()
    val violations: List<BalanceViolation> = violations.toList()

    init {
        require(hintsUsed >= 0) { "Hints used must not be negative." }
        require(mistakesUsed in 0..PuzzleMistakes.MAX_MISTAKES) {
            "Mistakes used must be within 0..${PuzzleMistakes.MAX_MISTAKES}."
        }
        require(this.pencilMarks.values.all { marks -> marks.isNotEmpty() && BalanceCell.EMPTY !in marks }) {
            "A pencil mark must be a concrete value."
        }
        require(this.cellStatuses.values.none { it == BalanceCellStatus.EMPTY }) {
            "Empty cells are not listed among the cell statuses."
        }
    }

    fun statusAt(position: BalancePosition): BalanceCellStatus = cellStatuses[position] ?: BalanceCellStatus.EMPTY

    /** A confirmed value is final: neither a committed placement nor a pencil mark may touch it. */
    fun isLocked(position: BalancePosition): Boolean =
        when (statusAt(position)) {
            BalanceCellStatus.FIXED, BalanceCellStatus.CORRECT -> true
            else -> false
        }

    fun pencilMarksAt(position: BalancePosition): Set<BalanceCell> = pencilMarks[position].orEmpty()

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is BalanceGameState &&
            board == other.board &&
            status == other.status &&
            pencilMarks == other.pencilMarks &&
            cellStatuses == other.cellStatuses &&
            mistakesUsed == other.mistakesUsed &&
            hintsUsed == other.hintsUsed &&
            currentHint == other.currentHint &&
            violations == other.violations

    override fun hashCode(): Int {
        var result = board.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + pencilMarks.hashCode()
        result = 31 * result + cellStatuses.hashCode()
        result = 31 * result + mistakesUsed
        result = 31 * result + hintsUsed
        result = 31 * result + (currentHint?.hashCode() ?: 0)
        result = 31 * result + violations.hashCode()
        return result
    }

    override fun toString(): String =
        "BalanceGameState(board=$board, status=$status, pencilMarks=$pencilMarks, " +
            "cellStatuses=$cellStatuses, mistakesUsed=$mistakesUsed, hintsUsed=$hintsUsed, " +
            "currentHint=$currentHint, violations=$violations)"
}
