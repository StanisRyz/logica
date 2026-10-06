package com.stanisryz.logica.ui

import com.stanisryz.logica.puzzle.core.word.WordLanguage
import com.stanisryz.logica.ui.word.wordKeyboardRows
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every language's keyboard holds each letter of its alphabet exactly once, in its familiar layout. */
class WordKeyboardLayoutTest {
    @Test
    fun everyAlphabetLetterIsOnTheKeyboardExactlyOnce() {
        WordLanguage.entries.forEach { language ->
            val keys = wordKeyboardRows(language).flatten()
            assertEquals("$language", keys.size, keys.toSet().size)
            assertEquals("$language", language.normalizer.alphabet.toSet(), keys.toSet())
        }
    }

    @Test
    fun theRowsKeepTheirLayouts() {
        assertEquals(listOf(12, 11, 9), wordKeyboardRows(WordLanguage.RUSSIAN).map { it.size })
        assertEquals(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), wordKeyboardRows(WordLanguage.ENGLISH).map { it.joinToString("") })
        assertEquals(listOf("ertyuıopğü", "asdfghjklşi", "zcvbnmöç"), wordKeyboardRows(WordLanguage.TURKISH).map { it.joinToString("") })
    }

    @Test
    fun turkishCapitalsComeFromItsOwnTable() {
        assertEquals("İ", WordLanguage.TURKISH.displayUppercase("i"))
        assertEquals("I", WordLanguage.TURKISH.displayUppercase("ı"))
        assertEquals("SERGİ", WordLanguage.TURKISH.displayUppercase("sergi"))
        assertEquals("SCOPE", WordLanguage.ENGLISH.displayUppercase("scope"))
        assertEquals("ПОЛКА", WordLanguage.RUSSIAN.displayUppercase("полка"))
    }
}
