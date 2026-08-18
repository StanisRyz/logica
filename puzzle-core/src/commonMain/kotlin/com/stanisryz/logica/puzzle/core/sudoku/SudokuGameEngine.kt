package com.stanisryz.logica.puzzle.core.sudoku

class SudokuGameEngine(
    private val puzzle: SudokuPuzzle,
    private val hintEngine: SudokuHintEngine = SudokuHintEngine(),
) {
    init {
        require(puzzle.givens.length == SudokuGameState.CELL_COUNT)
        require(puzzle.solution.length == SudokuGameState.CELL_COUNT)
    }

    fun start(): SudokuGameState =
        createState(
            cells =
                puzzle.givens.map { character ->
                    val value = character.digitToInt()
                    if (value == 0) {
                        SudokuCellState(0, SudokuCellStatus.EMPTY)
                    } else {
                        SudokuCellState(value, SudokuCellStatus.GIVEN)
                    }
                },
            mistakesUsed = 0,
            hintsUsed = 0,
            currentHint = null,
        )

    fun placeValue(
        state: SudokuGameState,
        position: SudokuPosition,
        digit: Int,
    ): SudokuGameState {
        SudokuCandidateMask.digitBit(digit)
        requireCompatible(state)
        if (state.status.isTerminal || state.isLocked(position)) return state
        val current = state.cellAt(position)
        val cells = state.cells.toMutableList()
        var mistakesUsed = state.mistakesUsed

        when {
            current.status == SudokuCellStatus.INCORRECT && current.value == digit ->
                cells[position.index] = SudokuCellState(0, SudokuCellStatus.EMPTY)
            puzzle.solution[position.index].digitToInt() == digit -> {
                cells[position.index] = SudokuCellState(digit, SudokuCellStatus.CORRECT)
                removeCandidateFromPeers(cells, position, digit)
            }
            else -> {
                cells[position.index] = SudokuCellState(digit, SudokuCellStatus.INCORRECT)
                mistakesUsed += 1
            }
        }
        return createState(cells, mistakesUsed, state.hintsUsed, currentHint = null)
    }

    fun toggleCandidate(
        state: SudokuGameState,
        position: SudokuPosition,
        digit: Int,
    ): SudokuGameState {
        SudokuCandidateMask.digitBit(digit)
        requireCompatible(state)
        if (state.status.isTerminal) return state
        val current = state.cellAt(position)
        if (current.status != SudokuCellStatus.EMPTY) return state
        if (!current.candidates.contains(digit) && hasConfirmedPeerValue(state.cells, position, digit)) return state

        val cells = state.cells.toMutableList()
        cells[position.index] = current.copy(candidates = current.candidates.toggle(digit))
        return createState(cells, state.mistakesUsed, state.hintsUsed, state.currentHint)
    }

    fun requestHint(state: SudokuGameState): SudokuGameState {
        requireCompatible(state)
        if (state.status.isTerminal) return state
        val hint = hintEngine.nextHint(puzzle, state) ?: return state
        val cells = state.cells.toMutableList()
        cells[hint.position.index] = SudokuCellState(hint.value, SudokuCellStatus.CORRECT)
        removeCandidateFromPeers(cells, hint.position, hint.value)
        return createState(cells, state.mistakesUsed, state.hintsUsed + 1, hint)
    }

    fun retry(state: SudokuGameState): SudokuGameState {
        requireCompatible(state)
        return if (state.status.isTerminal) start() else state
    }

    fun restore(
        playerValues: List<Int>,
        candidateMasks: List<SudokuCandidateMask>,
        mistakesUsed: Int,
        hintsUsed: Int,
    ): SudokuGameState {
        require(playerValues.size == SudokuGameState.CELL_COUNT) { "Saved Sudoku values have invalid length." }
        require(candidateMasks.size == SudokuGameState.CELL_COUNT) { "Saved Sudoku candidates have invalid length." }
        val cells =
            List(SudokuGameState.CELL_COUNT) { index ->
                val given = puzzle.givens[index].digitToInt()
                val playerValue = playerValues[index]
                require(playerValue in 0..9) { "Saved Sudoku value is out of range." }
                if (given != 0) {
                    require(playerValue == 0 && candidateMasks[index].isEmpty) {
                        "Saved Sudoku payload changes a given cell."
                    }
                    SudokuCellState(given, SudokuCellStatus.GIVEN)
                } else if (playerValue == 0) {
                    SudokuCellState(0, SudokuCellStatus.EMPTY, candidateMasks[index])
                } else {
                    require(candidateMasks[index].isEmpty) { "A committed Sudoku cell cannot have candidates." }
                    val status =
                        if (playerValue == puzzle.solution[index].digitToInt()) {
                            SudokuCellStatus.CORRECT
                        } else {
                            SudokuCellStatus.INCORRECT
                        }
                    SudokuCellState(playerValue, status)
                }
            }
        cells.forEachIndexed { index, cell ->
            if (cell.status == SudokuCellStatus.EMPTY) {
                cell.candidates.digits.forEach { digit ->
                    require(!hasConfirmedPeerValue(cells, SudokuPosition.fromIndex(index), digit)) {
                        "Saved Sudoku candidate conflicts with a confirmed peer."
                    }
                }
            }
        }
        require(cells.count { it.status == SudokuCellStatus.INCORRECT } <= mistakesUsed) {
            "Saved Sudoku has more current errors than committed mistake events."
        }
        return createState(cells, mistakesUsed, hintsUsed, currentHint = null)
    }

    private fun createState(
        cells: List<SudokuCellState>,
        mistakesUsed: Int,
        hintsUsed: Int,
        currentHint: SudokuHint?,
    ): SudokuGameState {
        val status =
            when {
                mistakesUsed >= SudokuGameState.MAX_MISTAKES -> SudokuGameStatus.FAILED
                cells.all { it.status.isConfirmed } -> SudokuGameStatus.SOLVED
                else -> SudokuGameStatus.IN_PROGRESS
            }
        return SudokuGameState(puzzle.id, cells, status, mistakesUsed, hintsUsed, currentHint)
    }

    private fun removeCandidateFromPeers(
        cells: MutableList<SudokuCellState>,
        position: SudokuPosition,
        digit: Int,
    ) {
        cells.forEachIndexed { index, cell ->
            val other = SudokuPosition.fromIndex(index)
            if (cell.status == SudokuCellStatus.EMPTY && position.isPeerOf(other)) {
                cells[index] = cell.copy(candidates = cell.candidates.remove(digit))
            }
        }
    }

    private fun hasConfirmedPeerValue(
        cells: List<SudokuCellState>,
        position: SudokuPosition,
        digit: Int,
    ): Boolean =
        cells.indices.any { index ->
            val cell = cells[index]
            cell.status.isConfirmed && cell.value == digit && position.isPeerOf(SudokuPosition.fromIndex(index))
        }

    private fun requireCompatible(state: SudokuGameState) {
        require(state.puzzleId == puzzle.id) { "Sudoku game state belongs to another puzzle." }
    }
}
