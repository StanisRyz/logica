package com.stanisryz.logica.puzzle.core.random

interface PuzzleRandom {
    fun nextLong(): Long

    fun nextInt(bound: Int): Int

    fun nextBoolean(): Boolean

    fun <T> shuffle(values: MutableList<T>)
}
