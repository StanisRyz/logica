package com.stanisryz.logica.puzzle.core.word

class WordGameEngine(
    private val puzzle: WordPuzzle,
    private val allowedGuesses: WordAllowedGuesses,
) {
    fun start(): WordGameState = createState(currentInput = "", attempts = emptyList())

    /** Ignored when the game is finished or the current input is already complete. */
    fun appendLetter(
        state: WordGameState,
        letter: Char,
    ): WordGameState {
        requireCompatible(state)
        require(RussianWordNormalizer.isSupportedLetter(letter)) { "Letter '$letter' is not a supported Russian letter." }
        if (state.isFinished || state.currentInput.length == WordRules.WORD_LENGTH) return state
        return createState(
            currentInput = state.currentInput + RussianWordNormalizer.normalizeLetter(letter),
            attempts = state.attempts,
        )
    }

    /** Ignored when the game is finished or the current input is empty. */
    fun removeLastLetter(state: WordGameState): WordGameState {
        requireCompatible(state)
        if (state.isFinished || state.currentInput.isEmpty()) return state
        return createState(currentInput = state.currentInput.dropLast(1), attempts = state.attempts)
    }

    fun submit(state: WordGameState): WordSubmitResult {
        requireCompatible(state)
        if (state.isFinished) return WordSubmitResult.Rejected(state, WordGuessRejection.GAME_FINISHED)
        if (state.currentInput.length != WordRules.WORD_LENGTH) {
            return WordSubmitResult.Rejected(state, WordGuessRejection.INCOMPLETE_INPUT)
        }

        val guess =
            when (val normalization = WordRules.normalize(state.currentInput)) {
                is WordNormalization.Normalized -> normalization.word
                is WordNormalization.Rejected ->
                    return WordSubmitResult.Rejected(
                        state = state,
                        rejection = WordGuessRejection.NORMALIZATION_FAILED,
                        normalizationRejection = normalization.rejection,
                        offendingCharacter = normalization.offendingCharacter,
                    )
            }
        if (guess !in allowedGuesses) {
            return WordSubmitResult.Rejected(state, WordGuessRejection.NOT_IN_ALLOWED_GUESSES)
        }

        val attempt = WordAttempt(guess, WordRules.evaluate(puzzle.answer, guess))
        return WordSubmitResult.Accepted(
            state = createState(currentInput = "", attempts = state.attempts + attempt),
            attempt = attempt,
        )
    }

    /** Rebuilds gameplay from identity plus the submitted words; feedback is always recomputed. */
    fun restore(
        currentInput: String,
        submittedWords: List<String>,
    ): WordGameState {
        require(submittedWords.size <= WordRules.MAXIMUM_ATTEMPTS) { "Too many submitted attempts." }
        val attempts =
            submittedWords.map { submitted ->
                val guess = WordRules.requireNormalized(submitted)
                require(guess in allowedGuesses) { "Submitted word '$guess' is not an allowed guess." }
                WordAttempt(guess, WordRules.evaluate(puzzle.answer, guess))
            }
        require(attempts.none { it.isCorrect } || attempts.last().isCorrect) {
            "A solved game cannot contain attempts after the correct guess."
        }
        val restoredInput = if (attempts.lastOrNull()?.isCorrect == true) "" else currentInput
        return createState(currentInput = restoredInput, attempts = attempts)
    }

    private fun createState(
        currentInput: String,
        attempts: List<WordAttempt>,
    ): WordGameState =
        WordGameState(
            puzzleId = puzzle.id,
            currentInput = currentInput,
            attempts = attempts,
            status = statusOf(attempts),
        )

    private fun statusOf(attempts: List<WordAttempt>): WordGameStatus =
        when {
            attempts.lastOrNull()?.isCorrect == true -> WordGameStatus.SOLVED
            attempts.size >= WordRules.MAXIMUM_ATTEMPTS -> WordGameStatus.FAILED
            else -> WordGameStatus.IN_PROGRESS
        }

    private fun requireCompatible(state: WordGameState) {
        require(state.puzzleId == puzzle.id) { "Game state belongs to a different puzzle." }
    }
}
