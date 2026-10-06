package com.stanisryz.logica.puzzle.core.nonogram.quality

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.nonogram.NonogramClues
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV4
import com.stanisryz.logica.puzzle.core.nonogram.NonogramLineSolver
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPictureLibraryV3
import java.io.File

/**
 * Developer-only freeze of the Nonogram V3 picture library. It reads the candidate library
 * (`datasets/nonogram/library-v1`), measures every picture with the shipped [NonogramLineSolver]
 * (passes over all rows, then all columns), and writes `nonogram/v3/<difficulty>.txt`. Easy and Medium
 * keep their candidates; the 15x15 Hard and Expert candidates are pooled and split again by the
 * same measure as Generator V4 — Hard at most [NonogramGeneratorV4.HARD_MAX_SWEEPS] passes, Expert
 * more — so the real pictures and the symmetric levels share one rule. A file that already exists is
 * only verified: the library is frozen once written.
 *
 * Usage: `./gradlew :puzzle-core:nonogramLibraryV3Prepare`.
 */
object NonogramLibraryV3Prepare {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Expected <candidate-library-dir> <resource-dir>." }
        val candidates = File(args[0])
        val output = File(args[1])
        val measured =
            CANDIDATE_FILES.flatMap { (file, size) ->
                read(File(candidates, file)).map { (key, rows) -> Picture(file.removeSuffix(".txt"), key, rows, size, sweeps(size, rows)) }
            }
        val byDifficulty =
            mapOf(
                Difficulty.EASY to measured.filter { it.source == "easy" },
                Difficulty.MEDIUM to measured.filter { it.source == "medium" },
                Difficulty.HARD to measured.filter { it.size == 15 && it.sweeps <= NonogramGeneratorV4.HARD_MAX_SWEEPS },
                Difficulty.EXPERT to measured.filter { it.size == 15 && it.sweeps > NonogramGeneratorV4.HARD_MAX_SWEEPS },
            ).mapValues { (_, pictures) -> pictures.sortedBy { it.key } }
        var changed = false
        byDifficulty.forEach { (difficulty, pictures) ->
            check(pictures.map { it.key }.distinct().size == pictures.size) { "${difficulty.name} repeats a key." }
            val text = render(difficulty, pictures)
            val target = File(output, NonogramPictureLibraryV3.resourcePath(difficulty).removePrefix("/"))
            val sweeps = pictures.groupingBy { it.sweeps }.eachCount().toSortedMap()
            val moved = pictures.count { it.size == 15 && it.source != difficulty.name.lowercase() }
            println("  ${difficulty.name}: ${pictures.size} pictures, passes $sweeps, moved from the other 15x15 half: $moved")
            if (!target.exists()) {
                target.parentFile.mkdirs()
                target.writeText(text)
                changed = true
            } else {
                check(target.readText() == text) {
                    "Generated ${target.path} differs from the frozen V3 library. Make a new library version instead."
                }
            }
        }
        println(if (changed) "Nonogram V3 library written." else "Nonogram V3 library verified.")
    }

    private data class Picture(
        val source: String,
        val key: String,
        val rows: String,
        val size: Int,
        val sweeps: Int,
    )

    private fun read(file: File): List<Pair<String, String>> =
        file
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> line.split('\t').let { it[0] to it[1] } }

    private fun sweeps(
        size: Int,
        rows: String,
    ): Int {
        val cells = rows.filter { it != '/' }.map { it == '#' }
        val rowClues = (0 until size).map { row -> NonogramClues.of(cells.subList(row * size, (row + 1) * size)) }
        val columnClues = (0 until size).map { column -> NonogramClues.of((0 until size).map { cells[it * size + column] }) }
        val solution = checkNotNull(NonogramLineSolver.solveWithEffort(size, rowClues, columnClues)) { "Line logic does not solve $rows." }
        return solution.sweeps
    }

    private fun render(
        difficulty: Difficulty,
        pictures: List<Picture>,
    ): String =
        buildString {
            val size = NonogramPictureLibraryV3.sizeFor(difficulty)
            append(
                "# Nonogram picture library V3, ${difficulty.name.lowercase()}: ${size}x$size. FROZEN: the line order fixes every V3 seed.\n",
            )
            append("# Made by :puzzle-core:nonogramLibraryV3Prepare from datasets/nonogram/library-v1; do not edit.\n")
            append("# <key>\\t<rows top to bottom, '/' between rows, '#' filled>\\t<line-logic passes, NonogramLineSolver>\n")
            pictures.forEach { append("${it.key}\t${it.rows}\t${it.sweeps}\n") }
        }

    private val CANDIDATE_FILES = listOf("easy.txt" to 10, "medium.txt" to 12, "hard.txt" to 15, "expert.txt" to 15)
}
