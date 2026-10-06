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

    /** Owner decisions of stage 10.1a: the family filter, American spelling, and the circumflex nouns. */
    @Test
    fun answersKeepTheFamilyFilterAndTheSpellingDecisions() {
        val english = Difficulty.entries.flatMap { WordLexiconV3.possibleAnswers.answers(it) }.toSet()
        val turkish = Difficulty.entries.flatMap { WordLexiconV4.possibleAnswers.answers(it) }.toSet()
        // The words the architect still found in the stage 10.1 answers.
        val englishControls = listOf("death", "curse", "coup", "scam", "prison", "pistol", "bullet", "casino", "beer", "tobacco")
        val turkishControls = listOf("fatiha", "mücahit", "bira", "içki", "şarap", "sigara", "kumar")
        assertEquals(emptyList(), englishControls.filter { it in english })
        assertEquals(emptyList(), turkishControls.filter { it in turkish })
        // British spellings stay guesses but never answers; the American forms can be answers.
        val british =
            listOf("colour", "honour", "centre", "defence", "theatre", "licence", "armour", "flavour", "labour", "favour", "humour")
        assertEquals(emptyList(), british.filter { it in english })
        assertTrue(british.all { it in WordLexiconV3.allowedGuesses })
        assertTrue(listOf("color", "honor", "center", "theater", "license", "flavor", "labor", "favor", "humor").all { it in english })
        // Frequent circumflex nouns are answers again, without the circumflex.
        assertTrue(listOf("hikaye", "rüzgar", "şikayet", "dükkan", "kağıt").all { it in turkish })
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
            "joke cane liner bread legion bumper hunting breakup kent bant kiraz boğaz kuzgun boykot kesinti başvuru"
    }
}
