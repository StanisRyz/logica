package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordLanguageNormalizerTest {
    @Test
    fun englishLowersByTheAsciiTableAndKeepsOnlyTwentySixLetters() {
        assertEquals("house", EnglishWordNormalizer.normalizeOrNull("HoUsE", 5))
        assertEquals(26, EnglishWordNormalizer.alphabet.length)
        assertRejected(EnglishWordNormalizer, "café", 'é')
        assertRejected(EnglishWordNormalizer, "дом", 'д')
        assertRejected(EnglishWordNormalizer, "naïve", 'ï')
        assertRejected(EnglishWordNormalizer, "co-op", '-')
        assertEquals(
            WordNormalization.Rejected(WordNormalizationRejection.WRONG_LENGTH),
            EnglishWordNormalizer.normalize("house", 4),
        )
        assertEquals(WordNormalization.Rejected(WordNormalizationRejection.EMPTY), EnglishWordNormalizer.normalize("", 4))
    }

    @Test
    fun turkishLowersDottedAndDotlessIByItsOwnTable() {
        assertEquals("ılık", TurkishWordNormalizer.normalizeOrNull("ILIK", 4))
        assertEquals("izin", TurkishWordNormalizer.normalizeOrNull("İZİN", 4))
        assertEquals("ıi", TurkishWordNormalizer.normalizeOrNull("Iİ", 2))
        assertEquals("ıi", TurkishWordNormalizer.normalizeOrNull("ıi", 2))
        assertEquals("çiğdem", TurkishWordNormalizer.normalizeOrNull("ÇİĞDEM", 6))
        assertEquals("şöyle", TurkishWordNormalizer.normalizeOrNull("ŞÖYLE", 5))
        assertEquals("üzüm", TurkishWordNormalizer.normalizeOrNull("ÜZÜM", 4))
        assertEquals(29, TurkishWordNormalizer.alphabet.length)
    }

    @Test
    fun turkishFoldsCircumflexVowelsAndRejectsLettersOutsideItsAlphabet() {
        assertEquals("kağıt", TurkishWordNormalizer.normalizeOrNull("kâğıt", 5))
        assertEquals("hala", TurkishWordNormalizer.normalizeOrNull("hâlâ", 4))
        assertEquals("milli", TurkishWordNormalizer.normalizeOrNull("millî", 5))
        assertEquals("umit", TurkishWordNormalizer.normalizeOrNull("ÛMİT", 4))
        assertRejected(TurkishWordNormalizer, "quiz", 'q')
        assertRejected(TurkishWordNormalizer, "wifi", 'w')
        assertRejected(TurkishWordNormalizer, "taxi", 'x')
        assertRejected(TurkishWordNormalizer, "дом", 'д')
    }

    @Test
    fun eachLanguageVersionResolvesItsLanguage() {
        listOf(1 to WordLanguage.RUSSIAN, 2 to WordLanguage.RUSSIAN, 3 to WordLanguage.ENGLISH, 4 to WordLanguage.TURKISH)
            .forEach { (version, language) ->
                assertEquals(language, WordRuntimeResolver.language(GeneratorVersion(version)))
            }
        assertNull(RussianWordNormalizer.normalizeOrNull("house", 5))
        assertEquals(RussianWordNormalizer.ALPHABET, WordLanguage.RUSSIAN.normalizer.alphabet)
    }

    private fun assertRejected(
        normalizer: WordNormalizer,
        raw: String,
        offending: Char,
    ) {
        assertEquals(
            WordNormalization.Rejected(WordNormalizationRejection.UNSUPPORTED_CHARACTER, offending),
            normalizer.normalize(raw, raw.length),
        )
    }
}
