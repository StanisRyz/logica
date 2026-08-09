package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.Difficulty

/**
 * Bundled Word V2 content generated offline from the pinned pymorphy3 Russian dictionary plus
 * project-owned allow/block lists. Runtime code never depends on Python or morphology libraries.
 *
 * Once released, the answer contents and ordering become Generator V2 compatibility data. Future
 * output-changing curation must introduce a new generator/lexicon version.
 */
object WordLexiconV2 {
    const val ALLOWED_GUESSES_RESOURCE = "/word/v2/allowed_guesses.txt"
    const val ANSWERS_RESOURCE = "/word/v2/answers.txt"

    val allowedGuesses: WordAllowedGuesses = BundledAllowedGuesses
    val possibleAnswers: WordPossibleAnswers = BundledPossibleAnswers

    private object BundledAllowedGuesses : WordAllowedGuesses {
        private val words: List<String> by lazy {
            readLexiconLines(ALLOWED_GUESSES_RESOURCE).map { line ->
                require(WordRules.isSupportedLength(line.length)) { "Bundled V2 guess '$line' has an unsupported length." }
                WordRules.normalizeOrNull(line, line.length)
                    ?: error("Bundled V2 allowed guess '$line' is not normalized Russian Cyrillic.")
            }
        }
        private val lookup: Set<String> by lazy { words.toSet() }

        override val size: Int get() = words.size

        override fun contains(normalizedWord: String): Boolean = normalizedWord in lookup

        override fun all(): List<String> = words
    }

    private object BundledPossibleAnswers : WordPossibleAnswers {
        private val entries: List<Pair<String, Difficulty>> by lazy {
            readLexiconLines(ANSWERS_RESOURCE).map(::parseEntry)
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

        private fun parseEntry(line: String): Pair<String, Difficulty> {
            val parts = line.split(FIELD_SEPARATOR)
            require(parts.size == 2) { "Bundled V2 answer line '$line' must be '<word>\t<difficulty>'." }
            val difficulty =
                Difficulty.entries.firstOrNull { it.name == parts[1].trim() }
                    ?: error("Bundled V2 answer '${parts[0]}' has unknown difficulty '${parts[1]}'.")
            val expectedLength = WordRules.wordLengthForV2(difficulty)
            val word =
                WordRules.normalizeOrNull(parts[0], expectedLength)
                    ?: error("Bundled V2 answer '${parts[0]}' does not match $difficulty length $expectedLength.")
            return word to difficulty
        }
    }

    private fun readLexiconLines(resource: String): List<String> {
        val stream =
            checkNotNull(WordLexiconV2::class.java.getResourceAsStream(resource)) {
                "Bundled Word lexicon resource $resource is missing."
            }
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader
                .readLines()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith(COMMENT_PREFIX) }
        }
    }

    private const val FIELD_SEPARATOR = '\t'
    private const val COMMENT_PREFIX = "#"
}
