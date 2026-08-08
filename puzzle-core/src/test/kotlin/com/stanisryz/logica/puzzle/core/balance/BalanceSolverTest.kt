package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceSolverTest {
    private val logicEngine = BalanceLogicEngine()
    private val solver = BalanceSolver(logicEngine)

    @Test
    fun logicEngineReturnsStableExplainableStepsWithoutMutatingState() {
        val tripleState = state("00..", "....", "....", "....")
        val quotaState = state("001.", "....", "....", "....")
        val uniquenessState = state("0011", "0.1.", "1.0.", "1.0.")

        val tripleStep = logicEngine.nextStep(puzzle(4), tripleState)
        val quotaStep = logicEngine.nextStep(puzzle(4), quotaState)
        val uniquenessStep = logicEngine.nextStep(puzzle(4), uniquenessState)

        assertEquals(
            BalanceLogicStep(BalancePosition(0, 2), BalanceCell.ONE, BalanceLogicTechnique.PREVENT_THREE),
            tripleStep,
        )
        assertEquals(
            BalanceLogicStep(BalancePosition(0, 3), BalanceCell.ONE, BalanceLogicTechnique.COMPLETE_QUOTA),
            quotaStep,
        )
        assertEquals(
            BalanceLogicStep(BalancePosition(1, 1), BalanceCell.ONE, BalanceLogicTechnique.PRESERVE_UNIQUENESS),
            uniquenessStep,
        )
        assertEquals(BalanceCell.EMPTY, tripleState.cellAt(BalancePosition(0, 2)))
        assertEquals(BalanceCell.ONE, tripleStep?.applyTo(tripleState)?.cellAt(BalancePosition(0, 2)))
    }

    @Test
    fun solverReturnsOnlyAValidatedCompletedSolutionWithAnalysis() {
        val puzzle = puzzle("001.", "110.", "010.", "101.")
        val expected = state("0011", "1100", "0101", "1010")

        val result = solver.solveWithAnalysis(puzzle)

        assertNotNull(result.solution)
        assertEquals(expected, result.solution?.asState())
        assertEquals(ValidationResult.ValidComplete, BalanceValidator().validate(puzzle, result.solution!!.asState()))
        assertEquals(4, result.analysis.logicalSteps)
        assertEquals(0, result.analysis.branchDecisions)
        assertEquals(0, result.analysis.maximumSearchDepth)
    }

    @Test
    fun solutionCountingStopsAtTheLimitAndDistinguishesFixtureKinds() {
        val unsolvable = puzzle("000.", "....", "....", "....")
        val unique = puzzle("001.", "110.", "010.", "101.")
        val ambiguous = puzzle(4)

        assertEquals(0, solver.countSolutions(unsolvable))
        assertEquals(1, solver.countSolutions(unique))
        assertEquals(2, solver.countSolutions(ambiguous, limit = 2))
        assertTrue(solver.solve(ambiguous) is BalanceSolution)
        assertThrows(IllegalArgumentException::class.java) { solver.countSolutions(ambiguous, limit = 0) }
    }

    private fun puzzle(size: Int): BalancePuzzle = puzzle(*List(size) { ".".repeat(size) }.toTypedArray())

    private fun puzzle(vararg rows: String): BalancePuzzle {
        val clues =
            rows.flatMapIndexed { row, values ->
                values.mapIndexedNotNull { column, value ->
                    value.toCell().takeUnless { it == BalanceCell.EMPTY }?.let { cell ->
                        BalanceClue(BalancePosition(row, column), cell)
                    }
                }
            }
        return BalancePuzzle(
            id =
                PuzzleId(
                    type = PuzzleType.BALANCE,
                    difficulty = Difficulty.EASY,
                    seed = PuzzleSeed(1),
                    generatorVersion = GeneratorVersion(1),
                ),
            size = rows.size,
            fixedClues = clues,
        )
    }

    private fun state(vararg rows: String): BalanceState = BalanceState.fromRows(rows.map { row -> row.map { it.toCell() } })

    private fun Char.toCell(): BalanceCell =
        when (this) {
            '.' -> BalanceCell.EMPTY
            '0' -> BalanceCell.ZERO
            '1' -> BalanceCell.ONE
            else -> error("Unknown cell value: $this")
        }
}
