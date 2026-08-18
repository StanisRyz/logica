package com.stanisryz.logica.puzzle.core.balance

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

class BalanceValidatorTest {
    private val validator = BalanceValidator()

    @Test
    fun validPartialAndCompletedStatesAreDistinguished() {
        val puzzle = puzzle()
        val partial =
            state(
                "001.",
                "110.",
                "....",
                "....",
            )
        val completed =
            state(
                "0011",
                "1100",
                "0101",
                "1010",
            )

        assertEquals(ValidationResult.ValidPartial, validator.validate(puzzle, partial))
        assertEquals(ValidationResult.ValidComplete, validator.validate(puzzle, completed))
    }

    @Test
    fun excessValuesAndThreeEqualValuesAreInvalid() {
        val puzzle = puzzle()
        val unbalanced =
            state(
                "0010",
                "....",
                "....",
                "....",
            )
        val triple =
            state(
                "000.",
                "....",
                "....",
                "....",
            )

        assertTrue(validator.validate(puzzle, unbalanced) is ValidationResult.Invalid)
        assertTrue(validator.validate(puzzle, triple) is ValidationResult.Invalid)
    }

    @Test
    fun duplicateLinesChangedCluesAndContradictoryCluesAreRejected() {
        val cluePosition = BalancePosition(0, 0)
        val puzzle = puzzle(listOf(BalanceClue(cluePosition, BalanceCell.ZERO)))
        val changedClue =
            state(
                "1010",
                "....",
                "....",
                "....",
            )
        val duplicateRows =
            state(
                "0011",
                "0011",
                "....",
                "....",
            )

        assertTrue(validator.validate(puzzle, changedClue) is ValidationResult.Invalid)
        assertTrue(validator.validate(puzzle(), duplicateRows) is ValidationResult.Invalid)
        assertThrows(IllegalArgumentException::class.java) {
            puzzle(
                listOf(
                    BalanceClue(cluePosition, BalanceCell.ZERO),
                    BalanceClue(cluePosition, BalanceCell.ONE),
                ),
            )
        }
    }

    private fun puzzle(clues: List<BalanceClue> = emptyList()) =
        BalancePuzzle(
            id =
                PuzzleId(
                    type = PuzzleType.BALANCE,
                    difficulty = Difficulty.EASY,
                    seed = PuzzleSeed(1),
                    generatorVersion = GeneratorVersion(1),
                ),
            size = 4,
            fixedClues = clues,
        )

    private fun state(vararg rows: String): BalanceState =
        BalanceState.fromRows(
            rows.map { row ->
                row.map { value ->
                    when (value) {
                        '.' -> BalanceCell.EMPTY
                        '0' -> BalanceCell.ZERO
                        '1' -> BalanceCell.ONE
                        else -> error("Unknown cell value: $value")
                    }
                }
            },
        )
}
