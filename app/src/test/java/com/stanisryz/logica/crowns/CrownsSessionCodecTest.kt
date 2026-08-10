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
    fun roundTripPreservesCommittedValuesPencilMarksMistakesAndHint() {
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
        assertEquals(1, restored.mistakesUsed)
        assertEquals("", encoded.moveHistoryPayload)
    }

    @Test
    fun olderSavesRestoreTheirValuesWithoutPencilMarksOrRetroactiveMistakes() {
        val puzzle = testPuzzle()
        val solutionCell = CrownsPosition(2, 0)
        val wrongMark = CrownsPosition(0, 1)

        // V1 predates both pencil marks and mistakes; V2 predates mistakes only.
        val v1 = decodeLegacy(puzzle, version = 1, gameplayPayload = "size=4\ncrowns=2:0\nmarks=0:1\nhint=-")
        val v2 =
            decodeLegacy(
                puzzle,
                version = 2,
                gameplayPayload = "size=4\ncrowns=2:0\nmarks=0:1\npencilCrowns=1:1\npencilMarks=-\nhint=-",
            )

        listOf(v1, v2).forEach { restored ->
            assertEquals(CrownsCellStatus.CORRECT, restored.statusAt(solutionCell))
            assertTrue(restored.isLocked(solutionCell))
            assertEquals(CrownsCellStatus.INCORRECT, restored.statusAt(wrongMark))
            assertFalse(restored.isLocked(wrongMark))
            // An already-incorrect cell never becomes a retroactive mistake.
            assertEquals(0, restored.mistakesUsed)
            assertEquals(0, restored.hintsUsed)
        }
        assertTrue(v1.pencilCrowns.isEmpty() && v1.pencilMarks.isEmpty())
        assertEquals(setOf(CrownsPosition(1, 1)), v2.pencilCrowns)
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

    private fun decodeLegacy(
        puzzle: CrownsPuzzle,
        version: Int,
        gameplayPayload: String,
    ) = CrownsSessionCodec.decode(
        puzzle = puzzle,
        sessionFormatVersion = version,
        gameplayPayload = gameplayPayload,
        hintsUsed = 0,
        status = "IN_PROGRESS",
    )

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
