package com.stanisryz.logica.balance

import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceClue
import com.stanisryz.logica.puzzle.core.balance.BalanceGameEngine
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BalanceSessionCodecTest {
    @Test
    fun roundTripPreservesBoardMoveHistoryAndHintUsage() {
        val puzzle = testPuzzle()
        val engine = BalanceGameEngine(puzzle)
        val moved = engine.setValue(engine.start(), BalancePosition(0, 0), BalanceCell.ZERO)
        val game = engine.requestHint(moved)
        val encoded = BalanceSessionCodec.encode(game)

        val restored =
            BalanceSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = BalanceSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload,
                moveHistoryPayload = encoded.moveHistoryPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )

        assertEquals(game, restored)
        val undone = engine.undo(restored)
        assertEquals(engine.start().board, undone.board)
        assertEquals(1, undone.hintsUsed)
        assertEquals(emptyList<Any>(), undone.moveHistory)
    }

    @Test
    fun decodeRejectsUnsupportedOrCorruptedSessions() {
        val puzzle = testPuzzle()
        val encoded = BalanceSessionCodec.encode(BalanceGameEngine(puzzle).start())

        assertThrows(IllegalArgumentException::class.java) {
            BalanceSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = 2,
                gameplayPayload = encoded.gameplayPayload,
                moveHistoryPayload = encoded.moveHistoryPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BalanceSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = BalanceSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload.replace("cells=..", "cells=."),
                moveHistoryPayload = encoded.moveHistoryPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }
    }

    private fun testPuzzle(): BalancePuzzle {
        val solution =
            listOf(
                listOf(BalanceCell.ZERO, BalanceCell.ZERO, BalanceCell.ONE, BalanceCell.ONE),
                listOf(BalanceCell.ZERO, BalanceCell.ONE, BalanceCell.ZERO, BalanceCell.ONE),
                listOf(BalanceCell.ONE, BalanceCell.ZERO, BalanceCell.ONE, BalanceCell.ZERO),
                listOf(BalanceCell.ONE, BalanceCell.ONE, BalanceCell.ZERO, BalanceCell.ZERO),
            )
        val editable = setOf(BalancePosition(0, 0), BalancePosition(0, 1))
        val clues =
            solution.flatMapIndexed { row, cells ->
                cells.mapIndexedNotNull { column, cell ->
                    val position = BalancePosition(row, column)
                    if (position in editable) null else BalanceClue(position, cell)
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
            size = 4,
            fixedClues = clues,
        )
    }
}
