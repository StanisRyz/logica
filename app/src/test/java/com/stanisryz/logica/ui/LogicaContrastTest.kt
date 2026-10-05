package com.stanisryz.logica.ui

import androidx.compose.ui.graphics.Color
import com.stanisryz.logica.ui.theme.LogicaContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every checked theme pair keeps its WCAG threshold, so a token change cannot quietly drop below it. */
class LogicaContrastTest {
    @Test
    fun theRatioFollowsWcag() {
        assertEquals(21.0, LogicaContrast.ratio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, LogicaContrast.ratio(Color(0xFF777777), Color(0xFF777777)), 0.0001)
        assertEquals(4.48, LogicaContrast.ratio(Color(0xFF777777), Color.White), 0.01)
    }

    @Test
    fun everyCheckedPairMeetsItsThreshold() {
        val failing = LogicaContrast.checkedPairs().filter { it.ratio < it.minimum }
        assertTrue(
            failing.joinToString("\n") { "${it.name}: ${"%.2f".format(it.ratio)} < ${it.minimum}" },
            failing.isEmpty(),
        )
    }
}
