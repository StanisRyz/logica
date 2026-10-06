package com.stanisryz.logica.puzzle.core.nonogram

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1

/** Board size, density, and accepted line-logic effort of one Nonogram V4 difficulty. */
data class NonogramV4Profile(
    val size: Int,
    /** Chance in percent that a cell of the left half is filled; the right half mirrors it. */
    val fillPercent: Int,
    /** The accepted range of [NonogramSolution.sweeps]; Hard and Expert share a size and differ here. */
    val sweeps: IntRange,
    val maximumAttempts: Int,
)

/**
 * Nonogram Generator V4: the symmetric levels between the real pictures of a future level pack. It is
 * Generator V1's algorithm — a mirror-symmetric picture from the project's own random stream, accepted
 * only when line logic alone completes it — at the picture library's sizes (10, 12, 15, 15), so a
 * symmetric level is as large as the pictures next to it. Hard and Expert share 15x15 and accept only
 * pictures whose solve takes their own range of passes. A seed whose bounded attempts all fail is
 * rejected rather than replaced. V1 is unchanged; nothing in the game uses V4 yet.
 */
class NonogramGeneratorV4 : PuzzleGenerator<NonogramPuzzle> {
    override val type = PuzzleType.NONOGRAM
    override val version = GeneratorVersion(4)

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
            val solution = NonogramLineSolver.solveWithEffort(puzzle.size, puzzle.rowClues, puzzle.columnClues)
            if (solution != null && solution.sweeps in profile.sweeps) return puzzle
        }
        error("Unable to generate a ${difficulty.name} Nonogram V4 for seed ${seed.value} within ${profile.maximumAttempts} attempts.")
    }

    private fun picture(
        profile: NonogramV4Profile,
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
        fun profileFor(difficulty: Difficulty): NonogramV4Profile =
            when (difficulty) {
                Difficulty.EASY -> NonogramV4Profile(size = 10, fillPercent = 60, sweeps = 1..Int.MAX_VALUE, maximumAttempts = 64)
                Difficulty.MEDIUM -> NonogramV4Profile(size = 12, fillPercent = 58, sweeps = 1..Int.MAX_VALUE, maximumAttempts = 64)
                Difficulty.HARD -> NonogramV4Profile(size = 15, fillPercent = 55, sweeps = 1..HARD_MAX_SWEEPS, maximumAttempts = 64)
                Difficulty.EXPERT ->
                    NonogramV4Profile(size = 15, fillPercent = 55, sweeps = HARD_MAX_SWEEPS + 1..Int.MAX_VALUE, maximumAttempts = 64)
            }

        /** At 15x15 and 55 % fill about half of the solvable pictures take at most four passes. */
        const val HARD_MAX_SWEEPS = 4
    }
}
