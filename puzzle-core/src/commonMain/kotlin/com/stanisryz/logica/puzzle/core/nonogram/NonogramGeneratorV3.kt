package com.stanisryz.logica.puzzle.core.nonogram

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.BundledWordResources

/**
 * Nonogram Generator V3: the real pictures of Catalog Level Pack V2. The seed is the index of one
 * picture in the frozen [NonogramPictureLibraryV3] of its difficulty, and the pack's odd slots hold
 * those indices in a frozen shuffled order. An index outside the library is rejected, never wrapped.
 */
class NonogramGeneratorV3 : PuzzleGenerator<NonogramPuzzle> {
    override val type = PuzzleType.NONOGRAM
    override val version = GeneratorVersion(3)

    override fun generate(
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ): NonogramPuzzle {
        val count = NonogramPictureLibraryV3.count(difficulty)
        require(seed.value in 0 until count) { "Nonogram V3 ${difficulty.name} has no picture ${seed.value} (of $count)." }
        val picture = NonogramPictureLibraryV3.picture(difficulty, seed.value.toInt())
        return NonogramPuzzle(PuzzleId(type, difficulty, seed, version), NonogramPictureLibraryV3.sizeFor(difficulty), picture)
    }
}

/**
 * The frozen picture library of Generator V3: one bundled text file per difficulty
 * (`nonogram/v3/<difficulty>.txt`), made once by `:puzzle-core:nonogramLibraryV3Prepare` from the
 * candidate library in `datasets/nonogram/library-v1`. Its contents and order fix what every V3 seed
 * shows, so a change needs a new library and generator version rather than an edit. The Web host
 * loads only the file of the difficulty it plays.
 */
object NonogramPictureLibraryV3 {
    fun resourcePath(difficulty: Difficulty): String = "/nonogram/v3/${difficulty.name.lowercase()}.txt"

    val RESOURCE_PATHS: List<String> get() = Difficulty.entries.map(::resourcePath)

    fun sizeFor(difficulty: Difficulty): Int =
        when (difficulty) {
            Difficulty.EASY -> 10
            Difficulty.MEDIUM -> 12
            Difficulty.HARD, Difficulty.EXPERT -> 15
        }

    fun count(difficulty: Difficulty): Int = entries(difficulty).size

    fun key(
        difficulty: Difficulty,
        index: Int,
    ): String = entries(difficulty)[index].key

    fun picture(
        difficulty: Difficulty,
        index: Int,
    ): List<Boolean> = entries(difficulty)[index].cells

    private class Entry(
        val key: String,
        val cells: List<Boolean>,
    )

    // A read that fails (a Web file not loaded yet) is not cached, so a later call reads again.
    private val easy by lazy { parse(Difficulty.EASY) }
    private val medium by lazy { parse(Difficulty.MEDIUM) }
    private val hard by lazy { parse(Difficulty.HARD) }
    private val expert by lazy { parse(Difficulty.EXPERT) }

    private fun entries(difficulty: Difficulty): List<Entry> =
        when (difficulty) {
            Difficulty.EASY -> easy
            Difficulty.MEDIUM -> medium
            Difficulty.HARD -> hard
            Difficulty.EXPERT -> expert
        }

    private fun parse(difficulty: Difficulty): List<Entry> {
        val size = sizeFor(difficulty)
        return BundledWordResources
            .readText(resourcePath(difficulty))
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val fields = line.split('\t')
                check(fields.size >= 2) { "Malformed Nonogram V3 ${difficulty.name} line: $line" }
                val cells = fields[1].filter { it != '/' }.map { it == '#' }
                check(cells.size == size * size) { "Nonogram V3 ${difficulty.name} picture ${fields[0]} is not ${size}x$size." }
                Entry(fields[0], cells)
            }.toList()
            .also { check(it.isNotEmpty()) { "Nonogram V3 ${difficulty.name} library is empty." } }
    }
}

/** Every shipped Nonogram generator by its version: Catalog V1, the Daily's V2, and Level Pack V2's V3 and V4. */
object NonogramGenerators {
    fun generate(
        seed: PuzzleSeed,
        difficulty: Difficulty,
        version: GeneratorVersion,
    ): NonogramPuzzle =
        when (version.value) {
            1 -> NonogramGeneratorV1().generate(seed, difficulty)
            2 -> NonogramGeneratorV2().generate(seed, difficulty)
            3 -> NonogramGeneratorV3().generate(seed, difficulty)
            4 -> NonogramGeneratorV4().generate(seed, difficulty)
            else -> error("No Nonogram generator ${version.value}.")
        }
}
