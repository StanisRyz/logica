package com.stanisryz.logica.puzzle.core.word

/** Structured rejection reasons. Localized user-facing text belongs to `:app`. */
enum class WordGuessRejection {
    GAME_FINISHED,
    INCOMPLETE_INPUT,
    NORMALIZATION_FAILED,
    NOT_IN_ALLOWED_GUESSES,
}

sealed interface WordSubmitResult {
    val state: WordGameState

    /** The guess was valid, consumed one attempt, and is now final. */
    data class Accepted(
        override val state: WordGameState,
        val attempt: WordAttempt,
    ) : WordSubmitResult

    /** The guess consumed no attempt; [state] is the unchanged gameplay state. */
    data class Rejected(
        override val state: WordGameState,
        val rejection: WordGuessRejection,
        val normalizationRejection: WordNormalizationRejection? = null,
        val offendingCharacter: Char? = null,
    ) : WordSubmitResult
}
