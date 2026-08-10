package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceGameplayTest {
    @Test
    fun committedValuesAreValidatedWhilePencilMarksStayHypotheses() {
        val puzzle = puzzle("001.", "110.", "010.", "101.")
        val engine = BalanceGameEngine(puzzle)
        val editable = BalancePosition(0, 3)
        val initial = engine.start()

        assertSame(initial, engine.placeValue(initial, BalancePosition(0, 0), BalanceCell.ONE))
        assertEquals(BalanceCellStatus.FIXED, initial.statusAt(BalancePosition(0, 0)))

        val hinted = engine.requestHint(initial)
        assertEquals(1, hinted.hintsUsed)
        assertSame(hinted, engine.requestHint(hinted))

        val drafted = engine.togglePencilMark(hinted, editable, BalanceCell.ZERO)
        val bothDrafted = engine.togglePencilMark(drafted, editable, BalanceCell.ONE)
        assertEquals(setOf(BalanceCell.ZERO, BalanceCell.ONE), bothDrafted.pencilMarksAt(editable))
        assertEquals(BalanceCellStatus.EMPTY, bothDrafted.statusAt(editable))
        assertEquals(setOf(BalanceCell.ONE), engine.togglePencilMark(bothDrafted, editable, BalanceCell.ZERO).pencilMarksAt(editable))

        val wrong = engine.placeValue(bothDrafted, editable, BalanceCell.ZERO)
        assertEquals(BalanceCellStatus.INCORRECT, wrong.statusAt(editable))
        assertEquals(BalanceCell.ZERO, wrong.board.cellAt(editable))
        assertTrue(wrong.pencilMarksAt(editable).isEmpty())
        // A wrong value can neither be pencilled over nor silently corrected.
        assertSame(wrong, engine.togglePencilMark(wrong, editable, BalanceCell.ONE))
        assertEquals(BalanceCell.EMPTY, engine.placeValue(wrong, editable, BalanceCell.ZERO).board.cellAt(editable))

        val correct = engine.placeValue(wrong, editable, BalanceCell.ONE)
        assertEquals(BalanceCellStatus.CORRECT, correct.statusAt(editable))
        assertSame(correct, engine.placeValue(correct, editable, BalanceCell.ONE))
        assertSame(correct, engine.togglePencilMark(correct, editable, BalanceCell.ZERO))

        val reset = engine.reset(correct)
        assertEquals(BalanceState.fromPuzzle(puzzle), reset.board)
        assertTrue(reset.pencilMarks.isEmpty())
        assertEquals(1, reset.hintsUsed)
        assertNull(reset.currentHint)
    }

    @Test
    fun diagnosticsTrackInvalidMovesAndStatusChangesOnlyForAValidSolution() {
        val puzzle = puzzle("....", "....", "....", "....")
        val engine = BalanceGameEngine(puzzle)
        var game = engine.start()

        game = engine.placeValue(game, BalancePosition(0, 0), BalanceCell.ZERO)
        game = engine.placeValue(game, BalancePosition(0, 1), BalanceCell.ZERO)
        game = engine.placeValue(game, BalancePosition(0, 2), BalanceCell.ZERO)

        assertEquals(BalanceGameStatus.IN_PROGRESS, game.status)
        // An empty board has many answers, so nothing can be called right or wrong.
        assertEquals(BalanceCellStatus.UNVERIFIED, game.statusAt(BalancePosition(0, 0)))
        assertTrue(game.violations.any { it.type == BalanceViolationType.UNBALANCED_ROW })
        assertEquals(
            setOf(BalancePosition(0, 0), BalancePosition(0, 1), BalancePosition(0, 2)),
            game.violations.first { it.type == BalanceViolationType.THREE_EQUAL_HORIZONTAL }.affectedPositions,
        )

        game = engine.reset(game)
        game = enter(engine, game, "0011", "0011", "....", "....")
        assertTrue(game.violations.any { it.type == BalanceViolationType.DUPLICATE_ROWS })

        game = enter(engine, game, "0011", "1100", "0101", "1010")
        assertEquals(BalanceGameStatus.SOLVED, game.status)
        assertTrue(game.violations.isEmpty())
    }

    @Test
    fun hintsPreferAnIncorrectValueThenReturnTheNextLogicalStep() {
        val puzzle = puzzle(".01.", "1100", "0101", "101.")
        val provider = BalanceHintProvider()
        val initial = BalanceState.fromPuzzle(puzzle)

        val logicalHint = provider.hint(puzzle, initial)
        assertEquals(BalanceHintKind.LOGICAL_DEDUCTION, logicalHint?.kind)
        assertEquals(BalancePosition(3, 3), logicalHint?.position)
        assertEquals(BalanceCell.ZERO, logicalHint?.suggestedValue)
        assertEquals(BalanceLogicTechnique.COMPLETE_QUOTA, logicalHint?.technique)

        val incorrectPosition = BalancePosition(0, 3)
        val incorrectState = initial.withCell(incorrectPosition, BalanceCell.ZERO)
        assertTrue(BalanceDiagnostics().violations(puzzle, incorrectState).isEmpty())

        val incorrectHint = provider.hint(puzzle, incorrectState)
        assertEquals(BalanceHintKind.INCORRECT_VALUE, incorrectHint?.kind)
        assertEquals(incorrectPosition, incorrectHint?.position)
        assertEquals(BalanceCell.ONE, incorrectHint?.suggestedValue)
        assertNull(incorrectHint?.technique)
    }

    private fun enter(
        engine: BalanceGameEngine,
        initial: BalanceGameState,
        vararg rows: String,
    ): BalanceGameState {
        var game = initial
        rows.forEachIndexed { row, values ->
            values.forEachIndexed { column, value ->
                val position = BalancePosition(row, column)
                val cell = value.toCell()
                if (cell != BalanceCell.EMPTY && game.board.cellAt(position) != cell) {
                    game = engine.placeValue(game, position, cell)
                }
            }
        }
        return game
    }

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

    private fun Char.toCell(): BalanceCell =
        when (this) {
            '.' -> BalanceCell.EMPTY
            '0' -> BalanceCell.ZERO
            '1' -> BalanceCell.ONE
            else -> error("Unknown cell value: $this")
        }
}
