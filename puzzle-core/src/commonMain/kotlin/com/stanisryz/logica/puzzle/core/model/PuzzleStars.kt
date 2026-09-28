package com.stanisryz.logica.puzzle.core.model

/**
 * Stars a solved attempt earns, the one rule both hosts store and show: Balance, Crowns, and
 * Sudoku by mistakes (none 3, one 2, more 1), Word by guesses (1-2 3, 3-4 2, 5-6 1). 2048 has
 * neither mistakes nor guesses and earns none. A failed attempt earns none.
 */
object PuzzleStars {
    const val MAX_STARS = 3

    fun forMistakes(mistakesUsed: Int): Int = (MAX_STARS - mistakesUsed).coerceIn(1, MAX_STARS)

    fun forWordAttempts(attemptsUsed: Int): Int =
        when {
            attemptsUsed <= 2 -> 3
            attemptsUsed <= 4 -> 2
            else -> 1
        }
}
