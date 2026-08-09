package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrownsValidatorTest {
    private val validator = CrownsValidator()

    @Test
    fun malformedRegionLayoutsAreRejected() {
        val assignments = rowRegions(size = 4)

        assertThrows(IllegalArgumentException::class.java) {
            CrownsPuzzle(crownsId(), 4, assignments - CrownsPosition(3, 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrownsPuzzle(
                crownsId(),
                4,
                assignments.mapValues { RegionId(it.value.value % 3) },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrownsPuzzle(balanceId(), 4, assignments)
        }
    }

    @Test
    fun partialStateReportsEveryFormalConflictType() {
        val puzzle = puzzle()
        val state =
            CrownsState(
                listOf(
                    CrownsPosition(0, 0),
                    CrownsPosition(0, 1),
                    CrownsPosition(1, 0),
                ),
            )

        val analysis = CrownsRules.analyze(puzzle, state)

        assertEquals(
            setOf(
                CrownsViolationType.ROW_CONFLICT,
                CrownsViolationType.COLUMN_CONFLICT,
                CrownsViolationType.REGION_CONFLICT,
                CrownsViolationType.DIAGONAL_ADJACENCY_CONFLICT,
            ),
            analysis.violations.map { it.type }.toSet(),
        )
        assertTrue(analysis.violations.all { it.affectedPositions.isNotEmpty() })
        assertTrue(validator.validate(puzzle, state) is ValidationResult.Invalid)
    }

    @Test
    fun validPartialAndCompletedStatesAreDistinguished() {
        val puzzle = puzzle()
        val completedCrowns =
            listOf(
                CrownsPosition(0, 1),
                CrownsPosition(1, 3),
                CrownsPosition(2, 0),
                CrownsPosition(3, 2),
            )

        assertEquals(
            ValidationResult.ValidPartial,
            validator.validate(puzzle, CrownsState(completedCrowns.dropLast(1))),
        )
        assertEquals(
            ValidationResult.ValidComplete,
            validator.validate(puzzle, CrownsState(completedCrowns)),
        )
    }

    private fun puzzle(): CrownsPuzzle = CrownsPuzzle(crownsId(), 4, rowRegions(size = 4))

    private fun rowRegions(size: Int): Map<CrownsPosition, RegionId> =
        buildMap {
            repeat(size) { row ->
                repeat(size) { column ->
                    put(CrownsPosition(row, column), RegionId(row))
                }
            }
        }

    private fun crownsId(): PuzzleId = puzzleId(PuzzleType.CROWNS)

    private fun balanceId(): PuzzleId = puzzleId(PuzzleType.BALANCE)

    private fun puzzleId(type: PuzzleType): PuzzleId =
        PuzzleId(
            type = type,
            difficulty = Difficulty.EASY,
            seed = PuzzleSeed(1),
            generatorVersion = GeneratorVersion(1),
        )
}
