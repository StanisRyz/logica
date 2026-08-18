package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion

/** The complete synchronous runtime pairing for one frozen Word generator version. */
data class WordRuntime(
    val generator: PuzzleGenerator<WordPuzzle>,
    val allowedGuesses: WordAllowedGuesses,
    val requiredResourcePaths: List<String>,
)

/** One platform-neutral source of truth for Word generator and lexicon compatibility. */
object WordRuntimeResolver {
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
                )
            else -> error("Unsupported Word generator version ${generatorVersion.value}.")
        }
}
