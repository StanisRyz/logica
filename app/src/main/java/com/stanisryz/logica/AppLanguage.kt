package com.stanisryz.logica

import android.content.Context
import java.util.Locale

/**
 * The language the Android resources actually resolved to (`app_language` in `values`, `values-en`,
 * `values-tr`), so host-formatted dates and share text match the interface even when the device
 * language has no translation and the Russian default is shown.
 */
internal object AppLanguage {
    var tag: String = "ru"
        private set

    val locale: Locale get() = Locale.forLanguageTag(tag)

    fun update(context: Context) {
        tag = context.getString(R.string.app_language)
    }
}
