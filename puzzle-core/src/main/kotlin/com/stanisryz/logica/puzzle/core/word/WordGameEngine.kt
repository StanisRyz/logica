package com.stanisryz.logica.puzzle.core.word

class WordGameEngine(
    private val puzzle: WordPuzzle,
    private val allowedGuesses: WordAllowedGuesses,
) {
    fun start(): WordGameState = createState(currentDraft = WordDraft.empty(puzzle.wordLength), attempts = emptyList())

    /** Sets or replaces one position. Ignored only when the game is finished. */
    fun setLetter(
        state: WordGameState,
        position: Int,
        letter: Char,
    ): WordGameState {
        requireCompatible(state)
        require(position in 0 until puzzle.wordLength) { "Word draft position $position is out of bounds." }
        require(RussianWordNormalizer.isSupportedLetter(letter)) { "Letter '$letter' is not a supported Russian letter." }
        if (state.isFinished) return state
        return createState(
            currentDraft = state.currentDraft.withLetter(position, letter),
            attempts = state.attempts,
        )
    }

    /** Clears one position. Ignored only when the game is finished or that position is empty. */
    fun clearLetter(
        state: WordGameState,
        position: Int,
    ): WordGameState {
        requireCompatible(state)
        require(position in 0 until puzzle.wordLength) { "Word draft position $position is out of bounds." }
        if (state.isFinished) return state
        return createState(
            currentDraft = state.currentDraft.withoutLetter(position),
            attempts = state.attempts,
        )
    }

    fun submit(state: WordGameState): WordSubmitResult {
        requireCompatible(state)
        if (state.isFinished) return WordSubmitResult.Rejected(state, WordGuessRejection.GAME_FINISHED)
        val completedDraft = state.currentDraft.completedWordOrNull()
        if (completedDraft == null) {
            return WordSubmitResult.Rejected(state, WordGuessRejection.INCOMPLETE_INPUT)
        }

        val guess =
            when (val normalization = WordRules.normalize(completedDraft, puzzle.wordLength)) {
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
            state = createState(currentDraft = WordDraft.empty(puzzle.wordLength), attempts = state.attempts + attempt),
            attempt = attempt,
        )
    }

    /** Rebuilds gameplay from identity plus the submitted words; feedback is always recomputed. */
    fun restore(
        currentDraft: WordDraft,
        submittedWords: List<String>,
    ): WordGameState {
        require(currentDraft.wordLength == puzzle.wordLength) { "Saved Word draft has the wrong length." }
        require(submittedWords.size <= WordRules.MAXIMUM_ATTEMPTS) { "Too many submitted attempts." }
        val attempts =
            submittedWords.map { submitted ->
                val guess = WordRules.requireNormalized(submitted, puzzle.wordLength)
                require(guess in allowedGuesses) { "Submitted word '$guess' is not an allowed guess." }
                WordAttempt(guess, WordRules.evaluate(puzzle.answer, guess))
            }
        require(attempts.none { it.isCorrect } || attempts.last().isCorrect) {
            "A solved game cannot contain attempts after the correct guess."
        }
        val restoredDraft =
            if (attempts.lastOrNull()?.isCorrect == true) WordDraft.empty(puzzle.wordLength) else currentDraft
        return createState(currentDraft = restoredDraft, attempts = attempts)
    }

    private fun createState(
        currentDraft: WordDraft,
        attempts: List<WordAttempt>,
    ): WordGameState =
        WordGameState(
            puzzleId = puzzle.id,
            wordLength = puzzle.wordLength,
            currentDraft = currentDraft,
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
        require(state.wordLength == puzzle.wordLength) { "Game state word length belongs to a different puzzle." }
    }
}
