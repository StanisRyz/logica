package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes

class BalanceGameEngine(
    private val puzzle: BalancePuzzle,
    private val hintProvider: BalanceHintProvider = BalanceHintProvider(),
    private val solver: BalanceSolver = BalanceSolver(),
) {
    /**
     * The answer committed placements are checked against. It is resolved once, and only for a
     * puzzle that really has a single answer, so an ambiguous board leaves every value unverified
     * instead of calling a legitimate alternative wrong.
     */
    private val solution: BalanceSolution? by lazy {
        if (solver.countSolutions(puzzle, limit = 2) != 1) null else solver.solve(puzzle)
    }

    /** A fresh attempt: the puzzle's own clues, no player values, no pencil marks, no mistakes. */
    fun start(): BalanceGameState =
        createState(
            board = BalanceState.fromPuzzle(puzzle),
            pencilMarks = emptyMap(),
            mistakesUsed = 0,
            hintsUsed = 0,
            currentHint = null,
        )

    /**
     * Commits the value the player selected. Tapping a cell that already holds that same value
     * removes it, which is how a wrong value is taken back; a confirmed value cannot be changed.
     * Every newly committed incorrect value costs one mistake, and the third one ends the attempt.
     */
    fun placeValue(
        state: BalanceGameState,
        position: BalancePosition,
        value: BalanceCell,
    ): BalanceGameState {
        require(value != BalanceCell.EMPTY) { "A committed placement needs a concrete value." }
        requireCompatible(state)
        BalanceBoardConstraints.requireInside(puzzle.size, position)
        if (state.status.isTerminal) return state
        if (state.isLocked(position)) return state

        val committed = if (state.board.cellAt(position) == value) BalanceCell.EMPTY else value
        // Removing a wrong value never refunds the mistake it already cost, and replacing one wrong
        // value with another is a new incorrect attempt of its own.
        val isNewMistake = committed != BalanceCell.EMPTY && isIncorrect(position, committed)
        return createState(
            board = state.board.withCell(position, committed),
            pencilMarks = state.pencilMarks - position,
            mistakesUsed = if (isNewMistake) state.mistakesUsed + 1 else state.mistakesUsed,
            hintsUsed = state.hintsUsed,
            currentHint = null,
        )
    }

    /** Adds or removes one draft value. Pencil marks are never validated and never cost a mistake. */
    fun togglePencilMark(
        state: BalanceGameState,
        position: BalancePosition,
        value: BalanceCell,
    ): BalanceGameState {
        require(value != BalanceCell.EMPTY) { "A pencil mark needs a concrete value." }
        requireCompatible(state)
        BalanceBoardConstraints.requireInside(puzzle.size, position)
        if (state.status.isTerminal) return state
        if (state.isLocked(position)) return state
        // A cell holding a committed value is not a hypothesis any more.
        if (state.board.cellAt(position) != BalanceCell.EMPTY) return state

        val current = state.pencilMarksAt(position)
        val updated = if (value in current) current - value else current + value
        return createState(
            board = state.board,
            pencilMarks =
                if (updated.isEmpty()) state.pencilMarks - position else state.pencilMarks + (position to updated),
            mistakesUsed = state.mistakesUsed,
            hintsUsed = state.hintsUsed,
            // The board is unchanged, so an open hint still describes this position correctly.
            currentHint = state.currentHint,
        )
    }

    fun restore(
        board: BalanceState,
        pencilMarks: Map<BalancePosition, Set<BalanceCell>>,
        mistakesUsed: Int,
        hintsUsed: Int,
        currentHint: BalanceHint?,
    ): BalanceGameState {
        require(board.size == puzzle.size) { "Saved board size does not match the puzzle." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
        require(mistakesUsed in 0..PuzzleMistakes.MAX_MISTAKES) { "Saved mistakes are out of range." }
        puzzle.fixedClues.forEach { (position, value) ->
            require(board.cellAt(position) == value) { "Saved board changes a fixed clue." }
        }
        pencilMarks.forEach { (position, marks) ->
            BalanceBoardConstraints.requireInside(puzzle.size, position)
            require(marks.isNotEmpty() && BalanceCell.EMPTY !in marks) { "A pencil mark must be a concrete value." }
            require(position !in puzzle.fixedClues && board.cellAt(position) == BalanceCell.EMPTY) {
                "Pencil marks belong to empty editable cells only."
            }
        }
        require(currentHint == null || hintsUsed > 0) { "A current hint requires positive hint usage." }
        require(currentHint == null || hintProvider.hint(puzzle, board) == currentHint) {
            "Saved hint is not compatible with the saved board."
        }

        return createState(
            board = board,
            pencilMarks = pencilMarks,
            mistakesUsed = mistakesUsed,
            hintsUsed = hintsUsed,
            currentHint = currentHint,
        )
    }

    fun requestHint(state: BalanceGameState): BalanceGameState {
        requireCompatible(state)
        if (state.status.isTerminal) return state
        val hint = hintProvider.hint(puzzle, state.board) ?: return state
        if (hint == state.currentHint) return state
        return createState(
            board = state.board,
            pencilMarks = state.pencilMarks,
            mistakesUsed = state.mistakesUsed,
            hintsUsed = state.hintsUsed + 1,
            currentHint = hint,
        )
    }

    private fun createState(
        board: BalanceState,
        pencilMarks: Map<BalancePosition, Set<BalanceCell>>,
        mistakesUsed: Int,
        hintsUsed: Int,
        currentHint: BalanceHint?,
    ): BalanceGameState {
        val analysis = BalanceRules.analyze(puzzle, board)
        val status =
            when {
                mistakesUsed >= PuzzleMistakes.MAX_MISTAKES -> BalanceGameStatus.FAILED
                analysis.isComplete && analysis.violations.isEmpty() -> BalanceGameStatus.SOLVED
                else -> BalanceGameStatus.IN_PROGRESS
            }
        return BalanceGameState(
            board = board,
            status = status,
            pencilMarks = pencilMarks,
            cellStatuses = cellStatuses(board),
            mistakesUsed = mistakesUsed,
            hintsUsed = hintsUsed,
            currentHint = currentHint,
            violations = analysis.violations,
        )
    }

    private fun isIncorrect(
        position: BalancePosition,
        value: BalanceCell,
    ): Boolean = solution?.let { it.cellAt(position) != value } == true

    private fun cellStatuses(board: BalanceState): Map<BalancePosition, BalanceCellStatus> {
        val answer = solution
        return buildMap {
            for (row in 0 until puzzle.size) {
                for (column in 0 until puzzle.size) {
                    val position = BalancePosition(row, column)
                    val status =
                        when {
                            position in puzzle.fixedClues -> BalanceCellStatus.FIXED
                            board.cellAt(position) == BalanceCell.EMPTY -> BalanceCellStatus.EMPTY
                            answer == null -> BalanceCellStatus.UNVERIFIED
                            board.cellAt(position) == answer.cellAt(position) -> BalanceCellStatus.CORRECT
                            else -> BalanceCellStatus.INCORRECT
                        }
                    if (status != BalanceCellStatus.EMPTY) put(position, status)
                }
            }
        }
    }

    private fun requireCompatible(state: BalanceGameState) {
        require(state.board.size == puzzle.size) { "Game state board size does not match the puzzle." }
    }
}
