package com.stanisryz.logica.ui.components

/**
 * Platform-neutral hardware keyboard input. A host translates its own key events into these and
 * forwards them; each gameplay presentation decides what a key means for its own transient state.
 */
sealed interface GameKey {
    data class Letter(
        val char: Char,
    ) : GameKey

    data class Digit(
        val value: Int,
    ) : GameKey

    data object Up : GameKey

    data object Down : GameKey

    data object Left : GameKey

    data object Right : GameKey

    data object Enter : GameKey

    data object Backspace : GameKey

    data object Delete : GameKey
}
