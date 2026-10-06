package com.stanisryz.logica.puzzle.core.word

/**
 * One language's normalization contract, shared by lexicon preparation, lexicon lookup, gameplay
 * submission, and tests: lower case, only that language's letters, and an explicit length check.
 * Changing a language's rules changes its lexicon.
 */
interface WordNormalizer {
    /** The normalized alphabet: every letter a normalized word may contain. */
    val alphabet: String

    fun isSupportedLetter(character: Char): Boolean

    /** Lower-cases and folds letter variants, leaving unsupported characters untouched for rejection. */
    fun normalizeLetter(character: Char): Char

    fun normalize(
        raw: String,
        expectedLength: Int,
    ): WordNormalization

    fun normalizeOrNull(
        raw: String,
        expectedLength: Int,
    ): String? = (normalize(raw, expectedLength) as? WordNormalization.Normalized)?.word

    fun isNormalized(
        word: String,
        expectedLength: Int,
    ): Boolean = normalizeOrNull(word, expectedLength) == word
}

/** The language a Word generator version plays in; its normalizer is the language's whole alphabet rule. */
enum class WordLanguage(
    val normalizer: WordNormalizer,
) {
    RUSSIAN(RussianWordNormalizer),
    ENGLISH(EnglishWordNormalizer),
    TURKISH(TurkishWordNormalizer),
}

/** A normalizer defined by an explicit letter table, independent of the platform locale. */
abstract class TableWordNormalizer(
    final override val alphabet: String,
) : WordNormalizer {
    private val supportedLetters = alphabet.toSet()

    final override fun isSupportedLetter(character: Char): Boolean = normalizeLetter(character) in supportedLetters

    final override fun normalize(
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
}

/** English: the 26 letters a–z; upper case folds by the ASCII table, never by the platform locale. */
object EnglishWordNormalizer : TableWordNormalizer("abcdefghijklmnopqrstuvwxyz") {
    override fun normalizeLetter(character: Char): Char = if (character in 'A'..'Z') character + ('a' - 'A') else character
}

/**
 * Turkish: the 29-letter alphabet without q, w, and x. `I` lowers to dotless `ı` and `İ` to `i`, so the
 * table never asks the platform locale. The circumflex vowels â, î, û fold into a, i, u, the way most
 * Turkish keyboards and modern spelling write them.
 */
object TurkishWordNormalizer : TableWordNormalizer("abcçdefgğhıijklmnoöprsştuüvyz") {
    override fun normalizeLetter(character: Char): Char =
        when (character) {
            'I' -> 'ı'
            'İ' -> 'i'
            'Ç' -> 'ç'
            'Ğ' -> 'ğ'
            'Ö' -> 'ö'
            'Ş' -> 'ş'
            'Ü' -> 'ü'
            'Â', 'â' -> 'a'
            'Î', 'î' -> 'i'
            'Û', 'û' -> 'u'
            in 'A'..'Z' -> character + ('a' - 'A')
            else -> character
        }
}
