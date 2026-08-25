package com.stanisryz.logica.web

/**
 * Languages the Web application can currently present. The application is Russian-only; new
 * entries join this set together with real translated content, never before.
 */
internal enum class WebAppLanguage {
    RUSSIAN,
}

/**
 * Resolves a platform-reported Yandex I18N language tag to an application language. The
 * supported set is {ru}: any unknown, non-Russian, or missing platform value resolves safely
 * to the default Russian presentation instead of failing startup.
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
        "ru" -> WebAppLanguage.RUSSIAN
        else -> WebAppLanguage.RUSSIAN // safe default while the app is single-language
    }
}
