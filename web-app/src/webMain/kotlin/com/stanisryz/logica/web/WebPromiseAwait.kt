@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.asJsException

/**
 * Awaits a JavaScript promise. A rejection resumes with an [IllegalStateException] whose cause is
 * the JavaScript reason: `JsException` is only a `Throwable`, so rethrowing it bare would slip
 * past every `catch (Exception)` in the game controllers and leave their loading state forever.
 * The wait is cancellable, so a timeout around it really ends it; a promise settling after the
 * cancellation is ignored.
 */
internal suspend fun <T : JsAny?> Promise<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        then(
            onFulfilled = { value ->
                continuation.resume(value)
                null
            },
            onRejected = { reason ->
                continuation.resumeWithException(IllegalStateException("A JavaScript promise was rejected.", reason.asJsException()))
                null
            },
        )
    }
