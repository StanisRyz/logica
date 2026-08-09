package com.stanisryz.logica.puzzle.core.crowns

class CrownsGameEngine(
    private val puzzle: CrownsPuzzle,
    private val hintProvider: CrownsHintProvider = CrownsHintProvider(),
) {
    fun start(): CrownsGameState =
        createState(
            board = CrownsState(),
            userMarks = emptySet(),
            moveHistory = emptyList(),
            hintsUsed = 0,
            currentHint = null,
        )

    fun placeCrown(
        state: CrownsGameState,
        position: CrownsPosition,
    ): CrownsGameState = setCell(state, position, CrownsPlayerCell.CROWN)

    fun placeMark(
        state: CrownsGameState,
        position: CrownsPosition,
    ): CrownsGameState = setCell(state, position, CrownsPlayerCell.MARKED)

    fun clearCell(
        state: CrownsGameState,
        position: CrownsPosition,
    ): CrownsGameState = setCell(state, position, CrownsPlayerCell.EMPTY)

    fun cycleCell(
        state: CrownsGameState,
        position: CrownsPosition,
    ): CrownsGameState {
        requireCompatible(state)
        val nextCell =
            when (state.cellAt(position)) {
                CrownsPlayerCell.EMPTY -> CrownsPlayerCell.CROWN
                CrownsPlayerCell.CROWN -> CrownsPlayerCell.MARKED
                CrownsPlayerCell.MARKED -> CrownsPlayerCell.EMPTY
            }
        return setCell(state, position, nextCell)
    }

    fun undo(state: CrownsGameState): CrownsGameState {
        requireCompatible(state)
        val move = state.moveHistory.lastOrNull() ?: return state
        require(state.cellAt(move.position) == move.newCell) { "Move history is not compatible with the game state." }
        val restored = applyCell(state.board, state.userMarks, move.position, move.previousCell)
        return createState(
            board = restored.board,
            userMarks = restored.userMarks,
            moveHistory = state.moveHistory.dropLast(1),
            hintsUsed = state.hintsUsed,
            currentHint = null,
        )
    }

    fun reset(state: CrownsGameState): CrownsGameState {
        requireCompatible(state)
        if (
            state.board.crowns.isEmpty() &&
            state.userMarks.isEmpty() &&
            state.moveHistory.isEmpty() &&
            state.currentHint == null
        ) {
            return state
        }
        return createState(
            board = CrownsState(),
            userMarks = emptySet(),
            moveHistory = emptyList(),
            hintsUsed = state.hintsUsed,
            currentHint = null,
        )
    }

    fun restore(
        board: CrownsState,
        userMarks: Set<CrownsPosition>,
        moveHistory: List<CrownsMove>,
        hintsUsed: Int,
        currentHint: CrownsHint?,
    ): CrownsGameState {
        requirePositionsInside(board.crowns)
        requirePositionsInside(userMarks)
        require(board.crowns.intersect(userMarks).isEmpty()) { "A cell cannot contain both a crown and a mark." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }

        var replayed = PlayerBoard(CrownsState(), emptySet())
        moveHistory.forEach { move ->
            CrownsBoardConstraints.requireInside(puzzle.size, move.position)
            require(playerCellAt(replayed.board, replayed.userMarks, move.position) == move.previousCell) {
                "Move history is not compatible with the saved board."
            }
            replayed = applyCell(replayed.board, replayed.userMarks, move.position, move.newCell)
        }
        require(replayed.board == board && replayed.userMarks == userMarks) {
            "Move history does not reconstruct the saved crowns and marks."
        }
        require(currentHint == null || hintsUsed > 0) { "A current hint requires positive hint usage." }
        require(currentHint == null || hintProvider.hint(puzzle, board, userMarks) == currentHint) {
            "Saved hint is not compatible with the saved gameplay state."
        }

        return createState(
            board = board,
            userMarks = userMarks,
            moveHistory = moveHistory,
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
            moveHistory = state.moveHistory,
            hintsUsed = state.hintsUsed + 1,
            currentHint = hint,
        )
    }

    private fun setCell(
        state: CrownsGameState,
        position: CrownsPosition,
        cell: CrownsPlayerCell,
    ): CrownsGameState {
        requireCompatible(state)
        CrownsBoardConstraints.requireInside(puzzle.size, position)
        val previousCell = state.cellAt(position)
        if (previousCell == cell) return state

        val updated = applyCell(state.board, state.userMarks, position, cell)
        return createState(
            board = updated.board,
            userMarks = updated.userMarks,
            moveHistory = state.moveHistory + CrownsMove(position, previousCell, cell),
            hintsUsed = state.hintsUsed,
            currentHint = null,
        )
    }

    private fun createState(
        board: CrownsState,
        userMarks: Set<CrownsPosition>,
        moveHistory: List<CrownsMove>,
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
            status = status,
            moveHistory = moveHistory,
            hintsUsed = hintsUsed,
            currentHint = currentHint,
            violations = analysis.violations,
        )
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

    private fun playerCellAt(
        board: CrownsState,
        userMarks: Set<CrownsPosition>,
        position: CrownsPosition,
    ): CrownsPlayerCell =
        when (position) {
            in board.crowns -> CrownsPlayerCell.CROWN
            in userMarks -> CrownsPlayerCell.MARKED
            else -> CrownsPlayerCell.EMPTY
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
