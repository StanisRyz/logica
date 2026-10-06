package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WordLanguageLexiconTest {
    @Test
    fun englishAndTurkishLexiconsHaveFiveHundredAnswersPerDifficultyAllAllowedAsGuesses() {
        listOf(WordLexiconV3.possibleAnswers to WordLexiconV3.allowedGuesses, WordLexiconV4.possibleAnswers to WordLexiconV4.allowedGuesses)
            .forEach { (answers, guesses) ->
                Difficulty.entries.forEach { difficulty ->
                    val pool = answers.answers(difficulty)
                    assertEquals(500, pool.size)
                    assertTrue(pool.all { it.length == WordRules.wordLengthForV2(difficulty) && it in guesses })
                    assertEquals(pool.sorted(), pool)
                }
            }
    }

    @Test
    fun theRuntimeResolvesTheLanguageVersions() {
        val english = WordRuntimeResolver.resolve(GeneratorVersion(3))
        val turkish = WordRuntimeResolver.resolve(GeneratorVersion(4))
        assertIs<WordGeneratorByLength>(english.generator)
        assertEquals(WordLanguage.ENGLISH, english.language)
        assertEquals(listOf(WordLexiconV3.ALLOWED_GUESSES_RESOURCE, WordLexiconV3.ANSWERS_RESOURCE), english.requiredResourcePaths)
        assertEquals(WordLanguage.TURKISH, turkish.language)
        assertEquals(listOf(WordLexiconV4.ALLOWED_GUESSES_RESOURCE, WordLexiconV4.ANSWERS_RESOURCE), turkish.requiredResourcePaths)
    }

    /** Recorded from the generated lexicons; a change means the V3/V4 answer pools changed. */
    @Test
    fun goldenAnswersForTheFirstSeeds() {
        val answers =
            listOf(3, 4).flatMap { version ->
                val generator = WordRuntimeResolver.resolve(GeneratorVersion(version)).generator
                Difficulty.entries.flatMap { difficulty -> listOf(1L, 2L).map { generator.generate(PuzzleSeed(it), difficulty).answer } }
            }
        assertEquals(GOLDEN_ANSWERS, answers.joinToString(" "))
    }

    @Test
    fun aTurkishGameTakesTurkishCapitalsAndScoresRepeatedLetters() {
        val runtime = WordRuntimeResolver.resolve(GeneratorVersion(4))
        val puzzle = runtime.generator.generate(PuzzleSeed(1L), Difficulty.EASY)
        val engine = WordGameEngine(puzzle, runtime.allowedGuesses)
        var state = engine.start()
        // Typed in capitals: I and İ must become ı and i, never the other way round.
        "İZİN".forEachIndexed { index, letter -> state = engine.setLetter(state, index, letter) }
        assertEquals("izin", state.currentDraft.completedWordOrNull())
        val submitted = engine.submit(state)
        assertIs<WordSubmitResult.Accepted>(submitted)
        assertEquals(WordRules.evaluate(puzzle.answer, "izin", WordLanguage.TURKISH), submitted.attempt.letters)
        assertTrue(runCatching { engine.setLetter(submitted.state, 0, 'q') }.isFailure)
    }

    @Test
    fun anEnglishGameRejectsAWordOutsideItsGuessesWithoutSpendingAnAttempt() {
        val runtime = WordRuntimeResolver.resolve(GeneratorVersion(3))
        val puzzle = runtime.generator.generate(PuzzleSeed(2L), Difficulty.MEDIUM)
        val engine = WordGameEngine(puzzle, runtime.allowedGuesses)
        var state = engine.start()
        "QZXJK".forEachIndexed { index, letter -> state = engine.setLetter(state, index, letter) }
        val rejected = engine.submit(state)
        assertIs<WordSubmitResult.Rejected>(rejected)
        assertEquals(WordGuessRejection.NOT_IN_ALLOWED_GUESSES, rejected.rejection)
        assertEquals(0, rejected.state.attempts.size)
    }

    private companion object {
        const val GOLDEN_ANSWERS =
            "joke card lobby brass layout buffer husband browser kişi bent korku boyun kuaför bolluk kokteyl bezelye"
    }
}
