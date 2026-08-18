package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class DailyPuzzleSeedV1Test {
    @Test
    fun derivationIsStableAndSensitiveToEveryInput() {
        val date = LocalDate.of(2026, 1, 1)
        val version = GeneratorVersion(1)
        val seed = DailyPuzzleSeedV1.derive(date, PuzzleType.SUDOKU, version)

        assertEquals(
            "c41c4360a646495b",
            java.lang.Long.toUnsignedString(seed.value, 16),
        )
        assertEquals(seed, DailyPuzzleSeedV1.derive(date, PuzzleType.SUDOKU, version))
        assertNotEquals(seed, DailyPuzzleSeedV1.derive(date.plusDays(1), PuzzleType.SUDOKU, version))
        assertNotEquals(seed, DailyPuzzleSeedV1.derive(date, PuzzleType.MOSAIC, version))
        assertNotEquals(seed, DailyPuzzleSeedV1.derive(date, PuzzleType.SUDOKU, GeneratorVersion(2)))
    }
}
