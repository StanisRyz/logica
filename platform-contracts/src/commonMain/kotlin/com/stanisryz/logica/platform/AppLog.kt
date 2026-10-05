package com.stanisryz.logica.platform

/**
 * The one warning log both hosts write to: Android installs `Log.w`, Web `console.warn`, and tests
 * keep the default, which drops everything. It is only for failures the app otherwise swallows
 * without a trace. Never pass Player ids, purchase tokens, or save contents; nothing is sent anywhere.
 */
object AppLog {
    fun interface Sink {
        fun warn(
            tag: String,
            message: String,
            throwable: Throwable?,
        )
    }

    var sink: Sink = Sink { _, _, _ -> }

    fun warn(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) = sink.warn(tag, message, throwable)
}
