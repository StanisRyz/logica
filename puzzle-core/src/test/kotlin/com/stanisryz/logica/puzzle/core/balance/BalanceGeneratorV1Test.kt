package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.registry.PuzzleGeneratorRegistry
import com.stanisryz.logica.puzzle.core.testing.PuzzleGeneratorContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class BalanceGeneratorV1Test {
    private val solver = BalanceSolver()
    private val evaluator = BalanceDifficultyEvaluator(solver)
    private val generator = BalanceGeneratorV1(solver, evaluator)

    @Test
    fun generationIsDeterministicAndPuzzlesHaveStructuralEquality() {
        val seed = PuzzleSeed(11)

        val first = generator.generate(seed, Difficulty.EASY)
        val second = generator.generate(seed, Difficulty.EASY)

        assertNotSame(first, second)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(Difficulty.EASY, evaluator.evaluate(first))
    }

    @Test
    fun generatedPuzzleSatisfiesTheSharedContractAndNeedsNoGuessing() {
        val puzzle =
            PuzzleGeneratorContract.verify(
                generator = generator,
                solver = solver,
                validator = BalanceValidator(),
                solutionToState = BalanceSolution::asState,
                seed = PuzzleSeed(22),
                difficulty = Difficulty.MEDIUM,
            )

        val analysis = solver.solveWithAnalysis(puzzle).analysis
        assertEquals(0, analysis.branchDecisions)
        assertEquals(0, analysis.maximumSearchDepth)
        assertEquals(Difficulty.MEDIUM, evaluator.evaluate(puzzle))
        assertSame(
            generator,
            PuzzleGeneratorRegistry(listOf(generator)).find(PuzzleType.BALANCE, GeneratorVersion(1)),
        )
    }

    @Test
    fun fixedSeedsReachHardAndExpertProfiles() {
        listOf(
            Difficulty.HARD to PuzzleSeed(33),
            Difficulty.EXPERT to PuzzleSeed(44),
        ).forEach { (difficulty, seed) ->
            val puzzle = generator.generate(seed, difficulty)
            val result = solver.solveWithAnalysis(puzzle)

            assertEquals(difficulty, evaluator.evaluate(puzzle))
            assertEquals(1, solver.countSolutions(puzzle, limit = 2))
            assertEquals(0, result.analysis.branchDecisions)
            assertEquals(0, result.analysis.maximumSearchDepth)
        }
    }
}
