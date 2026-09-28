@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import kotlin.js.ExperimentalWasmJsInterop

/** Languages the Web application presents, each with the resource language tag it selects. */
internal enum class WebAppLanguage(
    val tag: String,
) {
    RUSSIAN("ru"),
    ENGLISH("en"),
    TURKISH("tr"),
}

/**
 * Resolves a platform-reported language tag to an application language: Russian for Russian and
 * the neighbouring CIS languages Yandex Games serves in Russian, Turkish for Turkish, and English
 * for every other language. A missing value keeps the platform's own default, Russian.
 */
internal fun resolveWebAppLanguage(platformLanguage: String?): WebAppLanguage {
    val normalized =
        platformLanguage
            ?.trim()
            ?.lowercase()
            ?.substringBefore('-')
            ?.substringBefore('_')
            .orEmpty()
    return when (normalized) {
        "", "ru", "be", "kk", "uk", "uz" -> WebAppLanguage.RUSSIAN
        "tr" -> WebAppLanguage.TURKISH
        else -> WebAppLanguage.ENGLISH
    }
}

/**
 * Makes [language] the one the shared resources resolve: Compose resources on the Web read the
 * browser language, so the page reports the application language instead. Applied before the
 * ready UI composes; a browser that refuses the override keeps its own language.
 */
internal fun applyWebAppLanguage(language: WebAppLanguage) {
    currentWebAppLanguage = language
    runCatching { overrideBrowserLanguage(language.tag) }
}

/** The language host-formatted text (dates) follows; set once at startup with [applyWebAppLanguage]. */
internal var currentWebAppLanguage: WebAppLanguage = WebAppLanguage.RUSSIAN
    private set

/** Standalone development follows `?lang=` or else the browser language, with no SDK involved. */
internal fun standaloneWebAppLanguage(): WebAppLanguage = resolveWebAppLanguage(runCatching { standaloneLanguageHint() }.getOrNull())

private fun overrideBrowserLanguage(tag: String) {
    js(
        """
        (function () {
          Object.defineProperty(navigator, 'language', { get: function () { return tag; }, configurable: true });
          Object.defineProperty(navigator, 'languages', { get: function () { return [tag]; }, configurable: true });
          document.documentElement.lang = tag;
        })()
        """,
    )
}

private fun standaloneLanguageHint(): String? =
    js("new URLSearchParams(globalThis.location.search).get('lang') || globalThis.navigator.language || null")
