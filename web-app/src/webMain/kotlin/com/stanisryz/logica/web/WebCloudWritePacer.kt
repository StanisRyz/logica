package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveWriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps Yandex `setData` within its limit (100 calls in 5 minutes per Player): at least
 * [minSpacingMs] between any two calls, whatever their key — the unified save, the legacy keys
 * before establishment, and every retry. A write waiting for its turn is merged with a newer write
 * of the same key, so the newer payload goes out once and both callers get its result; nothing is
 * lost, only fewer calls are made. A write whose Player context changed while it waited
 * ([contextEpoch] differs from the one it was queued under) is dropped as failed, so a paced write
 * can never land in another Player's cloud.
 */
internal class WebCloudWritePacer(
    private val minSpacingMs: Long = MIN_SPACING_MS,
    private val now: () -> Long = { webClock.now() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** The current Player context for cloud writes; a change drops every write queued before it. */
    var contextEpoch: () -> Long = { 0L }

    private class Pending(
        var write: suspend () -> CloudSaveWriteResult,
        val epoch: Long,
        val result: CompletableDeferred<CloudSaveWriteResult>,
    )

    private val order = ArrayDeque<String>()
    private val pending = mutableMapOf<String, Pending>()
    private val turn = Mutex()
    private var lastCallAt: Long? = null

    /** Calls [write] for [key] in its turn, or merges it into a write of [key] already waiting. */
    suspend fun write(
        key: String,
        write: suspend () -> CloudSaveWriteResult,
    ): CloudSaveWriteResult {
        val epoch = contextEpoch()
        val waiting = pending[key]
        val result =
            if (waiting != null && waiting.epoch == epoch) {
                waiting.write = write
                waiting.result
            } else {
                waiting?.result?.complete(STALE)
                order.remove(key)
                Pending(write, epoch, CompletableDeferred())
                    .also {
                        pending[key] = it
                        order.addLast(key)
                    }.result
            }
        turn.withLock {
            while (!result.isCompleted) {
                val head = order.first()
                lastCallAt?.let { last ->
                    val wait = last + minSpacingMs - now()
                    if (wait > 0) sleep(wait)
                }
                // Taken only now, after the wait, so a newer write that arrived meanwhile goes out instead.
                order.remove(head)
                val next = pending.remove(head) ?: continue
                if (next.epoch != contextEpoch()) {
                    next.result.complete(STALE)
                    continue
                }
                lastCallAt = now()
                val written =
                    try {
                        next.write()
                    } catch (error: CancellationException) {
                        next.result.complete(STALE)
                        throw error
                    } catch (error: Throwable) {
                        CloudSaveWriteResult.Failed(error)
                    }
                next.result.complete(written)
            }
        }
        return result.await()
    }

    companion object {
        /** 3.5 s apart is at most ~86 calls in 5 minutes, under Yandex's 100. */
        const val MIN_SPACING_MS = 3_500L

        private val STALE = CloudSaveWriteResult.Failed(IllegalStateException("The Player context changed before the cloud write."))
    }
}
