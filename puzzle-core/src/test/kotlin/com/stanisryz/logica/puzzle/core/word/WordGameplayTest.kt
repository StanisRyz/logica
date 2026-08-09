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
    fun rejectedGuessesConsumeNoAttempt() {
        val incomplete = type("пол")
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
        assertEquals("пол", engine.removeLastLetter(type("полк")).currentInput)
    }

    @Test
    fun aCorrectGuessSolvesTheGameAndClearsTheInput() {
        val solved = submitAll(listOf("весна", "полка"))

        assertEquals(WordGameStatus.SOLVED, solved.status)
        assertTrue(solved.isFinished)
        assertEquals("", solved.currentInput)
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
        assertEquals(failed, engine.restore(currentInput = "", submittedWords = wrongGuesses))
    }

    private fun submitAll(words: List<String>): WordGameState =
        words.fold(engine.start()) { state, word ->
            val result = engine.submit(word.fold(state, engine::appendLetter))
            assertTrue("Guess '$word' must be accepted.", result is WordSubmitResult.Accepted)
            result.state
        }

    private fun type(input: String): WordGameState = input.fold(engine.start(), engine::appendLetter)

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
