package com.stanisryz.logica.web

import kotlin.test.Test
import kotlin.test.assertEquals

/** Yandex I18N language resolution: Russian for the CIS, Turkish, and English for the rest. */
class WebAppLanguageTest {
    @Test
    fun platformLanguagesResolveToTheSupportedLanguages() {
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage("ru"))
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage("RU"))
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage("ru-RU"))
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage("be"))
        assertEquals(WebAppLanguage.TURKISH, resolveWebAppLanguage("tr-TR"))
        assertEquals(WebAppLanguage.ENGLISH, resolveWebAppLanguage("en"))
        assertEquals(WebAppLanguage.ENGLISH, resolveWebAppLanguage("de"))
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage(""))
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage(null))
    }
}
