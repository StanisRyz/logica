package com.stanisryz.logica.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * One load per resource path: callers asking for a path that is already loading wait on the same
 * load instead of fetching it again, a loaded path is never fetched twice, and a failed load is
 * forgotten so the next request (a Retry) really fetches again. Loads run in [scope], so a caller
 * that gives up (a cancelled attempt) never cancels the load another caller still waits for.
 */
internal class SharedResourceLoads(
    private val scope: CoroutineScope,
) {
    private val loaded = mutableSetOf<String>()
    private val inFlight = mutableMapOf<String, Deferred<Unit>>()

    suspend fun load(
        path: String,
        block: suspend () -> Unit,
    ) {
        if (path in loaded) return
        val load =
            inFlight[path] ?: scope
                .async(start = CoroutineStart.LAZY) {
                    try {
                        block()
                        loaded += path
                    } finally {
                        inFlight.remove(path)
                    }
                }.also {
                    inFlight[path] = it
                    it.start()
                }
        load.await()
    }
}
