package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion

/** The complete synchronous runtime pairing for one frozen Word generator version. */
data class WordRuntime(
    val generator: PuzzleGenerator<WordPuzzle>,
    val allowedGuesses: WordAllowedGuesses,
    val requiredResourcePaths: List<String>,
    val language: WordLanguage = WordLanguage.RUSSIAN,
)

/**
 * One platform-neutral source of truth for Word generator, lexicon, and language compatibility: V1 and
 * V2 are Russian, V3 English, V4 Turkish. A result or level that stores its generator version therefore
 * also says its language.
 */
object WordRuntimeResolver {
    fun language(generatorVersion: GeneratorVersion): WordLanguage =
        when (generatorVersion.value) {
            1, 2 -> WordLanguage.RUSSIAN
            3 -> WordLanguage.ENGLISH
            4 -> WordLanguage.TURKISH
            else -> error("Unsupported Word generator version ${generatorVersion.value}.")
        }

    fun resolve(generatorVersion: GeneratorVersion): WordRuntime =
        when (generatorVersion.value) {
            1 ->
                WordRuntime(
                    generator = WordGeneratorV1(),
                    allowedGuesses = WordLexiconV1.allowedGuesses,
                    requiredResourcePaths =
                        listOf(
                            WordLexiconV1.ALLOWED_GUESSES_RESOURCE,
                            WordLexiconV1.ANSWERS_RESOURCE,
                        ),
                    language = WordLanguage.RUSSIAN,
                )
            2 ->
                WordRuntime(
                    generator = WordGeneratorV2(),
                    allowedGuesses = WordLexiconV2.allowedGuesses,
                    requiredResourcePaths =
                        listOf(
                            WordLexiconV2.ALLOWED_GUESSES_RESOURCE,
                            WordLexiconV2.ANSWERS_RESOURCE,
                        ),
                    language = WordLanguage.RUSSIAN,
                )
            3 ->
                WordRuntime(
                    generator = WordGeneratorByLength.v3(),
                    allowedGuesses = WordLexiconV3.allowedGuesses,
                    requiredResourcePaths = listOf(WordLexiconV3.ALLOWED_GUESSES_RESOURCE, WordLexiconV3.ANSWERS_RESOURCE),
                    language = WordLanguage.ENGLISH,
                )
            4 ->
                WordRuntime(
                    generator = WordGeneratorByLength.v4(),
                    allowedGuesses = WordLexiconV4.allowedGuesses,
                    requiredResourcePaths = listOf(WordLexiconV4.ALLOWED_GUESSES_RESOURCE, WordLexiconV4.ANSWERS_RESOURCE),
                    language = WordLanguage.TURKISH,
                )
            else -> error("Unsupported Word generator version ${generatorVersion.value}.")
        }
}
