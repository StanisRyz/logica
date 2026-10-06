package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.Difficulty

/**
 * A bundled lexicon whose answers are tagged by difficulty and whose answer length follows
 * [WordRules.wordLengthForV2], read in [language]. Word V3 (English) and V4 (Turkish) use it; their
 * answer contents and ordering are generator compatibility data once a level pack depends on them.
 */
class WordLexiconByLength internal constructor(
    private val label: String,
    private val language: WordLanguage,
    allowedGuessesResource: String,
    answersResource: String,
) {
    val allowedGuesses: WordAllowedGuesses =
        object : WordAllowedGuesses {
            private val words: List<String> by lazy {
                readLexiconLines(allowedGuessesResource).map { line ->
                    require(WordRules.isSupportedLength(line.length)) { "Bundled $label guess '$line' has an unsupported length." }
                    language.normalizer.normalizeOrNull(line, line.length)
                        ?: error("Bundled $label allowed guess '$line' is not normalized ${language.name.lowercase()}.")
                }
            }
            private val lookup: Set<String> by lazy { words.toSet() }

            override val size: Int get() = words.size

            override fun contains(normalizedWord: String): Boolean = normalizedWord in lookup

            override fun all(): List<String> = words
        }

    val possibleAnswers: WordPossibleAnswers =
        object : WordPossibleAnswers {
            private val entries: List<Pair<String, Difficulty>> by lazy {
                readLexiconLines(answersResource).map(::parseEntry)
            }
            private val words: List<String> by lazy { entries.map { it.first } }
            private val byDifficulty: Map<Difficulty, List<String>> by lazy {
                Difficulty.entries.associateWith { difficulty ->
                    entries.filter { it.second == difficulty }.map { it.first }
                }
            }
            private val difficultyByWord: Map<String, Difficulty> by lazy { entries.toMap() }

            override val size: Int get() = words.size

            override fun answers(difficulty: Difficulty): List<String> = byDifficulty.getValue(difficulty)

            override fun difficultyOf(normalizedWord: String): Difficulty? = difficultyByWord[normalizedWord]

            override fun all(): List<String> = words
        }

    private fun parseEntry(line: String): Pair<String, Difficulty> {
        val parts = line.split(FIELD_SEPARATOR)
        require(parts.size == 2) { "Bundled $label answer line '$line' must be '<word>\t<difficulty>'." }
        val difficulty =
            Difficulty.entries.firstOrNull { it.name == parts[1].trim() }
                ?: error("Bundled $label answer '${parts[0]}' has unknown difficulty '${parts[1]}'.")
        val expectedLength = WordRules.wordLengthForV2(difficulty)
        val word =
            language.normalizer.normalizeOrNull(parts[0], expectedLength)
                ?: error("Bundled $label answer '${parts[0]}' does not match $difficulty length $expectedLength.")
        return word to difficulty
    }

    private fun readLexiconLines(resource: String): List<String> =
        BundledWordResources
            .readText(resource)
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith(COMMENT_PREFIX) }
            .toList()

    private companion object {
        const val FIELD_SEPARATOR = '\t'
        const val COMMENT_PREFIX = "#"
    }
}

/**
 * Word V3, English: generated offline by `tools/word-lexicon/extract_english.py` from ENABLE (guesses),
 * Open English WordNet 2023 (nouns), and wordfreq (ranking). Provenance: `datasets/word/en/`.
 */
object WordLexiconV3 {
    const val ALLOWED_GUESSES_RESOURCE = "/word/v3/allowed_guesses.txt"
    const val ANSWERS_RESOURCE = "/word/v3/answers.txt"

    private val lexicon = WordLexiconByLength("V3", WordLanguage.ENGLISH, ALLOWED_GUESSES_RESOURCE, ANSWERS_RESOURCE)
    val allowedGuesses: WordAllowedGuesses = lexicon.allowedGuesses
    val possibleAnswers: WordPossibleAnswers = lexicon.possibleAnswers
}

/**
 * Word V4, Turkish: generated offline by `tools/word-lexicon/extract_turkish.py` from the Zemberek
 * lexicon and morphology (guesses and nouns) and wordfreq (ranking). Provenance: `datasets/word/tr/`.
 */
object WordLexiconV4 {
    const val ALLOWED_GUESSES_RESOURCE = "/word/v4/allowed_guesses.txt"
    const val ANSWERS_RESOURCE = "/word/v4/answers.txt"

    private val lexicon = WordLexiconByLength("V4", WordLanguage.TURKISH, ALLOWED_GUESSES_RESOURCE, ANSWERS_RESOURCE)
    val allowedGuesses: WordAllowedGuesses = lexicon.allowedGuesses
    val possibleAnswers: WordPossibleAnswers = lexicon.possibleAnswers
}
