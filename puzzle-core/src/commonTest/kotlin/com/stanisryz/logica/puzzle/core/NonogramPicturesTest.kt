package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV2
import com.stanisryz.logica.puzzle.core.nonogram.NonogramLineSolver
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPictureSetV1
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPuzzle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NonogramPicturesTest {
    @Test
    fun everyPictureIsSolvedByLineLogicAlone() {
        repeat(NonogramPictureSetV1.COUNT) { index ->
            val puzzle =
                NonogramPuzzle(
                    PuzzleId(PuzzleType.NONOGRAM, Difficulty.MEDIUM, PuzzleSeed(index.toLong()), GeneratorVersion(2)),
                    NonogramPictureSetV1.SIZE,
                    NonogramPictureSetV1.picture(index),
                )
            assertNotNull(
                NonogramLineSolver.solve(puzzle.size, puzzle.rowClues, puzzle.columnClues),
                "Picture ${NonogramPictureSetV1.KEYS[index]} needs a guess.",
            )
        }
    }

    @Test
    fun consecutiveDaysShowEveryPictureOncePerCycle() {
        val count = NonogramPictureSetV1.COUNT.toLong()
        val start = 20_000L - 20_000L % count
        val cycle = (start until start + count).map { NonogramPictureSetV1.indexFor(PuzzleSeed(it)) }
        assertEquals((0 until NonogramPictureSetV1.COUNT).toSet(), cycle.toSet())
        val generator = NonogramGeneratorV2()
        assertEquals(generator.generate(PuzzleSeed(20_123L), Difficulty.MEDIUM), generator.generate(PuzzleSeed(20_123L), Difficulty.MEDIUM))
    }
}
