package com.stanisryz.logica.crowns

import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsHintAction
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.crowns.RegionId
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CrownsSessionCodecTest {
    @Test
    fun roundTripPreservesCrownsMarksHistoryAndHint() {
        val puzzle = testPuzzle()
        val engine = CrownsGameEngine(puzzle)
        var game = engine.placeMark(engine.start(), CrownsPosition(2, 0))
        game = engine.placeCrown(game, CrownsPosition(2, 0))
        game = engine.placeMark(game, CrownsPosition(0, 1))
        game = engine.requestHint(game)
        val encoded = CrownsSessionCodec.encode(puzzle, game)

        val restored =
            CrownsSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = CrownsSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload,
                moveHistoryPayload = encoded.moveHistoryPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )

        assertEquals(game, restored)
        assertEquals(CrownsHintAction.CLEAR_MARK, restored.currentHint?.action)
        val undone = engine.undo(restored)
        assertEquals(setOf(CrownsPosition(2, 0)), undone.board.crowns)
        assertEquals(emptySet<CrownsPosition>(), undone.userMarks)
        assertEquals(1, undone.hintsUsed)
    }

    @Test
    fun decodeRejectsUnsupportedOrCorruptedSessions() {
        val puzzle = testPuzzle()
        val encoded = CrownsSessionCodec.encode(puzzle, CrownsGameEngine(puzzle).start())

        assertThrows(IllegalArgumentException::class.java) {
            CrownsSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = CrownsSessionCodec.SESSION_FORMAT_VERSION + 1,
                gameplayPayload = encoded.gameplayPayload,
                moveHistoryPayload = encoded.moveHistoryPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrownsSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = CrownsSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload.replace("size=4", "size=5"),
                moveHistoryPayload = encoded.moveHistoryPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }
    }

    private fun testPuzzle(): CrownsPuzzle {
        val rows = listOf("AAAB", "ADAB", "CDDD", "DDDD")
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
                    difficulty = Difficulty.EASY,
                    seed = PuzzleSeed(91),
                    generatorVersion = GeneratorVersion(1),
                ),
            size = rows.size,
            regionAssignments = assignments,
        )
    }
}
