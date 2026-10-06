package com.stanisryz.logica.puzzle.core.nonogram

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The candidate picture library (datasets/nonogram/library-v1, built by tools/nonogram) holds only
 * pictures the shipped line solver completes on its own, with no repeats or mirror repeats per size.
 * Nothing in the game reads this library yet.
 */
class NonogramPictureLibraryTest {
    private val root = listOf(File("."), File("..")).first { File(it, "datasets/nonogram/library-v1").isDirectory }

    @Test
    fun everyLibraryPictureIsSolvedByLineLogicAloneAndAppearsOnce() {
        val sizes = mapOf("easy" to 10, "medium" to 12, "hard" to 15, "expert" to 15)
        val seenBySize = mutableMapOf<Int, MutableSet<List<Boolean>>>()
        sizes.forEach { (difficulty, size) ->
            val pictures =
                File(root, "datasets/nonogram/library-v1/$difficulty.txt")
                    .readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { line -> line.split('\t').let { it[0] to it[1] } }
            // Owner decision (stage 10.2a): at least 150 pictures per difficulty, and no alcohol.
            assertTrue(pictures.size >= 150, "$difficulty has only ${pictures.size} pictures")
            val alcohol = listOf("beer", "wine", "brandy", "champagne", "cheers", "martini", "pint-glass", "liquor")
            assertTrue(pictures.none { (key, _) -> alcohol.any { it in key } }, "$difficulty holds an alcohol picture")
            pictures.forEach { (key, rows) ->
                val cells = rows.filter { it != '/' }.map { it == '#' }
                assertEquals(size * size, cells.size, "$difficulty/$key has the wrong size")
                val rowClues = (0 until size).map { row -> NonogramClues.of(cells.subList(row * size, (row + 1) * size)) }
                val columnClues = (0 until size).map { column -> NonogramClues.of((0 until size).map { cells[it * size + column] }) }
                val solved = NonogramLineSolver.solve(size, rowClues, columnClues)
                assertEquals(cells, solved?.map { it == NonogramKnowledge.FILLED }, "$difficulty/$key is not solved by line logic")
                val mirror = (0 until size).flatMap { row -> (size - 1 downTo 0).map { cells[row * size + it] } }
                val seen = seenBySize.getOrPut(size) { mutableSetOf() }
                assertTrue(cells !in seen && mirror !in seen, "$difficulty/$key repeats another $size x $size picture")
                seen += cells
            }
        }
    }
}
