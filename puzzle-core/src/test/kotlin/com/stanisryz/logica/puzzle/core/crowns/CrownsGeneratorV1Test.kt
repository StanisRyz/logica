package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.registry.PuzzleGeneratorRegistry
import com.stanisryz.logica.puzzle.core.testing.PuzzleGeneratorContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CrownsGeneratorV1Test {
    private val solver = CrownsSolver()
    private val evaluator = CrownsDifficultyEvaluator(solver)
    private val generator = CrownsGeneratorV1(solver, evaluator)

    @Test
    fun generationIsDeterministicAndEveryRegionIsConnected() {
        val puzzle = generator.generate(PuzzleSeed(11), Difficulty.EASY)
        val repeated = generator.generate(PuzzleSeed(11), Difficulty.EASY)

        assertEquals(puzzle, repeated)
        assertEquals(puzzle.hashCode(), repeated.hashCode())
        assertEquals(Difficulty.EASY, evaluator.evaluate(puzzle))
        puzzle.regionAssignments.values.toSet().forEach { regionId ->
            val positions = puzzle.regionAssignments.filterValues { it == regionId }.keys
            assertTrue(CrownsRegionConnectivity.isConnected(puzzle.size, positions))
        }
    }

    @Test
    fun generatedPuzzleSatisfiesTheSharedContractAndNeedsNoGuessing() {
        val puzzle =
            PuzzleGeneratorContract.verify(
                generator = generator,
                solver = solver,
                validator = CrownsValidator(),
                solutionToState = CrownsSolution::asState,
                seed = PuzzleSeed(22),
                difficulty = Difficulty.MEDIUM,
            )

        val analysis = solver.solveWithAnalysis(puzzle).analysis
        assertEquals(0, analysis.branchDecisions)
        assertEquals(0, analysis.maximumSearchDepth)
        assertEquals(Difficulty.MEDIUM, evaluator.evaluate(puzzle))
        assertSame(
            generator,
            PuzzleGeneratorRegistry(listOf(generator)).find(PuzzleType.CROWNS, GeneratorVersion(1)),
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
