package com.stanisryz.logica.puzzle.core.random

import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import org.junit.Assert.assertEquals
import org.junit.Test

class PuzzleRandomV1Test {
    @Test
    fun sequenceIsStable() {
        val random = PuzzleRandomV1(PuzzleSeed(0))

        val sequence =
            List(5) {
                java.lang.Long
                    .toUnsignedString(random.nextLong(), 16)
                    .padStart(16, '0')
            }

        assertEquals(
            listOf(
                "e220a8397b1dcdaf",
                "6e789e6aa1b965f4",
                "06c45d188009454f",
                "f88bb8a8724c81ec",
                "1b39896a51a8749b",
            ),
            sequence,
        )
    }
}
