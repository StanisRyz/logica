package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.Difficulty

/**
 * Word V1 lexicon, loaded from bundled project data with no network or platform access.
 *
 * FROZEN. The answer pool contents and ordering are part of Generator V1 compatibility: any change
 * that makes an existing `(difficulty, seed, generatorVersion = 1)` produce a different answer
 * requires `WordGeneratorV2` plus a new lexicon revision, never an edit to these V1 resources.
 * Adding allowed guesses alone does not change answers, but it still belongs to a reviewed
 * regeneration through `:puzzle-core:wordLexiconPrepare`.
 *
 * [allowedGuesses] and [possibleAnswers] stay separate implementations on purpose: the guess pool is
 * deliberately wider than the curated answer pool.
 */
object WordLexiconV1 {
    const val ALLOWED_GUESSES_RESOURCE = "/word/v1/allowed_guesses.txt"
    const val ANSWERS_RESOURCE = "/word/v1/answers.txt"

    val allowedGuesses: WordAllowedGuesses = BundledAllowedGuesses
    val possibleAnswers: WordPossibleAnswers = BundledPossibleAnswers

    private object BundledAllowedGuesses : WordAllowedGuesses {
        private val words: List<String> by lazy {
            readLexiconLines(ALLOWED_GUESSES_RESOURCE).map { line ->
                WordRules.normalizeOrNull(line)
                    ?: error("Bundled allowed guess '$line' is not a normalized ${WordRules.WORD_LENGTH}-letter word.")
            }
        }
        private val lookup: Set<String> by lazy { words.toSet() }

        override val size: Int get() = words.size

        override fun contains(normalizedWord: String): Boolean = normalizedWord in lookup

        override fun all(): List<String> = words
    }

    private object BundledPossibleAnswers : WordPossibleAnswers {
        private val words: List<String> by lazy { entries.map { it.first } }
        private val byDifficulty: Map<Difficulty, List<String>> by lazy {
            Difficulty.entries.associateWith { difficulty ->
                entries.filter { it.second == difficulty }.map { it.first }
            }
        }
        private val difficultyByWord: Map<String, Difficulty> by lazy { entries.toMap() }

        private val entries: List<Pair<String, Difficulty>> by lazy {
            readLexiconLines(ANSWERS_RESOURCE).map(::parseEntry)
        }

        override val size: Int get() = words.size

        override fun answers(difficulty: Difficulty): List<String> = byDifficulty.getValue(difficulty)

        override fun difficultyOf(normalizedWord: String): Difficulty? = difficultyByWord[normalizedWord]

        override fun all(): List<String> = words

        private fun parseEntry(line: String): Pair<String, Difficulty> {
            val parts = line.split(FIELD_SEPARATOR)
            require(parts.size == 2) { "Bundled answer line '$line' must be '<word>$FIELD_SEPARATOR<difficulty>'." }
            val word =
                WordRules.normalizeOrNull(parts[0])
                    ?: error("Bundled answer '${parts[0]}' is not a normalized ${WordRules.WORD_LENGTH}-letter word.")
            val difficulty =
                Difficulty.entries.firstOrNull { it.name == parts[1].trim() }
                    ?: error("Bundled answer '$word' has unknown difficulty '${parts[1]}'.")
            return word to difficulty
        }
    }

    private fun readLexiconLines(resource: String): List<String> {
        val stream =
            checkNotNull(WordLexiconV1::class.java.getResourceAsStream(resource)) {
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
