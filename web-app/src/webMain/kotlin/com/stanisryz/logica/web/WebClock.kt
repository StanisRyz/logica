@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.daily.DailyDate
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The one Web "now" for the economy, rewards, and the Daily date. With the Yandex SDK every reading
 * asks `ysdk.serverTime()` ([serverNowMs]), which the SDK derives from the server time it received
 * at page load plus the browser's monotonic `performance.now()`, so moving the device clock while
 * the page is open changes nothing. A failed or non-numeric reading falls back to the device clock
 * shifted by the last successful offset (zero before any); standalone has no server source and is
 * the device clock. Calendar days use the browser's time zone on this time; ad cooldowns, which live
 * only in the session, keep the device clock.
 */
internal class WebClock(
    private val deviceNowMs: () -> Long,
    private var serverNowMs: () -> Long? = { null },
) {
    /** Server time minus device time at the last successful server reading, in milliseconds. */
    var offsetMs: Long = 0L
        private set

    /** Starts reading the server time from [source] (the SDK, once it is ready). */
    fun attachServerTime(source: () -> Long?) {
        serverNowMs = source
    }

    fun now(): Long {
        val server = runCatching { serverNowMs() }.getOrNull()?.takeIf { it > 0L }
        if (server != null) {
            offsetMs = server - deviceNowMs()
            return server
        }
        return deviceNowMs() + offsetMs
    }

    /** Today's browser-local calendar date on [now]. */
    fun currentDate(): DailyDate {
        val encoded = browserLocalDateCodeAt(now().toDouble())
        return DailyDate(year = encoded / 10_000, month = encoded / 100 % 100, day = encoded % 100)
    }

    /** Milliseconds until the next browser-local midnight on [now]. */
    fun millisUntilNextLocalMidnight(): Long = browserMillisUntilNextLocalMidnightAt(now().toDouble()).toLong()
}

/** The page's clock; the bootstrap attaches the Yandex server time once the SDK is ready. */
internal val webClock = WebClock(::currentTimeMillis)

private fun browserLocalDateCodeAt(nowMs: Double): Int =
    js("(() => { const date = new Date(nowMs); return date.getFullYear() * 10000 + (date.getMonth() + 1) * 100 + date.getDate(); })()")

private fun browserMillisUntilNextLocalMidnightAt(nowMs: Double): Double =
    js(
        "(() => { const now = new Date(nowMs); " +
            "return new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1).getTime() - now.getTime(); })()",
    )
