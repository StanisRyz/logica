package com.stanisryz.logica.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressMathTest {
    @Test
    fun `daily progress covers every V4 step`() {
        assertEquals(0f, progressFraction(0, 3))
        assertEquals(1f / 3f, progressFraction(1, 3))
        assertEquals(2f / 3f, progressFraction(2, 3))
        assertEquals(1f, progressFraction(3, 3))
    }

    @Test
    fun `daily progress stays defined without entries`() {
        assertEquals(0f, progressFraction(0, 0))
        assertEquals(0f, progressFraction(2, 0))
    }

    @Test
    fun `attempt bars are relative to the busiest bucket`() {
        assertEquals(1f, barFraction(4, 4))
        assertEquals(0.5f, barFraction(2, 4))
        assertEquals(0f, barFraction(0, 4))
    }

    @Test
    fun `attempt bars stay empty without any solved game`() {
        assertEquals(0f, barFraction(0, 0))
    }
}
