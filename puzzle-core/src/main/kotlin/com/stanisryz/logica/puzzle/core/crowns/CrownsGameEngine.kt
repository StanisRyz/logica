package com.stanisryz.logica.puzzle.core.crowns

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

    fun start(): CrownsGameState =
        createState(
            board = CrownsState(),
            userMarks = emptySet(),
            pencilCrowns = emptySet(),
            pencilMarks = emptySet(),
            hintsUsed = 0,
            currentHint = null,
        )

    /**
     * Commits the value the player selected. Tapping a cell that already holds that same value
     * removes it, which is how a wrong value is taken back; a confirmed value cannot be changed.
     */
    fun placeValue(
        state: CrownsGameState,
        position: CrownsPosition,
        cell: CrownsPlayerCell,
    ): CrownsGameState {
        require(cell != CrownsPlayerCell.EMPTY) { "A committed placement needs a concrete value." }
        requireCompatible(state)
        CrownsBoardConstraints.requireInside(puzzle.size, position)
        if (state.isLocked(position)) return state

        val committed = if (state.cellAt(position) == cell) CrownsPlayerCell.EMPTY else cell
        val updated = applyCell(state.board, state.userMarks, position, committed)
        return createState(
            board = updated.board,
            userMarks = updated.userMarks,
            pencilCrowns = state.pencilCrowns - position,
            pencilMarks = state.pencilMarks - position,
            hintsUsed = state.hintsUsed,
            currentHint = null,
        )
    }

    /** Adds or removes one draft value. Pencil marks are never validated and never lock a cell. */
    fun togglePencilMark(
        state: CrownsGameState,
        position: CrownsPosition,
        cell: CrownsPlayerCell,
    ): CrownsGameState {
        require(cell != CrownsPlayerCell.EMPTY) { "A pencil mark needs a concrete value." }
        requireCompatible(state)
        CrownsBoardConstraints.requireInside(puzzle.size, position)
        if (state.isLocked(position)) return state
        // A cell holding a committed value is not a hypothesis any more.
        if (state.cellAt(position) != CrownsPlayerCell.EMPTY) return state

        val drafts = if (cell == CrownsPlayerCell.CROWN) state.pencilCrowns else state.pencilMarks
        val updated = if (position in drafts) drafts - position else drafts + position
        return createState(
            board = state.board,
            userMarks = state.userMarks,
            pencilCrowns = if (cell == CrownsPlayerCell.CROWN) updated else state.pencilCrowns,
            pencilMarks = if (cell == CrownsPlayerCell.CROWN) state.pencilMarks else updated,
            hintsUsed = state.hintsUsed,
            // The committed board is unchanged, so an open hint still describes this position correctly.
            currentHint = state.currentHint,
        )
    }

    fun reset(state: CrownsGameState): CrownsGameState {
        requireCompatible(state)
        if (!state.hasPlayerInput && state.currentHint == null) return state
        return createState(
            board = CrownsState(),
            userMarks = emptySet(),
            pencilCrowns = emptySet(),
            pencilMarks = emptySet(),
            hintsUsed = state.hintsUsed,
            currentHint = null,
        )
    }

    fun restore(
        board: CrownsState,
        userMarks: Set<CrownsPosition>,
        pencilCrowns: Set<CrownsPosition>,
        pencilMarks: Set<CrownsPosition>,
        hintsUsed: Int,
        currentHint: CrownsHint?,
    ): CrownsGameState {
        requirePositionsInside(board.crowns)
        requirePositionsInside(userMarks)
        requirePositionsInside(pencilCrowns)
        requirePositionsInside(pencilMarks)
        require(board.crowns.intersect(userMarks).isEmpty()) { "A cell cannot contain both a crown and a mark." }
        val committed = board.crowns + userMarks
        require((pencilCrowns + pencilMarks).none { it in committed }) {
            "A committed cell cannot also hold pencil marks."
        }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
        require(currentHint == null || hintsUsed > 0) { "A current hint requires positive hint usage." }
        require(currentHint == null || hintProvider.hint(puzzle, board, userMarks) == currentHint) {
            "Saved hint is not compatible with the saved gameplay state."
        }

        return createState(
            board = board,
            userMarks = userMarks,
            pencilCrowns = pencilCrowns,
            pencilMarks = pencilMarks,
            hintsUsed = hintsUsed,
            currentHint = currentHint,
        )
    }

    fun requestHint(state: CrownsGameState): CrownsGameState {
        requireCompatible(state)
        val hint = hintProvider.hint(puzzle, state.board, state.userMarks) ?: return state
        if (hint == state.currentHint) return state
        return createState(
            board = state.board,
            userMarks = state.userMarks,
            pencilCrowns = state.pencilCrowns,
            pencilMarks = state.pencilMarks,
            hintsUsed = state.hintsUsed + 1,
            currentHint = hint,
        )
    }

    private fun createState(
        board: CrownsState,
        userMarks: Set<CrownsPosition>,
        pencilCrowns: Set<CrownsPosition>,
        pencilMarks: Set<CrownsPosition>,
        hintsUsed: Int,
        currentHint: CrownsHint?,
    ): CrownsGameState {
        val analysis = CrownsRules.analyze(puzzle, board)
        val status =
            if (analysis.isComplete && analysis.violations.isEmpty()) {
                CrownsGameStatus.SOLVED
            } else {
                CrownsGameStatus.IN_PROGRESS
            }
        return CrownsGameState(
            puzzleId = puzzle.id,
            board = board,
            userMarks = userMarks,
            pencilCrowns = pencilCrowns,
            pencilMarks = pencilMarks,
            cellStatuses = cellStatuses(board, userMarks),
            status = status,
            hintsUsed = hintsUsed,
            currentHint = currentHint,
            violations = analysis.violations,
        )
    }

    private fun cellStatuses(
        board: CrownsState,
        userMarks: Set<CrownsPosition>,
    ): Map<CrownsPosition, CrownsCellStatus> {
        val answer = solution
        return buildMap {
            board.crowns.forEach { position ->
                put(position, statusOf(answer, isCrownExpected = true, position = position))
            }
            userMarks.forEach { position ->
                put(position, statusOf(answer, isCrownExpected = false, position = position))
            }
        }
    }

    private fun statusOf(
        answer: CrownsSolution?,
        isCrownExpected: Boolean,
        position: CrownsPosition,
    ): CrownsCellStatus =
        when {
            answer == null -> CrownsCellStatus.UNVERIFIED
            (position in answer.crowns) == isCrownExpected -> CrownsCellStatus.CORRECT
            else -> CrownsCellStatus.INCORRECT
        }

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
