@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** A rejected browser promise (a failed level fetch) must reach the controllers' `catch (Exception)`. */
class WebPromiseAwaitTest {
    @Test
    fun rejectionResumesWithAnExceptionCarryingTheJavaScriptReason() =
        runTest {
            val caught =
                try {
                    rejectedPromise().await()
                    null
                } catch (exception: Exception) {
                    exception
                }
            assertIs<IllegalStateException>(caught)
            val cause = assertNotNull(caught.cause)
            assertFalse(cause is Exception, "JsException itself is only a Throwable")
        }

    @Test
    fun fulfilmentResumesWithTheValue() =
        runTest {
            assertEquals("ok", resolvedPromise().await().toString())
        }
}

private fun rejectedPromise(): Promise<JsAny?> = js("Promise.reject(new Error('offline'))")

private fun resolvedPromise(): Promise<JsString> = js("Promise.resolve('ok')")
