package com.stanisryz.logica.puzzle.core.word

/**
 * The single Russian normalization contract shared by lexicon preparation, lexicon lookup,
 * gameplay submission, and tests. Changing these rules changes the frozen lexicon.
 */
object RussianWordNormalizer {
    /** Supported normalized alphabet: 32 Cyrillic letters, because `ё` always folds into `е`. */
    const val ALPHABET = "абвгдежзийклмнопрстуфхцчшщъыьэюя"

    private const val YO_UPPER = 'Ё'
    private const val YO_LOWER = 'ё'
    private const val YE_LOWER = 'е'

    private val supportedLetters = ALPHABET.toSet()

    fun isSupportedLetter(character: Char): Boolean = normalizeLetter(character) in supportedLetters

    /** Lower-cases, folds `Ё` into `Е`, and leaves unsupported characters untouched for rejection. */
    fun normalizeLetter(character: Char): Char =
        when (character) {
            YO_UPPER, YO_LOWER -> YE_LOWER
            else -> character.lowercaseChar()
        }

    fun normalize(
        raw: String,
        expectedLength: Int,
    ): WordNormalization {
        require(expectedLength > 0) { "Expected length must be positive." }
        if (raw.isEmpty()) return WordNormalization.Rejected(WordNormalizationRejection.EMPTY)

        val normalized = StringBuilder(raw.length)
        raw.forEach { character ->
            val letter = normalizeLetter(character)
            if (letter !in supportedLetters) {
                return WordNormalization.Rejected(WordNormalizationRejection.UNSUPPORTED_CHARACTER, character)
            }
            normalized.append(letter)
        }
        if (normalized.length != expectedLength) {
            return WordNormalization.Rejected(WordNormalizationRejection.WRONG_LENGTH)
        }
        return WordNormalization.Normalized(normalized.toString())
    }

    fun normalizeOrNull(
        raw: String,
        expectedLength: Int,
    ): String? = (normalize(raw, expectedLength) as? WordNormalization.Normalized)?.word

    fun isNormalized(
        word: String,
        expectedLength: Int,
    ): Boolean = normalizeOrNull(word, expectedLength) == word
}

enum class WordNormalizationRejection {
    EMPTY,
    UNSUPPORTED_CHARACTER,
    WRONG_LENGTH,
}

sealed interface WordNormalization {
    data class Normalized(
        val word: String,
    ) : WordNormalization

    data class Rejected(
        val rejection: WordNormalizationRejection,
        val offendingCharacter: Char? = null,
    ) : WordNormalization
}
