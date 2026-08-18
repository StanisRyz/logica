package com.stanisryz.logica.puzzle.core.word

import org.junit.Assert.assertEquals
import org.junit.Test

class WordFeedbackTest {
    @Test
    fun repeatedGuessLettersConsumeOnlyTheRemainingAnswerLetters() {
        // "полка" holds a single "о", so only the first repeated "о" of "около" may be PRESENT.
        assertEquals(
            listOf(
                WordLetterFeedback.PRESENT,
                WordLetterFeedback.PRESENT,
                WordLetterFeedback.ABSENT,
                WordLetterFeedback.PRESENT,
                WordLetterFeedback.ABSENT,
            ),
            WordRules.evaluate(answer = "полка", guess = "около").map(WordLetterResult::feedback),
        )
    }

    @Test
    fun exactMatchesAreMarkedBeforeRepeatedLettersElsewhere() {
        // The exact "о" at index 1 consumes the only answer "о", so the later "о" must be ABSENT.
        assertEquals(
            listOf(
                WordLetterFeedback.ABSENT,
                WordLetterFeedback.CORRECT,
                WordLetterFeedback.PRESENT,
                WordLetterFeedback.ABSENT,
                WordLetterFeedback.PRESENT,
            ),
            WordRules.evaluate(answer = "полка", guess = "сокол").map(WordLetterResult::feedback),
        )
    }

    @Test
    fun letterKnowledgeKeepsTheStrongestFeedbackPerNormalizedLetter() {
        val attempt = WordAttempt("сокол", WordRules.evaluate(answer = "полка", guess = "сокол"))
        val knowledge = WordLetterKnowledge.from(listOf(attempt))

        // "о" is CORRECT once and ABSENT once in the same attempt; the stronger state must win.
        assertEquals(WordLetterFeedback.CORRECT, knowledge['о'])
        assertEquals(WordLetterFeedback.PRESENT, knowledge['к'])
        assertEquals(WordLetterFeedback.ABSENT, knowledge['с'])
        assertEquals(WordLetterFeedback.CORRECT, knowledge['О'])
        assertEquals(null, knowledge['б'])
    }
}
