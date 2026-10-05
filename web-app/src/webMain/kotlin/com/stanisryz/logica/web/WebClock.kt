@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.daily.DailyDate
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The one Web "now" for the economy, rewards, and the Daily date: the device clock shifted by the
 * offset to the Yandex server's time measured once after SDK start, so moving the device clock
 * neither restores lives nor opens another day's rewards. Without a server time (standalone, an
 * SDK without `serverTime`, a failed call) the offset is zero. Calendar days use the browser's time
 * zone on the corrected time; ad cooldowns, which live only in the session, keep the device clock.
 */
internal class WebClock(
    private val deviceNowMs: () -> Long,
) {
    /** Server time minus device time, in milliseconds. */
    var offsetMs: Long = 0L
        private set

    /** Measures the offset from one server time reading; null keeps the device clock as it is. */
    fun synchronize(serverNowMs: Long?) {
        offsetMs = if (serverNowMs == null || serverNowMs <= 0L) 0L else serverNowMs - deviceNowMs()
    }

    fun now(): Long = deviceNowMs() + offsetMs

    /** Today's browser-local calendar date on the corrected time. */
    fun currentDate(): DailyDate {
        val encoded = browserLocalDateCodeAt(now().toDouble())
        return DailyDate(year = encoded / 10_000, month = encoded / 100 % 100, day = encoded % 100)
    }

    /** Milliseconds until the next browser-local midnight on the corrected time. */
    fun millisUntilNextLocalMidnight(): Long = browserMillisUntilNextLocalMidnightAt(now().toDouble()).toLong()
}

/** The page's clock; the bootstrap synchronizes it with the Yandex server once the SDK is ready. */
internal val webClock = WebClock(::currentTimeMillis)

private fun browserLocalDateCodeAt(nowMs: Double): Int =
    js("(() => { const date = new Date(nowMs); return date.getFullYear() * 10000 + (date.getMonth() + 1) * 100 + date.getDate(); })()")

private fun browserMillisUntilNextLocalMidnightAt(nowMs: Double): Double =
    js(
        "(() => { const now = new Date(nowMs); " +
            "return new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1).getTime() - now.getTime(); })()",
    )
