package com.stanisryz.logica

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for code that suspends: every failure still becomes a [Result], but coroutine
 * cancellation is rethrown, so a cancelled scope really stops instead of carrying on as if a call had
 * merely failed.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
