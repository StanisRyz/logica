package com.stanisryz.logica.web

import kotlin.test.Test
import kotlin.test.assertEquals

/** Stage 45.16: Yandex I18N language resolution — supported set {ru}, safe Russian default. */
class WebAppLanguageTest {
    @Test
    fun everyPlatformLanguageResolvesToTheSingleSupportedDefault() {
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage("ru"))
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage("RU"))
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage("ru-RU"))
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage("be"))
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage(""))
        assertEquals(WebAppLanguage.RUSSIAN, resolveWebAppLanguage(null))
    }
}
