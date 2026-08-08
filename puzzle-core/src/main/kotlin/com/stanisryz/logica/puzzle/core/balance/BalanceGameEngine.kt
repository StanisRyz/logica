package com.stanisryz.logica.puzzle.core.balance

class BalanceGameEngine(
    private val puzzle: BalancePuzzle,
    private val hintProvider: BalanceHintProvider = BalanceHintProvider(),
) {
    fun start(): BalanceGameState =
        createState(
            board = BalanceState.fromPuzzle(puzzle),
            moveHistory = emptyList(),
            hintsUsed = 0,
            currentHint = null,
        )

    fun setValue(
        state: BalanceGameState,
        position: BalancePosition,
        value: BalanceCell,
    ): BalanceGameState {
        requireCompatible(state)
        if (position in puzzle.fixedClues) return state

        val previousValue = state.board.cellAt(position)
        if (previousValue == value) return state

        return createState(
            board = state.board.withCell(position, value),
            moveHistory = state.moveHistory + BalanceMove(position, previousValue, value),
            hintsUsed = state.hintsUsed,
            currentHint = null,
        )
    }

    fun clearValue(
        state: BalanceGameState,
        position: BalancePosition,
    ): BalanceGameState = setValue(state, position, BalanceCell.EMPTY)

    fun cycleValue(
        state: BalanceGameState,
        position: BalancePosition,
    ): BalanceGameState {
        requireCompatible(state)
        val nextValue =
            when (state.board.cellAt(position)) {
                BalanceCell.EMPTY -> BalanceCell.ZERO
                BalanceCell.ZERO -> BalanceCell.ONE
                BalanceCell.ONE -> BalanceCell.EMPTY
            }
        return setValue(state, position, nextValue)
    }

    fun undo(state: BalanceGameState): BalanceGameState {
        requireCompatible(state)
        val move = state.moveHistory.lastOrNull() ?: return state
        return createState(
            board = state.board.withCell(move.position, move.previousValue),
            moveHistory = state.moveHistory.dropLast(1),
            hintsUsed = state.hintsUsed,
            currentHint = null,
        )
    }

    fun reset(state: BalanceGameState): BalanceGameState {
        requireCompatible(state)
        val initialBoard = BalanceState.fromPuzzle(puzzle)
        if (state.board == initialBoard && state.moveHistory.isEmpty() && state.currentHint == null) return state
        return createState(
            board = initialBoard,
            moveHistory = emptyList(),
            hintsUsed = state.hintsUsed,
            currentHint = null,
        )
    }

    fun requestHint(state: BalanceGameState): BalanceGameState {
        requireCompatible(state)
        val hint = hintProvider.hint(puzzle, state.board) ?: return state
        if (hint == state.currentHint) return state
        return createState(
            board = state.board,
            moveHistory = state.moveHistory,
            hintsUsed = state.hintsUsed + 1,
            currentHint = hint,
        )
    }

    private fun createState(
        board: BalanceState,
        moveHistory: List<BalanceMove>,
        hintsUsed: Int,
        currentHint: BalanceHint?,
    ): BalanceGameState {
        val analysis = BalanceRules.analyze(puzzle, board)
        val status =
            if (analysis.isComplete && analysis.violations.isEmpty()) {
                BalanceGameStatus.SOLVED
            } else {
                BalanceGameStatus.IN_PROGRESS
            }
        return BalanceGameState(
            board = board,
            status = status,
            moveHistory = moveHistory,
            hintsUsed = hintsUsed,
            currentHint = currentHint,
            violations = analysis.violations,
        )
    }

    private fun requireCompatible(state: BalanceGameState) {
        require(state.board.size == puzzle.size) { "Game state board size does not match the puzzle." }
    }
}
