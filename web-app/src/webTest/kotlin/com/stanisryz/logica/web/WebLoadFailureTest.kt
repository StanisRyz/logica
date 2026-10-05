package com.stanisryz.logica.web

import com.stanisryz.logica.platform.AppLog
import kotlin.test.Test
import kotlin.test.assertEquals

/** Error screens show a localized reason chosen from the failure, never the exception's text. */
class WebLoadFailureTest {
    @Test
    fun aFailedFetchCorruptContentAndAnythingElseMapToTheirReasons() {
        assertEquals(WebLoadFailure.DATA_LOAD, WebPuzzleDataLoadException("Unable to load levels/v1/x: HTTP 404.").toWebLoadFailure())
        assertEquals(WebLoadFailure.DATA_CORRUPT, IllegalStateException("Corrupt Catalog Level Pack record.").toWebLoadFailure())
        assertEquals(WebLoadFailure.DATA_CORRUPT, IllegalArgumentException("Balance level 3 requires generator 2.").toWebLoadFailure())
        assertEquals(WebLoadFailure.UNKNOWN, RuntimeException("anything else").toWebLoadFailure())
    }

    @Test
    fun theCauseGoesToTheLogWhileThePlayerSeesTheReason() {
        val logged = mutableListOf<String>()
        val previous = AppLog.sink
        AppLog.sink = AppLog.Sink { tag, message, throwable -> logged += "$tag|$message|${throwable?.message}" }
        try {
            val failure = WebPuzzleDataLoadException("Unable to load levels/v1/x: HTTP 503.").toLoggedWebLoadFailure("Balance level")
            assertEquals(WebLoadFailure.DATA_LOAD, failure)
            assertEquals(listOf("WebGame|Balance level could not open (DATA_LOAD).|Unable to load levels/v1/x: HTTP 503."), logged)
        } finally {
            AppLog.sink = previous
        }
    }
}
