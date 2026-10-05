package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CrownsGameplayTest {
    @Test
    fun crownsAreCheckedWhileMarksAndPencilCrownsStayNotes() {
        val engine = CrownsGameEngine(puzzle())
        val solutionCell = CrownsPosition(2, 0)
        val draftCell = CrownsPosition(0, 0)
        val initial = engine.start()

        // A mark on a cell that really holds a crown is only a note: no status, no mistake, no lock.
        val marked = engine.placeValue(initial, solutionCell, CrownsPlayerCell.MARKED)
        assertEquals(CrownsPlayerCell.MARKED, marked.cellAt(solutionCell))
        assertEquals(CrownsCellStatus.EMPTY, marked.statusAt(solutionCell))
        assertEquals(0, marked.mistakesUsed)
        assertFalse(marked.isLocked(solutionCell))
        // Tapping the mark again takes it away.
        assertEquals(initial, engine.placeValue(marked, solutionCell, CrownsPlayerCell.MARKED))

        // A crown replaces the mark and is checked as before.
        val crowned = engine.placeValue(marked, solutionCell, CrownsPlayerCell.CROWN)
        assertEquals(CrownsCellStatus.CORRECT, crowned.statusAt(solutionCell))
        assertTrue(crowned.userMarks.isEmpty())
        assertSame(crowned, engine.placeValue(crowned, solutionCell, CrownsPlayerCell.CROWN))
        assertSame(crowned, engine.togglePencilMark(crowned, solutionCell, CrownsPlayerCell.MARKED))

        // A pencil crown is a hypothesis; the mark tool in pencil mode places the same note as outside it.
        val drafted = engine.togglePencilMark(crowned, draftCell, CrownsPlayerCell.CROWN)
        assertEquals(setOf(CrownsPlayerCell.CROWN), drafted.pencilAt(draftCell))
        assertEquals(CrownsPlayerCell.EMPTY, drafted.cellAt(draftCell))
        val pencilMarked = engine.togglePencilMark(drafted, draftCell, CrownsPlayerCell.MARKED)
        assertEquals(CrownsPlayerCell.MARKED, pencilMarked.cellAt(draftCell))
        assertTrue(pencilMarked.pencilAt(draftCell).isEmpty())
        assertEquals(drafted.userMarks + draftCell, pencilMarked.userMarks)

        val restored =
            engine.restore(
                board = drafted.board,
                userMarks = drafted.userMarks,
                pencilCrowns = drafted.pencilCrowns,
                mistakesUsed = drafted.mistakesUsed,
                hintsUsed = drafted.hintsUsed,
                currentHint = drafted.currentHint,
            )
        assertEquals(drafted, restored)

        val hinted = engine.requestHint(drafted)
        assertEquals(1, hinted.hintsUsed)
        assertNotNull(hinted.currentHint)
        assertSame(hinted, engine.requestHint(hinted))
        assertEquals(0, hinted.mistakesUsed)
    }

    @Test
    fun theThirdCommittedMistakeEndsTheAttemptAndFreezesTheBoard() {
        val engine = CrownsGameEngine(puzzle())
        val locked = CrownsPosition(2, 0)

        var game = engine.placeValue(engine.start(), locked, CrownsPlayerCell.CROWN)
        assertEquals(CrownsCellStatus.CORRECT, game.statusAt(locked))
        assertEquals(0, game.mistakesUsed)

        game = engine.placeValue(game, CrownsPosition(0, 0), CrownsPlayerCell.CROWN)
        // A mark on a real crown is a note and costs nothing; a second wrong crown does.
        game = engine.placeValue(game, CrownsPosition(1, 3), CrownsPlayerCell.MARKED)
        assertEquals(1, game.mistakesUsed)
        game = engine.placeValue(game, CrownsPosition(3, 3), CrownsPlayerCell.CROWN)
        assertEquals(2, game.mistakesUsed)
        assertEquals(CrownsGameStatus.IN_PROGRESS, game.status)
        // Pencil marks are hypotheses and never cost a mistake.
        game = engine.togglePencilMark(game, CrownsPosition(3, 0), CrownsPlayerCell.CROWN)
        assertEquals(2, game.mistakesUsed)

        val failed = engine.placeValue(game, CrownsPosition(0, 2), CrownsPlayerCell.CROWN)
        assertEquals(3, failed.mistakesUsed)
        assertEquals(CrownsGameStatus.FAILED, failed.status)
        // The wrong values stay exactly as the player left them, and the board goes read-only.
        assertEquals(CrownsPlayerCell.CROWN, failed.cellAt(CrownsPosition(0, 2)))
        assertEquals(CrownsCellStatus.INCORRECT, failed.statusAt(CrownsPosition(0, 2)))
        assertEquals(CrownsCellStatus.CORRECT, failed.statusAt(locked))
        assertSame(failed, engine.placeValue(failed, CrownsPosition(0, 2), CrownsPlayerCell.CROWN))
        assertSame(failed, engine.togglePencilMark(failed, CrownsPosition(3, 0), CrownsPlayerCell.MARKED))
        assertSame(failed, engine.requestHint(failed))

        // Retrying is a brand-new attempt at the same puzzle.
        val retried = engine.start()
        assertEquals(0, retried.mistakesUsed)
        assertTrue(retried.board.crowns.isEmpty())
        assertEquals(CrownsGameStatus.IN_PROGRESS, retried.status)
    }

    @Test
    fun invalidCrownsAreAllowedAndMarksDoNotPreventCompletion() {
        val engine = CrownsGameEngine(puzzle())
        var game = engine.start()

        game = engine.placeValue(game, CrownsPosition(0, 0), CrownsPlayerCell.CROWN)
        game = engine.placeValue(game, CrownsPosition(0, 2), CrownsPlayerCell.CROWN)

        assertEquals(CrownsGameStatus.IN_PROGRESS, game.status)
        assertEquals(CrownsCellStatus.INCORRECT, game.statusAt(CrownsPosition(0, 0)))
        assertTrue(game.violations.any { it.type == CrownsViolationType.ROW_CONFLICT })
        assertTrue(game.violations.any { it.type == CrownsViolationType.REGION_CONFLICT })

        game = engine.start()
        game = engine.placeValue(game, CrownsPosition(0, 3), CrownsPlayerCell.MARKED)
        solutionPositions.forEach { position -> game = engine.placeValue(game, position, CrownsPlayerCell.CROWN) }

        assertEquals(CrownsGameStatus.SOLVED, game.status)
        assertTrue(game.violations.isEmpty())
        assertEquals(CrownsPlayerCell.MARKED, game.cellAt(CrownsPosition(0, 3)))
        assertEquals(CrownsCellStatus.EMPTY, game.statusAt(CrownsPosition(0, 3)))

        // All crowns in place is enough: no cell needs a mark.
        var crownsOnly = engine.start()
        solutionPositions.forEach { position -> crownsOnly = engine.placeValue(crownsOnly, position, CrownsPlayerCell.CROWN) }
        assertEquals(CrownsGameStatus.SOLVED, crownsOnly.status)
        assertTrue(crownsOnly.userMarks.isEmpty())
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

    @Test
    fun revealedHintsOpenOnlyCrownsAndLeaveMarksAloneExceptOnTheirOwnCell() {
        val engine = CrownsGameEngine(puzzle())
        val solutionCell = CrownsPosition(2, 0)
        val noteCell = CrownsPosition(0, 0)
        var game = engine.placeValue(engine.start(), solutionCell, CrownsPlayerCell.MARKED)
        game = engine.placeValue(game, noteCell, CrownsPlayerCell.MARKED)

        // The first logical crown is (2, 0): the mark there gives way, the other mark stays untouched.
        game = engine.revealHint(game)
        assertEquals(CrownsPlayerCell.CROWN, game.cellAt(solutionCell))
        assertEquals(CrownsCellStatus.CORRECT, game.statusAt(solutionCell))
        assertEquals(setOf(noteCell), game.userMarks)
        assertEquals(1, game.hintsUsed)

        // Every reveal opens one crown, so the hints alone reach the solution.
        while (game.status == CrownsGameStatus.IN_PROGRESS) {
            val before = game
            game = engine.revealHint(game)
            assertEquals(before.board.crowns.size + 1, game.board.crowns.size)
        }
        assertEquals(CrownsGameStatus.SOLVED, game.status)
        assertEquals(4, game.hintsUsed)
        assertEquals(0, game.mistakesUsed)
        assertSame(game, engine.revealHint(game))
    }

    @Test
    fun aRevealTakesAWrongCrownAwayBeforeOpeningTheNextOne() {
        val engine = CrownsGameEngine(puzzle())
        val wrong = engine.placeValue(engine.start(), CrownsPosition(0, 0), CrownsPlayerCell.CROWN)
        assertEquals(1, wrong.mistakesUsed)

        val fixed = engine.revealHint(wrong)
        assertEquals(CrownsPlayerCell.EMPTY, fixed.cellAt(CrownsPosition(0, 0)))
        assertEquals(1, fixed.mistakesUsed)
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
