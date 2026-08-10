package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.model.PuzzleId

enum class CrownsPlayerCell {
    EMPTY,
    CROWN,
    MARKED,
}

/**
 * How one cell of the board stands right now. A committed crown or blocked mark is checked against
 * the puzzle's own answer as soon as it is placed: a correct value is final, a wrong one stays on
 * the board until the player fixes it. `UNVERIFIED` covers a board with no single answer to check.
 */
enum class CrownsCellStatus {
    EMPTY,
    CORRECT,
    INCORRECT,
    UNVERIFIED,
}

enum class CrownsGameStatus {
    IN_PROGRESS,
    SOLVED,
}

class CrownsGameState internal constructor(
    val puzzleId: PuzzleId,
    val board: CrownsState,
    userMarks: Iterable<CrownsPosition>,
    pencilCrowns: Iterable<CrownsPosition>,
    pencilMarks: Iterable<CrownsPosition>,
    cellStatuses: Map<CrownsPosition, CrownsCellStatus>,
    val status: CrownsGameStatus,
    val hintsUsed: Int,
    val currentHint: CrownsHint?,
    violations: Iterable<CrownsViolation>,
) {
    /** Committed blocked marks; crowns live in [board]. */
    val userMarks: Set<CrownsPosition> = userMarks.toSet()

    /** Unvalidated player hypotheses, kept apart from the committed values. */
    val pencilCrowns: Set<CrownsPosition> = pencilCrowns.toSet()
    val pencilMarks: Set<CrownsPosition> = pencilMarks.toSet()
    val cellStatuses: Map<CrownsPosition, CrownsCellStatus> = cellStatuses.toMap()
    val violations: List<CrownsViolation> = violations.toList()

    init {
        require(board.crowns.intersect(this.userMarks).isEmpty()) { "A cell cannot contain both a crown and a mark." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
        val committed = board.crowns + this.userMarks
        require((this.pencilCrowns + this.pencilMarks).none { it in committed }) {
            "A committed cell cannot also hold pencil marks."
        }
        require(this.cellStatuses.values.none { it == CrownsCellStatus.EMPTY }) {
            "Empty cells are not listed among the cell statuses."
        }
    }

    val hasPlayerInput: Boolean
        get() = board.crowns.isNotEmpty() || userMarks.isNotEmpty() || pencilCrowns.isNotEmpty() || pencilMarks.isNotEmpty()

    fun cellAt(position: CrownsPosition): CrownsPlayerCell =
        when (position) {
            in board.crowns -> CrownsPlayerCell.CROWN
            in userMarks -> CrownsPlayerCell.MARKED
            else -> CrownsPlayerCell.EMPTY
        }

    fun statusAt(position: CrownsPosition): CrownsCellStatus = cellStatuses[position] ?: CrownsCellStatus.EMPTY

    /** A confirmed value is final: neither a committed placement nor a pencil mark may touch it. */
    fun isLocked(position: CrownsPosition): Boolean = statusAt(position) == CrownsCellStatus.CORRECT

    fun pencilAt(position: CrownsPosition): Set<CrownsPlayerCell> =
        buildSet {
            if (position in pencilCrowns) add(CrownsPlayerCell.CROWN)
            if (position in pencilMarks) add(CrownsPlayerCell.MARKED)
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is CrownsGameState &&
            puzzleId == other.puzzleId &&
            board == other.board &&
            userMarks == other.userMarks &&
            pencilCrowns == other.pencilCrowns &&
            pencilMarks == other.pencilMarks &&
            cellStatuses == other.cellStatuses &&
            status == other.status &&
            hintsUsed == other.hintsUsed &&
            currentHint == other.currentHint &&
            violations == other.violations

    override fun hashCode(): Int {
        var result = puzzleId.hashCode()
        result = 31 * result + board.hashCode()
        result = 31 * result + userMarks.hashCode()
        result = 31 * result + pencilCrowns.hashCode()
        result = 31 * result + pencilMarks.hashCode()
        result = 31 * result + cellStatuses.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + hintsUsed
        result = 31 * result + (currentHint?.hashCode() ?: 0)
        result = 31 * result + violations.hashCode()
        return result
    }

    override fun toString(): String =
        "CrownsGameState(puzzleId=$puzzleId, board=$board, userMarks=$userMarks, pencilCrowns=$pencilCrowns, " +
            "pencilMarks=$pencilMarks, cellStatuses=$cellStatuses, status=$status, hintsUsed=$hintsUsed, " +
            "currentHint=$currentHint, violations=$violations)"
}
