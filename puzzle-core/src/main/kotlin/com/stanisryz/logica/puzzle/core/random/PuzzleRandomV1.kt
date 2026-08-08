package com.stanisryz.logica.puzzle.core.random

import com.stanisryz.logica.puzzle.core.model.PuzzleSeed

/** Stable SplitMix64 implementation. Changes require a new random version. */
class PuzzleRandomV1(
    seed: PuzzleSeed,
) : PuzzleRandom {
    private var state = seed.value

    override fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var value = state
        value = (value xor (value ushr 30)) * MIX_MULTIPLIER_1
        value = (value xor (value ushr 27)) * MIX_MULTIPLIER_2
        return value xor (value ushr 31)
    }

    override fun nextInt(bound: Int): Int {
        require(bound > 0) { "Bound must be positive." }
        val longBound = bound.toLong()

        while (true) {
            val bits = nextLong().ushr(1)
            val value = bits % longBound
            if (bits - value + (longBound - 1) >= 0) {
                return value.toInt()
            }
        }
    }

    override fun nextBoolean(): Boolean = nextLong() and 1L == 0L

    override fun <T> shuffle(values: MutableList<T>) {
        for (index in values.lastIndex downTo 1) {
            val swapIndex = nextInt(index + 1)
            val current = values[index]
            values[index] = values[swapIndex]
            values[swapIndex] = current
        }
    }

    private companion object {
        const val GOLDEN_GAMMA = -7046029254386353131L
        const val MIX_MULTIPLIER_1 = -4658895280553007687L
        const val MIX_MULTIPLIER_2 = -7723592293110705685L
    }
}
