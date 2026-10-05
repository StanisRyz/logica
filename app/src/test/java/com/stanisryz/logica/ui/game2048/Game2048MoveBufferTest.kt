package com.stanisryz.logica.ui.game2048

import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Game2048MoveBufferTest {
    @Test
    fun idleBoardAppliesAMoveAtOnce() {
        val buffer = Game2048MoveBuffer()

        assertEquals(Game2048Direction.LEFT, buffer.request(Game2048Direction.LEFT, animating = false, inputEnabled = true))
        assertNull(buffer.pending)
    }

    @Test
    fun aMoveDuringAnimationRunsRightAfterIt() {
        val buffer = Game2048MoveBuffer()

        assertNull(buffer.request(Game2048Direction.UP, animating = true, inputEnabled = true))
        assertNull(buffer.release(animating = true, inputEnabled = true))
        assertEquals(Game2048Direction.UP, buffer.release(animating = false, inputEnabled = true))
        // Applied once only.
        assertNull(buffer.release(animating = false, inputEnabled = true))
    }

    @Test
    fun theLastMoveDuringOneAnimationWins() {
        val buffer = Game2048MoveBuffer()

        buffer.request(Game2048Direction.UP, animating = true, inputEnabled = true)
        buffer.request(Game2048Direction.RIGHT, animating = true, inputEnabled = true)

        assertEquals(Game2048Direction.RIGHT, buffer.release(animating = false, inputEnabled = true))
    }

    @Test
    fun disabledInputDropsTheKeptMove() {
        val buffer = Game2048MoveBuffer()

        buffer.request(Game2048Direction.DOWN, animating = true, inputEnabled = true)
        // The animated move ended the game (or lives ran out, or a dialog opened).
        assertNull(buffer.release(animating = true, inputEnabled = false))
        assertNull(buffer.release(animating = false, inputEnabled = true))
    }

    @Test
    fun disabledInputNeitherAppliesNorKeepsAMove() {
        val buffer = Game2048MoveBuffer()

        assertNull(buffer.request(Game2048Direction.LEFT, animating = false, inputEnabled = false))
        assertNull(buffer.request(Game2048Direction.LEFT, animating = true, inputEnabled = false))
        assertNull(buffer.pending)
    }
}
