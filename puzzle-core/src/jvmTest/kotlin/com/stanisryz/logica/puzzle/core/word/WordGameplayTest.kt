package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordGameplayTest {
    private val engine = WordGameEngine(puzzle(), WordLexiconV1.allowedGuesses)

    @Test
    fun positionalDraftSupportsInsertReplaceClearAndNonConsumingIncompleteSubmit() {
        var incomplete = engine.start()
        incomplete = engine.setLetter(incomplete, 0, 'п')
        incomplete = engine.setLetter(incomplete, 2, 'л')
        incomplete = engine.setLetter(incomplete, 2, 'м')
        incomplete = engine.clearLetter(incomplete, 2)
        val incompleteResult = engine.submit(incomplete)

        val unknownWord = type("ббббб")
        val unknownResult = engine.submit(unknownWord)

        assertEquals(
            WordSubmitResult.Rejected(incomplete, WordGuessRejection.INCOMPLETE_INPUT),
            incompleteResult,
        )
        assertEquals(
            WordSubmitResult.Rejected(unknownWord, WordGuessRejection.NOT_IN_ALLOWED_GUESSES),
            unknownResult,
        )
        assertTrue(incompleteResult.state.attempts.isEmpty())
        assertTrue(unknownResult.state.attempts.isEmpty())
        assertEquals(WordRules.MAXIMUM_ATTEMPTS, unknownResult.state.remainingAttempts)
        assertEquals(WordGameStatus.IN_PROGRESS, unknownResult.state.status)
        assertEquals(listOf('п', null, null, null, null), incomplete.currentDraft.positions)
        assertEquals("ббббб", unknownWord.currentDraft.completedWordOrNull())
    }

    @Test
    fun aCorrectGuessSolvesTheGameAndClearsTheInput() {
        val solved = submitAll(listOf("весна", "полка"))

        assertEquals(WordGameStatus.SOLVED, solved.status)
        assertTrue(solved.isFinished)
        assertEquals(WordDraft.empty(5), solved.currentDraft)
        assertEquals(2, solved.attempts.size)
        assertTrue(solved.attempts.last().isCorrect)
        assertEquals(WordLetterFeedback.CORRECT, solved.letterKnowledge['к'])
        assertEquals(
            WordSubmitResult.Rejected(solved, WordGuessRejection.GAME_FINISHED),
            engine.submit(solved),
        )
    }

    @Test
    fun theSixthValidIncorrectGuessFailsTheGame() {
        val wrongGuesses = listOf("весна", "сосна", "книга", "лампа", "ветка", "банка")
        val failed = submitAll(wrongGuesses)

        assertEquals(WordGameStatus.FAILED, failed.status)
        assertEquals(WordRules.MAXIMUM_ATTEMPTS, failed.attempts.size)
        assertEquals(0, failed.remainingAttempts)
        assertTrue(failed.attempts.none { it.isCorrect })
        assertEquals(failed, engine.restore(currentDraft = WordDraft.empty(5), submittedWords = wrongGuesses))
    }

    @Test
    fun aRepeatedGuessIsRejectedWithoutSpendingAnAttempt() {
        val first = submitAll(listOf("весна"))
        val repeated = type(first, "весна")

        assertEquals(
            WordSubmitResult.Rejected(repeated, WordGuessRejection.ALREADY_GUESSED),
            engine.submit(repeated),
        )
        assertEquals(1, repeated.attempts.size)
        assertEquals(WordRules.MAXIMUM_ATTEMPTS - 1, repeated.remainingAttempts)
        // Ё is normalized first, so a spelling variant of the same word is the same guess.
        assertEquals(WordGuessRejection.ALREADY_GUESSED, (engine.submit(type(first, "вёсна")) as WordSubmitResult.Rejected).rejection)
        // Restore stays as it was: already recorded attempts are rebuilt even if a word repeats.
        assertEquals(2, engine.restore(WordDraft.empty(5), listOf("весна", "весна")).attempts.size)
    }

    private fun submitAll(words: List<String>): WordGameState =
        words.fold(engine.start()) { state, word ->
            val result = engine.submit(type(state, word))
            assertTrue("Guess '$word' must be accepted.", result is WordSubmitResult.Accepted)
            result.state
        }

    private fun type(input: String): WordGameState = type(engine.start(), input)

    private fun type(
        state: WordGameState,
        input: String,
    ): WordGameState = input.foldIndexed(state) { index, current, letter -> engine.setLetter(current, index, letter) }

    private fun puzzle(): WordPuzzle =
        WordPuzzle(
            id =
                PuzzleId(
                    type = PuzzleType.WORD,
                    difficulty = Difficulty.MEDIUM,
                    seed = PuzzleSeed(77),
                    generatorVersion = GeneratorVersion(1),
                ),
            answer = "полка",
        )
}
