package com.stanisryz.logica.puzzle.core.nonogram

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1

/** Board size and picture density of one Nonogram difficulty. */
data class NonogramGenerationProfile(
    val size: Int,
    /** Chance in percent that a cell of the left half is filled; the right half mirrors it. */
    val fillPercent: Int,
    val maximumAttempts: Int,
)

/**
 * Nonogram Generator V1: a mirror-symmetric picture drawn from the project's own random stream,
 * accepted only when row and column line logic alone completes it, so every level has exactly one
 * answer and never needs a guess. Difficulty is the board size (5, 8, 10, 12). A seed whose bounded
 * attempts all fail is rejected rather than replaced, so the frozen level pack skips it.
 */
class NonogramGeneratorV1 : PuzzleGenerator<NonogramPuzzle> {
    override val type = PuzzleType.NONOGRAM
    override val version = GeneratorVersion(1)

    override fun generate(
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ): NonogramPuzzle {
        val profile = profileFor(difficulty)
        val random = PuzzleRandomV1(seed)
        val id = PuzzleId(type, difficulty, seed, version)
        repeat(profile.maximumAttempts) {
            val picture = picture(profile, random)
            if (picture.none { it } || picture.all { it }) return@repeat
            val puzzle = NonogramPuzzle(id, profile.size, picture)
            val solved = NonogramLineSolver.solve(puzzle.size, puzzle.rowClues, puzzle.columnClues)
            if (solved != null) return puzzle
        }
        error("Unable to generate a ${difficulty.name} Nonogram for seed ${seed.value} within ${profile.maximumAttempts} attempts.")
    }

    private fun picture(
        profile: NonogramGenerationProfile,
        random: PuzzleRandomV1,
    ): List<Boolean> {
        val size = profile.size
        val cells = BooleanArray(size * size)
        val half = (size + 1) / 2
        for (row in 0 until size) {
            for (column in 0 until half) {
                val filled = random.nextInt(100) < profile.fillPercent
                cells[row * size + column] = filled
                cells[row * size + (size - 1 - column)] = filled
            }
        }
        return cells.toList()
    }

    companion object {
        fun profileFor(difficulty: Difficulty): NonogramGenerationProfile =
            when (difficulty) {
                Difficulty.EASY -> NonogramGenerationProfile(size = 5, fillPercent = 60, maximumAttempts = 64)
                Difficulty.MEDIUM -> NonogramGenerationProfile(size = 8, fillPercent = 60, maximumAttempts = 64)
                Difficulty.HARD -> NonogramGenerationProfile(size = 10, fillPercent = 58, maximumAttempts = 64)
                Difficulty.EXPERT -> NonogramGenerationProfile(size = 12, fillPercent = 56, maximumAttempts = 64)
            }
    }
}
