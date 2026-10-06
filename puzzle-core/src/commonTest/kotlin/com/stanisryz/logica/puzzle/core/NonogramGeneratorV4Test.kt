package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV4
import com.stanisryz.logica.puzzle.core.nonogram.NonogramKnowledge
import com.stanisryz.logica.puzzle.core.nonogram.NonogramLineSolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NonogramGeneratorV4Test {
    private val generator = NonogramGeneratorV4()

    @Test
    fun symmetricLevelsHaveThePictureSizesAndLineLogicSolvesThemInTheirEffortRange() {
        val sizes = mapOf(Difficulty.EASY to 10, Difficulty.MEDIUM to 12, Difficulty.HARD to 15, Difficulty.EXPERT to 15)
        sizes.forEach { (difficulty, size) ->
            (1L..30L).forEach { seed ->
                val puzzle = generator.generate(PuzzleSeed(seed), difficulty)
                assertEquals(size, puzzle.size)
                assertEquals(GeneratorVersion(4), puzzle.id.generatorVersion)
                (0 until size).forEach { row ->
                    (0 until size).forEach { column ->
                        assertEquals(puzzle.isFilled(row, column), puzzle.isFilled(row, size - 1 - column), "mirror symmetry")
                    }
                }
                val solution = NonogramLineSolver.solveWithEffort(size, puzzle.rowClues, puzzle.columnClues)
                assertEquals(puzzle.solution, solution?.board?.map { it == NonogramKnowledge.FILLED })
                assertTrue(solution!!.sweeps in NonogramGeneratorV4.profileFor(difficulty).sweeps)
            }
        }
        // Hard and Expert share 15x15 and are told apart by effort.
        assertTrue(
            NonogramGeneratorV4.profileFor(Difficulty.HARD).sweeps.last < NonogramGeneratorV4.profileFor(Difficulty.EXPERT).sweeps.first,
        )
    }

    /** Recorded on the JVM; the same on every target, since a frozen pack will depend on it. */
    @Test
    fun outputsMatchTheRecordedFingerprints() {
        val fingerprints =
            Difficulty.entries.flatMap { difficulty ->
                listOf(1L, 2L).map { seed ->
                    val cells = generator.generate(PuzzleSeed(seed), difficulty).solution
                    var hash = FNV_OFFSET
                    cells.forEach { hash = (hash xor if (it) 1L else 0L) * FNV_PRIME }
                    "${cells.count { it }}:${hash.toULong().toString(16)}"
                }
            }
        assertEquals(GOLDEN, fingerprints.joinToString(" "))
    }

    @Test
    fun theSameSeedGivesTheSamePicture() {
        Difficulty.entries.forEach { difficulty ->
            assertEquals(generator.generate(PuzzleSeed(77L), difficulty), generator.generate(PuzzleSeed(77L), difficulty))
        }
    }

    private companion object {
        const val FNV_OFFSET = -3750763034362895579L
        const val FNV_PRIME = 1099511628211L
        const val GOLDEN =
            "56:6356e0c76f785ea1 68:780c87dcdaec94f9 82:dde3ac60ff525077 92:7b85eb5c6ab5e91 " +
                "123:2b02e5179a1dda80 139:ef2ac9479871c6fa 131:b67f8ead7dcf2028 120:c4da77f69bccbb3d"
    }
}
