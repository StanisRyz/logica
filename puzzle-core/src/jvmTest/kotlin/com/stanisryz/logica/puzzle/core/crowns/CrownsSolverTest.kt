package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrownsSolverTest {
    private val logicEngine = CrownsLogicEngine()
    private val solver = CrownsSolver(logicEngine)

    @Test
    fun logicEngineReturnsStableSingleCandidateAndRegionLockSteps() {
        val puzzle =
            puzzle(
                "AABB",
                "AABB",
                "CCDD",
                "CCDD",
            )
        val initial = CrownsCandidateState.from(puzzle)
        val singleState =
            initial
                .exclude(
                    listOf(
                        CrownsPosition(0, 1),
                        CrownsPosition(0, 2),
                        CrownsPosition(0, 3),
                    ),
                ).state
        val lockedState =
            initial
                .exclude(
                    listOf(
                        CrownsPosition(1, 0),
                        CrownsPosition(1, 1),
                    ),
                ).state

        val singleStep = logicEngine.nextStep(puzzle, singleState)
        val lockedStep = logicEngine.nextStep(puzzle, lockedState)

        assertTrue(singleStep is CrownsLogicStep.PlaceCrown)
        assertEquals(CrownsLogicTechnique.SINGLE_CANDIDATE_ROW, singleStep?.technique)
        assertEquals(setOf(CrownsPosition(0, 0)), singleStep?.targetPositions)
        assertTrue(lockedStep is CrownsLogicStep.ExcludePositions)
        assertEquals(CrownsLogicTechnique.REGION_LOCKED_TO_ROW, lockedStep?.technique)
        assertEquals(
            setOf(CrownsPosition(0, 2), CrownsPosition(0, 3)),
            lockedStep?.targetPositions,
        )

        val propagated = CrownsCandidateState.from(puzzle, CrownsState(listOf(CrownsPosition(0, 0))))
        assertFalse(CrownsPosition(0, 1) in propagated.allowed)
        assertFalse(CrownsPosition(2, 0) in propagated.allowed)
        assertFalse(CrownsPosition(1, 1) in propagated.allowed)
    }

    @Test
    fun solverFindsTheKnownUniqueSolutionForAnIrregularPuzzle() {
        val puzzle = irregularPuzzle()
        val expected =
            CrownsState(
                listOf(
                    CrownsPosition(0, 1),
                    CrownsPosition(1, 4),
                    CrownsPosition(2, 2),
                    CrownsPosition(3, 0),
                    CrownsPosition(4, 3),
                ),
            )

        val result = solver.solveWithAnalysis(puzzle)

        assertNotNull(result.solution)
        assertEquals(expected, result.solution?.asState())
        assertEquals(ValidationResult.ValidComplete, CrownsValidator().validate(puzzle, result.solution!!.asState()))
        assertTrue(result.analysis.candidateEliminations > 0)
        assertEquals(result.analysis.logicalSteps, result.analysis.techniqueCounts.total())
    }

    @Test
    fun solutionCountingDistinguishesUnsolvableUniqueAndAmbiguousPuzzles() {
        val unsolvable = rowRegionPuzzle(size = 2)
        val unique = irregularPuzzle()
        val ambiguous = rowRegionPuzzle(size = 4)

        assertEquals(0, solver.countSolutions(unsolvable))
        assertEquals(1, solver.countSolutions(unique))
        assertEquals(2, solver.countSolutions(ambiguous, limit = 2))
        assertThrows(IllegalArgumentException::class.java) { solver.countSolutions(ambiguous, limit = 0) }
    }

    private fun irregularPuzzle(): CrownsPuzzle =
        puzzle(
            "CCCBB",
            "CCCCB",
            "CCDBB",
            "EEDDB",
            "EEDAA",
        )

    private fun rowRegionPuzzle(size: Int): CrownsPuzzle = puzzle(*Array(size) { row -> ('A' + row).toString().repeat(size) })

    private fun puzzle(vararg rows: String): CrownsPuzzle {
        val assignments =
            buildMap {
                rows.forEachIndexed { row, regions ->
                    regions.forEachIndexed { column, region ->
                        put(CrownsPosition(row, column), RegionId(region - 'A'))
                    }
                }
            }
        return CrownsPuzzle(
            id =
                PuzzleId(
                    type = PuzzleType.CROWNS,
                    difficulty = Difficulty.MEDIUM,
                    seed = PuzzleSeed(17),
                    generatorVersion = GeneratorVersion(1),
                ),
            size = rows.size,
            regionAssignments = assignments,
        )
    }

    private fun CrownsTechniqueCounts.total(): Int =
        singleCandidateRow +
            singleCandidateColumn +
            singleCandidateRegion +
            regionLockedToRow +
            regionLockedToColumn
}
