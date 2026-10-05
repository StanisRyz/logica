package com.stanisryz.logica.web

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
}
