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
    fun gameplayTransitionsProtectCluesAndPreserveHintUsageAcrossReset() {
        val puzzle = puzzle("001.", "110.", "010.", "101.")
        val engine = BalanceGameEngine(puzzle)
        val editable = BalancePosition(0, 3)
        val initial = engine.start()

        assertSame(initial, engine.cycleValue(initial, BalancePosition(0, 0)))

        val zero = engine.cycleValue(initial, editable)
        val hinted = engine.requestHint(zero)
        assertEquals(BalanceCell.ZERO, zero.board.cellAt(editable))
        assertEquals(1, hinted.hintsUsed)
        assertSame(hinted, engine.requestHint(hinted))

        val one = engine.cycleValue(hinted, editable)
        val empty = engine.cycleValue(one, editable)
        val set = engine.setValue(empty, editable, BalanceCell.ONE)
        val cleared = engine.clearValue(set, editable)
        val undone = engine.undo(cleared)

        assertEquals(BalanceCell.ONE, undone.board.cellAt(editable))
        assertEquals(BalanceCell.EMPTY, empty.board.cellAt(editable))
        assertEquals(set.moveHistory.last(), BalanceMove(editable, BalanceCell.EMPTY, BalanceCell.ONE))

        val reset = engine.reset(undone)
        assertEquals(BalanceState.fromPuzzle(puzzle), reset.board)
        assertTrue(reset.moveHistory.isEmpty())
        assertEquals(1, reset.hintsUsed)
        assertNull(reset.currentHint)
    }

    @Test
    fun diagnosticsTrackInvalidMovesAndStatusChangesOnlyForAValidSolution() {
        val puzzle = puzzle("....", "....", "....", "....")
        val engine = BalanceGameEngine(puzzle)
        var game = engine.start()

        game = engine.setValue(game, BalancePosition(0, 0), BalanceCell.ZERO)
        game = engine.setValue(game, BalancePosition(0, 1), BalanceCell.ZERO)
        game = engine.setValue(game, BalancePosition(0, 2), BalanceCell.ZERO)

        assertEquals(BalanceGameStatus.IN_PROGRESS, game.status)
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
                game = engine.setValue(game, BalancePosition(row, column), value.toCell())
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
