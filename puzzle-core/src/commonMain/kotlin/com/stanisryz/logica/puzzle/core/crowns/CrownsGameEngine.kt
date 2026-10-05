package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes

class CrownsGameEngine(
    private val puzzle: CrownsPuzzle,
    private val hintProvider: CrownsHintProvider = CrownsHintProvider(),
    private val solver: CrownsSolver = CrownsSolver(),
) {
    /**
     * The answer committed placements are checked against. It is resolved once, and only for a
     * puzzle that really has a single answer, so an ambiguous board leaves every value unverified
     * instead of calling a legitimate alternative wrong.
     */
    private val solution: CrownsSolution? by lazy {
        if (solver.countSolutions(puzzle, limit = 2) != 1) null else solver.solve(puzzle)
    }

    /** A fresh attempt: an empty board, no marks, no pencil crowns, no mistakes. */
    fun start(): CrownsGameState =
        createState(
            board = CrownsState(),
            userMarks = emptySet(),
            pencilCrowns = emptySet(),
            mistakesUsed = 0,
            hintsUsed = 0,
            currentHint = null,
        )

    /**
     * Commits the value the player selected. Tapping a cell that already holds that same value
     * removes it. Only a crown is checked: a correct crown is final, and every newly committed wrong
     * crown costs one mistake, the third ending the attempt. A mark is the player's own note — never
     * checked, never locked, never a mistake — and a crown placed on a marked cell replaces the mark.
     */
    fun placeValue(
        state: CrownsGameState,
        position: CrownsPosition,
        cell: CrownsPlayerCell,
    ): CrownsGameState {
        require(cell != CrownsPlayerCell.EMPTY) { "A committed placement needs a concrete value." }
        requireCompatible(state)
        CrownsBoardConstraints.requireInside(puzzle.size, position)
        if (state.status.isTerminal) return state
        if (state.isLocked(position)) return state

        val committed = if (state.cellAt(position) == cell) CrownsPlayerCell.EMPTY else cell
        // Removing a wrong crown never refunds the mistake it already cost; a mark costs nothing.
        val isNewMistake = committed == CrownsPlayerCell.CROWN && isIncorrectCrown(position)
        val updated = applyCell(state.board, state.userMarks, position, committed)
        return createState(
            board = updated.board,
            userMarks = updated.userMarks,
            pencilCrowns = state.pencilCrowns - position,
            mistakesUsed = if (isNewMistake) state.mistakesUsed + 1 else state.mistakesUsed,
            hintsUsed = state.hintsUsed,
            currentHint = null,
        )
    }

    /**
     * Adds or removes one draft value in pencil mode. A pencil crown is an unchecked hypothesis; a
     * mark is already an unchecked note, so in pencil mode it toggles the very same mark as outside it
     * (it never replaces a crown there).
     */
    fun togglePencilMark(
        state: CrownsGameState,
        position: CrownsPosition,
        cell: CrownsPlayerCell,
    ): CrownsGameState {
        require(cell != CrownsPlayerCell.EMPTY) { "A pencil mark needs a concrete value." }
        requireCompatible(state)
        CrownsBoardConstraints.requireInside(puzzle.size, position)
        if (state.status.isTerminal) return state
        if (state.isLocked(position)) return state
        if (cell == CrownsPlayerCell.MARKED) {
            if (state.cellAt(position) == CrownsPlayerCell.CROWN) return state
            return placeValue(state, position, CrownsPlayerCell.MARKED)
        }
        // A crown or a mark already on the cell leaves no room for a crown hypothesis.
        if (state.cellAt(position) != CrownsPlayerCell.EMPTY) return state

        val drafts = state.pencilCrowns
        return createState(
            board = state.board,
            userMarks = state.userMarks,
            pencilCrowns = if (position in drafts) drafts - position else drafts + position,
            mistakesUsed = state.mistakesUsed,
            hintsUsed = state.hintsUsed,
            // The committed board is unchanged, so an open hint still describes this position correctly.
            currentHint = state.currentHint,
        )
    }

    fun restore(
        board: CrownsState,
        userMarks: Set<CrownsPosition>,
        pencilCrowns: Set<CrownsPosition>,
        mistakesUsed: Int,
        hintsUsed: Int,
        currentHint: CrownsHint?,
    ): CrownsGameState {
        requirePositionsInside(board.crowns)
        requirePositionsInside(userMarks)
        requirePositionsInside(pencilCrowns)
        require(board.crowns.intersect(userMarks).isEmpty()) { "A cell cannot contain both a crown and a mark." }
        val occupied = board.crowns + userMarks
        require(pencilCrowns.none { it in occupied }) { "A crown or marked cell cannot also hold a pencil crown." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
        require(mistakesUsed in 0..PuzzleMistakes.MAX_MISTAKES) { "Saved mistakes are out of range." }
        require(currentHint == null || hintsUsed > 0) { "A current hint requires positive hint usage." }
        require(currentHint == null || logicalHint(board) == currentHint) {
            "Saved hint is not compatible with the saved gameplay state."
        }

        return createState(
            board = board,
            userMarks = userMarks,
            pencilCrowns = pencilCrowns,
            mistakesUsed = mistakesUsed,
            hintsUsed = hintsUsed,
            currentHint = currentHint,
        )
    }

    /** The next logical step, read from the crowns alone: the player's marks are notes no hint reads. */
    fun requestHint(state: CrownsGameState): CrownsGameState {
        requireCompatible(state)
        if (state.status.isTerminal) return state
        val hint = logicalHint(state.board) ?: return state
        if (hint == state.currentHint) return state
        return createState(
            board = state.board,
            userMarks = state.userMarks,
            pencilCrowns = state.pencilCrowns,
            mistakesUsed = state.mistakesUsed,
            hintsUsed = state.hintsUsed + 1,
            currentHint = hint,
        )
    }

    /**
     * Carries a hint out instead of explaining it: a wrong crown is taken away, otherwise the next
     * crown of the single answer is placed, preferring the one the logical hint points at; a mark on
     * that cell simply gives way to the crown. Marks are never read, judged, or opened by a hint. The
     * reveal counts as one used hint, never as a mistake. A puzzle without a unique answer, or a
     * finished board, changes nothing.
     */
    fun revealHint(state: CrownsGameState): CrownsGameState {
        requireCompatible(state)
        if (state.status.isTerminal) return state
        val answer = solution ?: return state
        val wrongCrown =
            state.board.crowns
                .filter { it !in answer.crowns }
                .minWithOrNull(POSITION_ORDER)
        val (position, cell) =
            when {
                wrongCrown != null -> wrongCrown to CrownsPlayerCell.EMPTY
                else -> {
                    val missing = answer.crowns - state.board.crowns
                    val logical =
                        logicalHint(state.board)
                            ?.takeIf { it.action == CrownsHintAction.PLACE_CROWN }
                            ?.targetPositions
                            ?.firstOrNull { it in missing }
                    (logical ?: missing.minWithOrNull(POSITION_ORDER) ?: return state) to CrownsPlayerCell.CROWN
                }
            }
        val updated = applyCell(state.board, state.userMarks, position, cell)
        return createState(
            board = updated.board,
            userMarks = updated.userMarks,
            pencilCrowns = state.pencilCrowns - position,
            mistakesUsed = state.mistakesUsed,
            hintsUsed = state.hintsUsed + 1,
            currentHint = null,
        )
    }

    /**
     * The one second chance of an attempt that the third mistake ended: the board stays exactly as
     * it was and the attempt goes on with one mistake left. Anything but a failed attempt is
     * returned unchanged; how often it may be offered is the host's policy.
     */
    fun continueAfterFailure(state: CrownsGameState): CrownsGameState =
        if (state.status != CrownsGameStatus.FAILED) {
            state
        } else {
            createState(
                state.board,
                state.userMarks,
                state.pencilCrowns,
                PuzzleMistakes.MAX_MISTAKES - 1,
                state.hintsUsed,
                currentHint = null,
            )
        }

    private fun createState(
        board: CrownsState,
        userMarks: Set<CrownsPosition>,
        pencilCrowns: Set<CrownsPosition>,
        mistakesUsed: Int,
        hintsUsed: Int,
        currentHint: CrownsHint?,
    ): CrownsGameState {
        // Solved means every crown is in place: marks play no part in it.
        val analysis = CrownsRules.analyze(puzzle, board)
        val status =
            when {
                mistakesUsed >= PuzzleMistakes.MAX_MISTAKES -> CrownsGameStatus.FAILED
                analysis.isComplete && analysis.violations.isEmpty() -> CrownsGameStatus.SOLVED
                else -> CrownsGameStatus.IN_PROGRESS
            }
        return CrownsGameState(
            puzzleId = puzzle.id,
            board = board,
            userMarks = userMarks,
            pencilCrowns = pencilCrowns,
            cellStatuses = cellStatuses(board),
            status = status,
            mistakesUsed = mistakesUsed,
            hintsUsed = hintsUsed,
            currentHint = currentHint,
            violations = analysis.violations,
        )
    }

    private fun isIncorrectCrown(position: CrownsPosition): Boolean = crownStatus(solution, position) == CrownsCellStatus.INCORRECT

    /** Only crowns carry a status; marks are unchecked notes and stay out of it. */
    private fun cellStatuses(board: CrownsState): Map<CrownsPosition, CrownsCellStatus> {
        val answer = solution
        return board.crowns.associateWith { position -> crownStatus(answer, position) }
    }

    private fun crownStatus(
        answer: CrownsSolution?,
        position: CrownsPosition,
    ): CrownsCellStatus =
        when {
            answer == null -> CrownsCellStatus.UNVERIFIED
            position in answer.crowns -> CrownsCellStatus.CORRECT
            else -> CrownsCellStatus.INCORRECT
        }

    private fun logicalHint(board: CrownsState): CrownsHint? = hintProvider.hint(puzzle, board)

    private fun applyCell(
        board: CrownsState,
        userMarks: Set<CrownsPosition>,
        position: CrownsPosition,
        cell: CrownsPlayerCell,
    ): PlayerBoard {
        val crowns = board.crowns - position
        val marks = userMarks - position
        return when (cell) {
            CrownsPlayerCell.EMPTY -> PlayerBoard(CrownsState(crowns), marks)
            CrownsPlayerCell.CROWN -> PlayerBoard(CrownsState(crowns + position), marks)
            CrownsPlayerCell.MARKED -> PlayerBoard(CrownsState(crowns), marks + position)
        }
    }

    private fun requireCompatible(state: CrownsGameState) {
        require(state.puzzleId == puzzle.id) { "Game state belongs to a different puzzle." }
        requirePositionsInside(state.board.crowns)
        requirePositionsInside(state.userMarks)
        val overlappingPositions = state.board.crowns.intersect(state.userMarks)
        require(overlappingPositions.isEmpty()) {
            "A cell cannot contain both a crown and a mark."
        }
    }

    private fun requirePositionsInside(positions: Iterable<CrownsPosition>) {
        positions.forEach { CrownsBoardConstraints.requireInside(puzzle.size, it) }
    }

    private data class PlayerBoard(
        val board: CrownsState,
        val userMarks: Set<CrownsPosition>,
    )
}

private val POSITION_ORDER: Comparator<CrownsPosition> = compareBy({ it.row }, { it.column })
