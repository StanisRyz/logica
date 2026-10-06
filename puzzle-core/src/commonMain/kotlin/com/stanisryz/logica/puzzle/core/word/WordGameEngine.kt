package com.stanisryz.logica.puzzle.core.word

class WordGameEngine(
    private val puzzle: WordPuzzle,
    private val allowedGuesses: WordAllowedGuesses,
) {
    private val language = puzzle.language

    fun start(): WordGameState = createState(currentDraft = WordDraft.empty(puzzle.wordLength, language), attempts = emptyList())

    /** Sets or replaces one position. Ignored only when the game is finished. */
    fun setLetter(
        state: WordGameState,
        position: Int,
        letter: Char,
    ): WordGameState {
        requireCompatible(state)
        require(position in 0 until puzzle.wordLength) { "Word draft position $position is out of bounds." }
        require(language.normalizer.isSupportedLetter(letter)) {
            "Letter '$letter' is not a supported ${language.name.lowercase()} letter."
        }
        if (state.isFinished || position in state.revealedLetters) return state
        return createState(
            currentDraft = state.currentDraft.withLetter(position, letter),
            attempts = state.attempts,
            revealedLetters = state.revealedLetters,
        )
    }

    /** Clears one position. Ignored when the game is finished, the position is empty, or a hint opened it. */
    fun clearLetter(
        state: WordGameState,
        position: Int,
    ): WordGameState {
        requireCompatible(state)
        require(position in 0 until puzzle.wordLength) { "Word draft position $position is out of bounds." }
        if (state.isFinished || position in state.revealedLetters) return state
        return createState(
            currentDraft = state.currentDraft.withoutLetter(position),
            attempts = state.attempts,
            revealedLetters = state.revealedLetters,
        )
    }

    /**
     * The position a hint would open: the leftmost one no submitted attempt has guessed exactly and no
     * hint has opened yet, or null when none is left, the limit is reached, or the game is over.
     */
    fun nextHintPosition(state: WordGameState): Int? {
        requireCompatible(state)
        return state.nextHintPosition
    }

    /**
     * Opens one answer letter in its place ([nextHintPosition]); it stays there for the rest of the
     * attempt. A hint never spends a guess. Without a position to open the state is returned as it is.
     */
    fun revealHint(state: WordGameState): WordGameState {
        val position = nextHintPosition(state) ?: return state
        val letter = puzzle.answer[position]
        return createState(
            currentDraft = state.currentDraft.withLetter(position, letter),
            attempts = state.attempts,
            revealedLetters = state.revealedLetters + (position to letter),
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
            when (val normalization = language.normalizer.normalize(completedDraft, puzzle.wordLength)) {
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
        if (state.attempts.any { it.word == guess }) {
            return WordSubmitResult.Rejected(state, WordGuessRejection.ALREADY_GUESSED)
        }

        val attempt = WordAttempt(guess, WordRules.evaluate(puzzle.answer, guess, language), language)
        return WordSubmitResult.Accepted(
            // Every new row starts with the letters hints opened.
            state =
                createState(
                    currentDraft = draftWith(state.revealedLetters),
                    attempts = state.attempts + attempt,
                    revealedLetters = state.revealedLetters,
                ),
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
                val guess = WordRules.requireNormalized(submitted, puzzle.wordLength, language)
                require(guess in allowedGuesses) { "Submitted word '$guess' is not an allowed guess." }
                WordAttempt(guess, WordRules.evaluate(puzzle.answer, guess, language), language)
            }
        require(attempts.none { it.isCorrect } || attempts.last().isCorrect) {
            "A solved game cannot contain attempts after the correct guess."
        }
        val restoredDraft =
            if (attempts.lastOrNull()?.isCorrect == true) WordDraft.empty(puzzle.wordLength, language) else currentDraft
        return createState(currentDraft = restoredDraft, attempts = attempts)
    }

    private fun draftWith(revealedLetters: Map<Int, Char>): WordDraft =
        WordDraft.fromPositions(List(puzzle.wordLength) { revealedLetters[it] }, language)

    private fun createState(
        currentDraft: WordDraft,
        attempts: List<WordAttempt>,
        revealedLetters: Map<Int, Char> = emptyMap(),
    ): WordGameState =
        WordGameState(
            puzzleId = puzzle.id,
            wordLength = puzzle.wordLength,
            currentDraft = currentDraft,
            attempts = attempts,
            status = statusOf(attempts),
            revealedLetters = revealedLetters,
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
