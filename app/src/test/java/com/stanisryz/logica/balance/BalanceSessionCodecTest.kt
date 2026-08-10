package com.stanisryz.logica.balance

import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceCellStatus
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceSessionCodecTest {
    @Test
    fun roundTripPreservesCommittedValuesPencilMarksAndHintUsage() {
        val puzzle = testPuzzle()
        val engine = BalanceGameEngine(puzzle)
        val wrongCell = BalancePosition(0, 0)
        val draftCell = BalancePosition(0, 1)

        var game = engine.placeValue(engine.start(), wrongCell, BalanceCell.ONE)
        game = engine.togglePencilMark(game, draftCell, BalanceCell.ZERO)
        game = engine.togglePencilMark(game, draftCell, BalanceCell.ONE)
        game = engine.requestHint(game)
        val encoded = BalanceSessionCodec.encode(game)

        val restored =
            BalanceSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = BalanceSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )

        assertEquals(game, restored)
        assertEquals(BalanceCellStatus.INCORRECT, restored.statusAt(wrongCell))
        assertEquals(setOf(BalanceCell.ZERO, BalanceCell.ONE), restored.pencilMarksAt(draftCell))
        assertEquals(1, restored.hintsUsed)
        assertEquals("", encoded.moveHistoryPayload)
    }

    @Test
    fun preStagePlayerValuesRestoreAsCorrectOrWrongWithoutPencilMarks() {
        val puzzle = testPuzzle()
        val editable = BalancePosition(0, 0)

        // A pre-Stage-30 save: committed values plus an Undo history, and no pencil field at all.
        val wrong = decodeLegacy(puzzle, "1.11" + "0101" + "1010" + "1100")
        val correct = decodeLegacy(puzzle, "0.11" + "0101" + "1010" + "1100")

        assertEquals(BalanceCellStatus.INCORRECT, wrong.statusAt(editable))
        assertFalse(wrong.isLocked(editable))
        assertEquals(BalanceCellStatus.CORRECT, correct.statusAt(editable))
        assertTrue(correct.isLocked(editable))
        assertTrue(wrong.pencilMarks.isEmpty())
        assertEquals(0, wrong.hintsUsed)
    }

    @Test
    fun decodeRejectsUnsupportedOrCorruptedSessions() {
        val puzzle = testPuzzle()
        val encoded = BalanceSessionCodec.encode(BalanceGameEngine(puzzle).start())

        assertThrows(IllegalArgumentException::class.java) {
            BalanceSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = BalanceSessionCodec.SESSION_FORMAT_VERSION + 1,
                gameplayPayload = encoded.gameplayPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BalanceSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = BalanceSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload.replace("cells=..", "cells=."),
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }
    }

    private fun decodeLegacy(
        puzzle: BalancePuzzle,
        cells: String,
    ) = BalanceSessionCodec.decode(
        puzzle = puzzle,
        sessionFormatVersion = 1,
        gameplayPayload = "size=4\ncells=$cells\nhint=-",
        hintsUsed = 0,
        status = "IN_PROGRESS",
    )

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
