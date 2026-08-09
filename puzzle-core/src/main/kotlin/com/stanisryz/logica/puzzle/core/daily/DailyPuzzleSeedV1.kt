package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.time.LocalDate

/** Stable FNV-1a derivation. Changes require a new daily seed version. */
object DailyPuzzleSeedV1 {
    fun derive(
        date: LocalDate,
        puzzleType: PuzzleType,
        generatorVersion: GeneratorVersion,
    ): PuzzleSeed {
        var hash = FNV_OFFSET_BASIS
        hash = mixLong(hash, date.year.toLong())
        hash = mixLong(hash, date.monthValue.toLong())
        hash = mixLong(hash, date.dayOfMonth.toLong())
        hash = mixLong(hash, puzzleType.stableCode.toLong())
        hash = mixLong(hash, generatorVersion.value.toLong())
        return PuzzleSeed(hash)
    }

    private fun mixLong(
        initialHash: Long,
        input: Long,
    ): Long {
        var hash = initialHash
        var value = input
        repeat(Long.SIZE_BYTES) {
            hash = (hash xor (value and 0xffL)) * FNV_PRIME
            value = value ushr Byte.SIZE_BITS
        }
        return hash
    }

    private val PuzzleType.stableCode: Int
        get() =
            when (this) {
                PuzzleType.BALANCE -> 1
                PuzzleType.ROUTE -> 2
                PuzzleType.CROWNS -> 3
                PuzzleType.SUDOKU -> 4
                PuzzleType.MOSAIC -> 5
                PuzzleType.WATER_JUGS -> 6
                PuzzleType.WORD -> 7
            }

    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
}
