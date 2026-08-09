package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.registry.PuzzleGeneratorRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WordGeneratorV1Test {
    private val generator = WordGeneratorV1()

    @Test
    fun generationIsDeterministicForTheSameIdentityAndFrozenAnswerPool() {
        val frozenSeedOneAnswers =
            mapOf(
                Difficulty.EASY to "верба",
                Difficulty.MEDIUM to "гряда",
                Difficulty.HARD to "холод",
                Difficulty.EXPERT to "хомяк",
            )
        Difficulty.entries.forEach { difficulty ->
            val seed = PuzzleSeed(4242)
            val puzzle = generator.generate(seed, difficulty)

            assertEquals(puzzle, WordGeneratorV1().generate(seed, difficulty))
            assertEquals(PuzzleType.WORD, puzzle.id.type)
            assertEquals(GeneratorVersion(1), puzzle.id.generatorVersion)
            assertEquals(seed, puzzle.id.seed)
            assertEquals(difficulty, puzzle.id.difficulty)
            assertEquals(difficulty, WordLexiconV1.possibleAnswers.difficultyOf(puzzle.answer))
            assertEquals(frozenSeedOneAnswers.getValue(difficulty), generator.generate(PuzzleSeed(1), difficulty).answer)

            val v2Puzzle = WordGeneratorV2().generate(seed, difficulty)
            assertEquals(v2Puzzle, WordGeneratorV2().generate(seed, difficulty))
            assertEquals(GeneratorVersion(2), v2Puzzle.id.generatorVersion)
            assertEquals(WordRules.wordLengthForV2(difficulty), v2Puzzle.answer.length)
            assertEquals(difficulty, WordLexiconV2.possibleAnswers.difficultyOf(v2Puzzle.answer))
        }
        assertSame(
            generator,
            PuzzleGeneratorRegistry(listOf(generator)).find(PuzzleType.WORD, GeneratorVersion(1)),
        )
    }

    @Test
    fun everyDifficultyHasANonEmptyPoolOfNormalizedAnswersInsideTheAllowedGuesses() {
        val answers = WordLexiconV1.possibleAnswers.all()

        assertEquals(answers.size, answers.toSet().size)
        assertTrue("Answers must stay more curated than the guess pool.", answers.size < WordLexiconV1.allowedGuesses.size)
        answers.forEach { answer ->
            assertTrue("'$answer' must be normalized.", WordRules.isNormalized(answer))
            assertTrue("'$answer' must be an allowed guess.", answer in WordLexiconV1.allowedGuesses)
        }
        Difficulty.entries.forEach { difficulty ->
            val pool = WordLexiconV1.possibleAnswers.answers(difficulty)
            assertTrue("The ${difficulty.name} answer pool must not be empty.", pool.isNotEmpty())
            pool.forEach { assertEquals(difficulty, WordLexiconV1.possibleAnswers.difficultyOf(it)) }
        }
    }

    @Test
    fun normalizationRejectsUnsupportedInputAndFoldsYoIntoYe() {
        assertEquals("ковер", WordRules.normalizeOrNull("КовЁр"))
        assertEquals(
            WordNormalization.Rejected(WordNormalizationRejection.UNSUPPORTED_CHARACTER, ' '),
            WordRules.normalize(" ковер"),
        )
        assertEquals(
            WordNormalization.Rejected(WordNormalizationRejection.UNSUPPORTED_CHARACTER, 'a'),
            WordRules.normalize("aовер"),
        )
        assertEquals(
            WordNormalization.Rejected(WordNormalizationRejection.UNSUPPORTED_CHARACTER, '-'),
            WordRules.normalize("ко-вер"),
        )
        assertEquals(WordNormalization.Rejected(WordNormalizationRejection.WRONG_LENGTH), WordRules.normalize("коврик"))
        assertEquals(WordNormalization.Rejected(WordNormalizationRejection.EMPTY), WordRules.normalize(""))
    }
}
