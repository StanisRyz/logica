package com.stanisryz.logica.web

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
}
