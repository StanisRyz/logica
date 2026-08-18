package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class WordRuntimeResolverTest {
    @Test
    fun resolvesFrozenGeneratorAndLexiconPairings() {
        val v1 = WordRuntimeResolver.resolve(GeneratorVersion(1))
        val v2 = WordRuntimeResolver.resolve(GeneratorVersion(2))

        assertIs<WordGeneratorV1>(v1.generator)
        assertSame(WordLexiconV1.allowedGuesses, v1.allowedGuesses)
        assertEquals(
            listOf(WordLexiconV1.ALLOWED_GUESSES_RESOURCE, WordLexiconV1.ANSWERS_RESOURCE),
            v1.requiredResourcePaths,
        )
        assertIs<WordGeneratorV2>(v2.generator)
        assertSame(WordLexiconV2.allowedGuesses, v2.allowedGuesses)
        assertEquals(
            listOf(WordLexiconV2.ALLOWED_GUESSES_RESOURCE, WordLexiconV2.ANSWERS_RESOURCE),
            v2.requiredResourcePaths,
        )
    }
}
