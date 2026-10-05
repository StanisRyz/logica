package com.stanisryz.logica.web

import com.stanisryz.logica.platform.AppLog
import com.stanisryz.logica.web.generated.resources.web_failure_data_corrupt
import com.stanisryz.logica.web.generated.resources.web_failure_data_load
import com.stanisryz.logica.web.generated.resources.web_failure_progress
import com.stanisryz.logica.web.generated.resources.web_failure_unknown
import org.jetbrains.compose.resources.StringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

/**
 * Why a level or Daily entry could not open. Error states carry only this, never an exception's
 * text: the player sees the localized [message] in their language.
 */
internal enum class WebLoadFailure(
    val message: StringResource,
) {
    /** A puzzle file could not be fetched, usually the connection. */
    DATA_LOAD(WebRes.string.web_failure_data_load),

    /** A puzzle file arrived but its content does not fit the level. */
    DATA_CORRUPT(WebRes.string.web_failure_data_corrupt),

    /** The player's progress is not available for this context right now. */
    PROGRESS_UNAVAILABLE(WebRes.string.web_failure_progress),

    UNKNOWN(WebRes.string.web_failure_unknown),
}

/** A puzzle data file that could not be fetched. */
internal class WebPuzzleDataLoadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The reason a failed load shows: a fetch failure is [WebLoadFailure.DATA_LOAD], a broken `check`
 * or `require` on the loaded content is [WebLoadFailure.DATA_CORRUPT], and anything else is unknown.
 */
internal fun Throwable.toWebLoadFailure(): WebLoadFailure =
    when (this) {
        is WebPuzzleDataLoadException -> WebLoadFailure.DATA_LOAD
        is IllegalStateException, is IllegalArgumentException -> WebLoadFailure.DATA_CORRUPT
        else -> WebLoadFailure.UNKNOWN
    }

/** [toWebLoadFailure], with the cause written to [AppLog] for whoever reads the console. */
internal fun Throwable.toLoggedWebLoadFailure(what: String): WebLoadFailure =
    toWebLoadFailure().also { AppLog.warn("WebGame", "$what could not open ($it).", this) }
