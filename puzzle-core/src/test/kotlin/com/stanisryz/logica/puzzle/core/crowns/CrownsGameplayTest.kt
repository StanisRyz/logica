package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CrownsGameplayTest {
    @Test
    fun mixedTransitionsRestoreExactlyAndResetPreservesHintUsage() {
        val engine = CrownsGameEngine(puzzle())
        val position = CrownsPosition(2, 0)
        val initial = engine.start()

        val marked = engine.placeMark(initial, position)
        val crowned = engine.placeCrown(marked, position)
        val restored =
            engine.restore(
                board = crowned.board,
                userMarks = crowned.userMarks,
                moveHistory = crowned.moveHistory,
                hintsUsed = crowned.hintsUsed,
                currentHint = crowned.currentHint,
            )
        val undone = engine.undo(crowned)
        val hinted = engine.requestHint(undone)

        assertEquals(crowned, restored)
        assertEquals(CrownsPlayerCell.CROWN, crowned.cellAt(position))
        assertFalse(position in crowned.userMarks)
        assertEquals(CrownsPlayerCell.MARKED, undone.cellAt(position))
        assertFalse(position in undone.board.crowns)
        assertEquals(CrownsHintKind.INCORRECT_MARK, hinted.currentHint?.kind)
        assertEquals(1, hinted.hintsUsed)
        assertSame(hinted, engine.requestHint(hinted))

        val reset = engine.reset(hinted)
        assertTrue(reset.board.crowns.isEmpty())
        assertTrue(reset.userMarks.isEmpty())
        assertTrue(reset.moveHistory.isEmpty())
        assertEquals(1, reset.hintsUsed)
        assertNull(reset.currentHint)
    }

    @Test
    fun invalidCrownsAreAllowedAndMarksDoNotPreventCompletion() {
        val engine = CrownsGameEngine(puzzle())
        var game = engine.start()

        game = engine.placeCrown(game, CrownsPosition(0, 0))
        game = engine.placeCrown(game, CrownsPosition(0, 2))

        assertEquals(CrownsGameStatus.IN_PROGRESS, game.status)
        assertTrue(game.violations.any { it.type == CrownsViolationType.ROW_CONFLICT })
        assertTrue(game.violations.any { it.type == CrownsViolationType.REGION_CONFLICT })

        game = engine.reset(game)
        game = engine.placeMark(game, CrownsPosition(0, 3))
        solutionPositions.forEach { position -> game = engine.placeCrown(game, position) }

        assertEquals(CrownsGameStatus.SOLVED, game.status)
        assertTrue(game.violations.isEmpty())
        assertEquals(CrownsPlayerCell.MARKED, game.cellAt(CrownsPosition(0, 3)))
    }

    @Test
    fun hintsPrioritizeIncorrectCrownsThenMarksThenLogicalDeductions() {
        val puzzle = puzzle()
        val provider = CrownsHintProvider()
        val incorrectCrown = CrownsPosition(0, 0)
        val incorrectlyMarkedSolutionCrown = CrownsPosition(0, 1)

        val crownHint =
            provider.hint(
                puzzle,
                CrownsState(listOf(incorrectCrown)),
                setOf(incorrectlyMarkedSolutionCrown),
            )
        val markHint = provider.hint(puzzle, CrownsState(), setOf(incorrectlyMarkedSolutionCrown))
        val logicalHint = provider.hint(puzzle, CrownsState(), emptySet())

        assertEquals(CrownsHintKind.INCORRECT_CROWN, crownHint?.kind)
        assertEquals(CrownsHintAction.CLEAR_CROWN, crownHint?.action)
        assertEquals(setOf(incorrectCrown), crownHint?.targetPositions)
        assertEquals(CrownsHintKind.INCORRECT_MARK, markHint?.kind)
        assertEquals(CrownsHintAction.CLEAR_MARK, markHint?.action)
        assertEquals(setOf(incorrectlyMarkedSolutionCrown), markHint?.targetPositions)
        assertEquals(CrownsHintKind.LOGICAL_DEDUCTION, logicalHint?.kind)
        assertEquals(CrownsHintAction.PLACE_CROWN, logicalHint?.action)
        assertEquals(setOf(CrownsPosition(2, 0)), logicalHint?.targetPositions)
        assertEquals(CrownsLogicTechnique.SINGLE_CANDIDATE_REGION, logicalHint?.technique)
    }

    private fun puzzle(): CrownsPuzzle {
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

    private companion object {
        val solutionPositions =
            listOf(
                CrownsPosition(0, 1),
                CrownsPosition(1, 3),
                CrownsPosition(2, 0),
                CrownsPosition(3, 2),
            )
    }
}
