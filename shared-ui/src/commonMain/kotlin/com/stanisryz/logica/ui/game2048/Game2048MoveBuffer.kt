package com.stanisryz.logica.ui.game2048

import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction

/**
 * One-move buffer for 2048 input: a swipe or arrow key recognised while the previous move still
 * animates is kept (the last one wins) and applied as soon as the animation ends. Input disabled for
 * any other reason — no lives, a finished game, a dialog — drops the kept move instead of applying it.
 */
class Game2048MoveBuffer {
    var pending: Game2048Direction? = null
        private set

    /** A recognised direction: returns it to apply now, or keeps it until the animation ends. */
    fun request(
        direction: Game2048Direction,
        animating: Boolean,
        inputEnabled: Boolean,
    ): Game2048Direction? {
        if (!inputEnabled) {
            pending = null
            return null
        }
        if (animating) {
            pending = direction
            return null
        }
        pending = null
        return direction
    }

    /** Called whenever animation or input availability changes: returns the kept move once it may run. */
    fun release(
        animating: Boolean,
        inputEnabled: Boolean,
    ): Game2048Direction? {
        if (!inputEnabled) {
            pending = null
            return null
        }
        if (animating) return null
        return pending.also { pending = null }
    }
}
