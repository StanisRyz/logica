package com.stanisryz.logica.puzzle.core.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuGameplayTest {
    private val puzzle = puzzle()
    private val engine = SudokuGameEngine(puzzle)

    @Test
    fun `committed values lock when correct and the third new wrong value fails the attempt`() {
        val started = engine.start()
        val given = SudokuPosition(0, 1)
        val correct = SudokuPosition(0, 0)
        val repaired = SudokuPosition(0, 2)

        assertEquals(started, engine.placeValue(started, given, 1))
        var state = engine.placeValue(started, correct, 1)
        assertEquals(SudokuCellStatus.CORRECT, state.cellAt(correct).status)
        assertEquals(state, engine.placeValue(state, correct, 2))

        state = engine.placeValue(state, repaired, 9)
        assertEquals(SudokuCellStatus.INCORRECT, state.cellAt(repaired).status)
        assertEquals(1, state.mistakesUsed)
        state = engine.placeValue(state, repaired, 9)
        assertEquals(SudokuCellStatus.EMPTY, state.cellAt(repaired).status)
        assertEquals(1, state.mistakesUsed)
        state = engine.placeValue(state, repaired, 8)
        assertEquals(SudokuCellStatus.CORRECT, state.cellAt(repaired).status)
        assertEquals(1, state.mistakesUsed)

        state = engine.placeValue(state, SudokuPosition(0, 4), 1)
        state = engine.placeValue(state, SudokuPosition(0, 6), 1)
        assertEquals(SudokuGameStatus.FAILED, state.status)
        assertEquals(3, state.mistakesUsed)
        assertEquals(1, state.cellAt(SudokuPosition(0, 6)).value)
        assertEquals(state, engine.placeValue(state, SudokuPosition(0, 8), 9))

        val retried = engine.retry(state)
        assertEquals(puzzle.id, retried.puzzleId)
        assertEquals(SudokuGameStatus.IN_PROGRESS, retried.status)
        assertEquals(0, retried.mistakesUsed)
        assertEquals(0, retried.hintsUsed)

        var solved = retried
        solved.cells.indices.filter { solved.cells[it].status == SudokuCellStatus.EMPTY }.forEach { index ->
            solved = engine.placeValue(solved, SudokuPosition.fromIndex(index), puzzle.solution[index].digitToInt())
        }
        assertEquals(SudokuGameStatus.SOLVED, solved.status)
        assertEquals(solved, engine.placeValue(solved, SudokuPosition(0, 0), 2))
    }

    @Test
    fun `pencil candidates toggle without mistakes and only confirmed values clean peers`() {
        val peer = SudokuPosition(0, 2)
        var state = engine.toggleCandidate(engine.start(), peer, 1)
        state = engine.toggleCandidate(state, peer, 2)
        assertEquals(listOf(1, 2), state.cellAt(peer).candidates.digits)
        assertEquals(0, state.mistakesUsed)
        assertEquals(state, engine.toggleCandidate(state, peer, 5))

        state = engine.placeValue(state, SudokuPosition(0, 0), 1)
        assertEquals(listOf(2), state.cellAt(peer).candidates.digits)

        state = engine.toggleCandidate(engine.start(), peer, 2)
        state = engine.placeValue(state, SudokuPosition(0, 0), 2)
        assertEquals(SudokuCellStatus.INCORRECT, state.cellAt(SudokuPosition(0, 0)).status)
        assertTrue(state.cellAt(peer).candidates.contains(2))
        assertEquals(1, state.mistakesUsed)
    }

    @Test
    fun `session codec round trip restores identity work mistakes hints and derived statuses`() {
        var state = engine.requestHint(engine.start())
        assertEquals(1, state.hintsUsed)
        assertEquals(0, state.mistakesUsed)
        val hint = checkNotNull(state.currentHint)
        assertEquals(SudokuHintTechnique.NAKED_SINGLE, hint.technique)
        assertEquals(SudokuPosition(6, 4), hint.position)
        assertEquals(8, hint.value)
        assertEquals(SudokuCellStatus.CORRECT, state.cellAt(hint.position).status)

        val wrongIndex = state.cells.indexOfFirst { it.status == SudokuCellStatus.EMPTY }
        val wrongPosition = SudokuPosition.fromIndex(wrongIndex)
        val solutionDigit = puzzle.solution[wrongIndex].digitToInt()
        val wrongDigit = if (solutionDigit == 9) 8 else solutionDigit + 1
        state = engine.placeValue(state, wrongPosition, wrongDigit)

        val candidateIndex = state.cells.indexOfFirst { it.status == SudokuCellStatus.EMPTY }
        val candidatePosition = SudokuPosition.fromIndex(candidateIndex)
        val withCandidate =
            (1..9)
                .asSequence()
                .map { digit -> engine.toggleCandidate(state, candidatePosition, digit) }
                .first { it != state }
        val encoded = SudokuSessionCodecV1.encode(withCandidate)
        val restored = SudokuSessionCodecV1.decode(puzzle, encoded)

        assertEquals(puzzle.id, SudokuSessionCodecV1.puzzleId(encoded))
        assertEquals(withCandidate.cells, restored.cells)
        assertEquals(SudokuCellStatus.INCORRECT, restored.cellAt(wrongPosition).status)
        assertEquals(withCandidate.mistakesUsed, restored.mistakesUsed)
        assertEquals(withCandidate.hintsUsed, restored.hintsUsed)
        assertEquals(encoded, SudokuSessionCodecV1.encode(restored))
        assertNotEquals(
            null,
            restored
                .cellAt(candidatePosition)
                .candidates.digits
                .firstOrNull(),
        )
    }

    private fun puzzle(): SudokuPuzzle =
        SudokuPuzzle(
            id = SudokuPuzzleId(SudokuDatasetVersion.V1, SudokuDifficulty.EASY, FINGERPRINT),
            givens = GIVENS,
            solution = SOLUTION,
            upstreamRatingTenths = 12,
        )

    private companion object {
        const val GIVENS =
            "050703060007000800000816000000030000005000100730040086906000204840572093000409000"
        const val SOLUTION =
            "158723469367954821294816375619238547485697132732145986976381254841572693523469718"
        const val FINGERPRINT = "dfe20863da651e55a9ac79a23e69134faa375a25f50ec4b8518b84199ede492d"
    }
}
