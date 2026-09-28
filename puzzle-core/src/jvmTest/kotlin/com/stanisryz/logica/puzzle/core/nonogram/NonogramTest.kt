package com.stanisryz.logica.puzzle.core.nonogram

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NonogramTest {
    private val k = NonogramKnowledge.UNKNOWN
    private val f = NonogramKnowledge.FILLED
    private val e = NonogramKnowledge.EMPTY

    @Test
    fun cluesAreRunLengths() {
        assertEquals(listOf(2, 1), NonogramClues.of(listOf(true, true, false, false, true)))
        assertEquals(emptyList(), NonogramClues.of(listOf(false, false)))
    }

    @Test
    fun lineSolverFindsOverlapsAndContradictions() {
        // A run of 4 in 5 cells always covers the middle three.
        assertEquals(listOf(k, f, f, f, k), NonogramLineSolver.solveLine(listOf(4), List(5) { k }))
        // 1 1 1 in five cells has exactly one placement.
        assertEquals(listOf(f, e, f, e, f), NonogramLineSolver.solveLine(listOf(1, 1, 1), List(5) { k }))
        assertEquals(List(3) { e }, NonogramLineSolver.solveLine(emptyList(), List(3) { k }))
        assertNull(NonogramLineSolver.solveLine(listOf(3), listOf(f, e, k, k, k).let { listOf(f, e, f, f, f) }.map { it }))
    }

    @Test
    fun generatorIsDeterministicAndLineSolvable() {
        Difficulty.entries.forEach { difficulty ->
            val generator = NonogramGeneratorV1()
            val first = generator.generate(PuzzleSeed(7), difficulty)
            assertEquals(first, generator.generate(PuzzleSeed(7), difficulty))
            assertEquals(NonogramGeneratorV1.profileFor(difficulty).size, first.size)
            val solved = assertNotNull(NonogramLineSolver.solve(first.size, first.rowClues, first.columnClues))
            assertEquals(first.solution, solved.map { it == f })
        }
    }

    @Test
    fun generatorAcceptsMostSeeds() {
        Difficulty.entries.forEach { difficulty ->
            val accepted =
                (1L..60L).count { seed ->
                    runCatching { NonogramGeneratorV1().generate(PuzzleSeed(seed), difficulty) }.isSuccess
                }
            assertTrue(accepted >= 50, "${difficulty.name}: only $accepted of 60 seeds accepted")
        }
    }

    @Test
    fun wrongTapsCostMistakesAndRevealTheTruth() {
        val puzzle = puzzle("11100", "00000", "10101", "11111", "00100")
        val engine = NonogramGameEngine(puzzle)
        var state = engine.start()
        // The empty row starts crossed out.
        assertTrue((0 until 5).all { state.cellAt(5, NonogramPosition(1, it)) == NonogramCell.CROSSED })

        state = engine.mark(state, NonogramPosition(0, 4), NonogramTool.FILL)
        assertEquals(1, state.mistakesUsed)
        assertEquals(NonogramCell.CROSSED, state.cellAt(5, NonogramPosition(0, 4)))
        assertTrue(4 in state.mistakeCells)
        // An opened cell ignores further taps.
        assertEquals(state, engine.mark(state, NonogramPosition(0, 4), NonogramTool.CROSS))

        // Finding the row's three filled cells crosses out the rest of it by itself.
        state = engine.mark(state, NonogramPosition(0, 0), NonogramTool.FILL)
        state = engine.mark(state, NonogramPosition(0, 1), NonogramTool.FILL)
        state = engine.mark(state, NonogramPosition(0, 2), NonogramTool.FILL)
        assertEquals(NonogramCell.CROSSED, state.cellAt(5, NonogramPosition(0, 3)))

        state = engine.mark(state, NonogramPosition(2, 1), NonogramTool.FILL)
        state = engine.mark(state, NonogramPosition(2, 3), NonogramTool.FILL)
        assertEquals(NonogramGameStatus.FAILED, state.status)
        assertEquals(state, engine.mark(state, NonogramPosition(3, 0), NonogramTool.FILL))
    }

    @Test
    fun hintsFinishAPuzzleWithoutMistakes() {
        val puzzle = NonogramGeneratorV1().generate(PuzzleSeed(3), Difficulty.HARD)
        val engine = NonogramGameEngine(puzzle)
        var state = engine.start()
        var guard = 0
        while (state.status == NonogramGameStatus.IN_PROGRESS && guard++ < 200) state = engine.revealHint(state)
        assertEquals(NonogramGameStatus.SOLVED, state.status)
        assertEquals(0, state.mistakesUsed)
        assertEquals(puzzle.solution, state.cells.map { it == NonogramCell.FILLED })
    }

    private fun puzzle(vararg rows: String): NonogramPuzzle =
        NonogramPuzzle(
            PuzzleId(PuzzleType.NONOGRAM, Difficulty.EASY, PuzzleSeed(1), GeneratorVersion(1)),
            rows.size,
            rows.flatMap { row -> row.map { it == '1' } },
        )
}
