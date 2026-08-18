package com.stanisryz.logica.puzzle.core.model

/**
 * The one attempt rule Balance and Crowns share: the third committed mistake ends the attempt.
 * Only the limit is shared; each puzzle counts and stores its own mistakes.
 */
object PuzzleMistakes {
    const val MAX_MISTAKES = 3
}
