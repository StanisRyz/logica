package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.word.WordLanguage
import com.stanisryz.logica.ui.components.GameKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebKeyboardTest {
    @Test
    fun navigationDigitsAndEditingKeysMapIndependentlyOfTheLayout() {
        assertEquals(GameKey.Up, webGameKeyOf("ArrowUp", "ArrowUp"))
        assertEquals(GameKey.Left, webGameKeyOf("ArrowLeft", "ArrowLeft"))
        assertEquals(GameKey.Enter, webGameKeyOf("Enter", "NumpadEnter"))
        assertEquals(GameKey.Backspace, webGameKeyOf("Backspace", "Backspace"))
        assertEquals(GameKey.Digit(7), webGameKeyOf("7", "Numpad7"))
        assertNull(webGameKeyOf("Tab", "Tab"))
        assertNull(webGameKeyOf(" ", "Space"))
    }

    @Test
    fun russianLettersArriveFromEitherTheRussianLayoutOrTheKeyPosition() {
        assertEquals(GameKey.Letter('ж'), webGameKeyOf("Ж", "Semicolon"))
        // An English layout types the letter printed on the same physical key in ЙЦУКЕН.
        assertEquals(GameKey.Letter('а'), webGameKeyOf("f", "KeyF"))
        assertEquals(GameKey.Letter('ё'), webGameKeyOf("`", "Backquote"))
        assertEquals(GameKey.Letter('з'), webGameKeyOf("p", "KeyP"))
        assertNull(webGameKeyOf("F5", "F5"))
    }

    @Test
    fun englishAndTurkishLettersFollowTheirOwnLayouts() {
        // English: typed Latin letters, or the QWERTY key under any other layout.
        assertEquals(GameKey.Letter('q'), webGameKeyOf("Q", "KeyQ", WordLanguage.ENGLISH))
        assertEquals(GameKey.Letter('f'), webGameKeyOf("а", "KeyF", WordLanguage.ENGLISH))
        // Turkish: I is dotless ı and İ is i by its own table, never the locale's.
        assertEquals(GameKey.Letter('ı'), webGameKeyOf("I", "KeyI", WordLanguage.TURKISH))
        assertEquals(GameKey.Letter('i'), webGameKeyOf("İ", "Quote", WordLanguage.TURKISH))
        assertEquals(GameKey.Letter('ş'), webGameKeyOf("Ş", "Semicolon", WordLanguage.TURKISH))
        // A Latin layout on a Turkish game types by the Turkish-Q key positions.
        assertEquals(GameKey.Letter('ğ'), webGameKeyOf("[", "BracketLeft", WordLanguage.TURKISH))
        assertEquals(GameKey.Letter('ı'), webGameKeyOf("ш", "KeyI", WordLanguage.TURKISH))
    }
}
