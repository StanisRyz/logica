package com.stanisryz.logica.crowns

import com.stanisryz.logica.puzzle.core.crowns.CrownsCellStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsHintAction
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.crowns.RegionId
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

class CrownsSessionCodecTest {
    @Test
    fun roundTripPreservesCommittedValuesPencilMarksAndHint() {
        val puzzle = testPuzzle()
        val engine = CrownsGameEngine(puzzle)
        val solutionCell = CrownsPosition(2, 0)
        val wrongMark = CrownsPosition(0, 1)
        val draftCell = CrownsPosition(1, 1)

        var game = engine.placeValue(engine.start(), solutionCell, CrownsPlayerCell.CROWN)
        game = engine.placeValue(game, wrongMark, CrownsPlayerCell.MARKED)
        game = engine.togglePencilMark(game, draftCell, CrownsPlayerCell.CROWN)
        game = engine.togglePencilMark(game, draftCell, CrownsPlayerCell.MARKED)
        game = engine.requestHint(game)
        val encoded = CrownsSessionCodec.encode(puzzle, game)

        val restored =
            CrownsSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = CrownsSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )

        assertEquals(game, restored)
        assertEquals(CrownsHintAction.CLEAR_MARK, restored.currentHint?.action)
        assertEquals(CrownsCellStatus.CORRECT, restored.statusAt(solutionCell))
        assertEquals(CrownsCellStatus.INCORRECT, restored.statusAt(wrongMark))
        assertEquals(
            setOf(CrownsPlayerCell.CROWN, CrownsPlayerCell.MARKED),
            restored.pencilAt(draftCell),
        )
        assertEquals("", encoded.moveHistoryPayload)
    }

    @Test
    fun preStagePlayerValuesRestoreAsCorrectOrWrongWithoutPencilMarks() {
        val puzzle = testPuzzle()
        val solutionCell = CrownsPosition(2, 0)
        val wrongMark = CrownsPosition(0, 1)

        // A pre-Stage-30 save: crowns and marks only, and no pencil fields at all.
        val restored =
            CrownsSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = 1,
                gameplayPayload = "size=4\ncrowns=2:0\nmarks=0:1\nhint=-",
                hintsUsed = 0,
                status = "IN_PROGRESS",
            )

        assertEquals(CrownsCellStatus.CORRECT, restored.statusAt(solutionCell))
        assertTrue(restored.isLocked(solutionCell))
        assertEquals(CrownsCellStatus.INCORRECT, restored.statusAt(wrongMark))
        assertFalse(restored.isLocked(wrongMark))
        assertTrue(restored.pencilCrowns.isEmpty() && restored.pencilMarks.isEmpty())
        assertEquals(0, restored.hintsUsed)
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
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrownsSessionCodec.decode(
                puzzle = puzzle,
                sessionFormatVersion = CrownsSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload.replace("size=4", "size=5"),
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
