package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes

enum class CrownsPlayerCell {
    EMPTY,
    CROWN,
    MARKED,
}

/**
 * How one crown on the board stands right now. A committed crown is checked against the puzzle's own
 * answer as soon as it is placed: a correct crown is final, a wrong one stays on the board until the
 * player fixes it. `UNVERIFIED` covers a board with no single answer to check. Marks are unchecked
 * notes and never have a status.
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
    FAILED,
    ;

    val isTerminal: Boolean get() = this != IN_PROGRESS
}

class CrownsGameState internal constructor(
    val puzzleId: PuzzleId,
    val board: CrownsState,
    userMarks: Iterable<CrownsPosition>,
    pencilCrowns: Iterable<CrownsPosition>,
    cellStatuses: Map<CrownsPosition, CrownsCellStatus>,
    val status: CrownsGameStatus,
    /** How many incorrect values this attempt has committed, counted as events rather than red cells. */
    val mistakesUsed: Int,
    val hintsUsed: Int,
    val currentHint: CrownsHint?,
    violations: Iterable<CrownsViolation>,
) {
    /** The player's X marks: unchecked notes, never locked and never a mistake. Crowns live in [board]. */
    val userMarks: Set<CrownsPosition> = userMarks.toSet()

    /** Unchecked crown hypotheses drawn small in the corner, kept apart from the committed crowns. */
    val pencilCrowns: Set<CrownsPosition> = pencilCrowns.toSet()
    val cellStatuses: Map<CrownsPosition, CrownsCellStatus> = cellStatuses.toMap()
    val violations: List<CrownsViolation> = violations.toList()

    init {
        require(board.crowns.intersect(this.userMarks).isEmpty()) { "A cell cannot contain both a crown and a mark." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
        require(mistakesUsed in 0..PuzzleMistakes.MAX_MISTAKES) {
            "Mistakes used must be within 0..${PuzzleMistakes.MAX_MISTAKES}."
        }
        val occupied = board.crowns + this.userMarks
        require(this.pencilCrowns.none { it in occupied }) { "A crown or marked cell cannot also hold a pencil crown." }
        require(this.cellStatuses.values.none { it == CrownsCellStatus.EMPTY }) {
            "Empty cells are not listed among the cell statuses."
        }
    }

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
        if (position in pencilCrowns) setOf(CrownsPlayerCell.CROWN) else emptySet()

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is CrownsGameState &&
            puzzleId == other.puzzleId &&
            board == other.board &&
            userMarks == other.userMarks &&
            pencilCrowns == other.pencilCrowns &&
            cellStatuses == other.cellStatuses &&
            status == other.status &&
            mistakesUsed == other.mistakesUsed &&
            hintsUsed == other.hintsUsed &&
            currentHint == other.currentHint &&
            violations == other.violations

    override fun hashCode(): Int {
        var result = puzzleId.hashCode()
        result = 31 * result + board.hashCode()
        result = 31 * result + userMarks.hashCode()
        result = 31 * result + pencilCrowns.hashCode()
        result = 31 * result + cellStatuses.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + mistakesUsed
        result = 31 * result + hintsUsed
        result = 31 * result + (currentHint?.hashCode() ?: 0)
        result = 31 * result + violations.hashCode()
        return result
    }

    override fun toString(): String =
        "CrownsGameState(puzzleId=$puzzleId, board=$board, userMarks=$userMarks, pencilCrowns=$pencilCrowns, " +
            "cellStatuses=$cellStatuses, status=$status, " +
            "mistakesUsed=$mistakesUsed, hintsUsed=$hintsUsed, currentHint=$currentHint, violations=$violations)"
}

/**
 * Whether leaving this unfinished attempt throws away something the player did — a crown (correct or
 * wrong), a pencil crown, a mistake, or a hint — so leaving costs a life. X marks are notes and do not
 * count. Both hosts ask only this.
 */
val CrownsGameState.hasMeaningfulProgress: Boolean
    get() =
        !status.isTerminal &&
            (
                board.crowns.isNotEmpty() ||
                    pencilCrowns.isNotEmpty() ||
                    mistakesUsed > 0 ||
                    hintsUsed > 0
            )
