@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * The portal currency icon a Yandex catalog price is shown with, loaded once per URL. Null while
 * loading or when the icon cannot be fetched or decoded; the price then names its currency in text.
 */
@Composable
internal fun rememberCurrencyIcon(url: String?): ImageBitmap? {
    val icon by produceState(url?.let(currencyIcons::get), url) {
        if (url == null || value != null) return@produceState
        value =
            runCatching { fetchImageBytes(url).decodeToImageBitmap() }
                .getOrNull()
                ?.also { currencyIcons[url] = it }
    }
    return icon
}

private val currencyIcons = mutableMapOf<String, ImageBitmap>()

private suspend fun fetchImageBytes(url: String): ByteArray {
    val buffer =
        suspendCoroutine<ArrayBuffer?> { continuation ->
            fetchArrayBuffer(url).then(
                onFulfilled = { value ->
                    continuation.resume(value)
                    null
                },
                onRejected = {
                    continuation.resume(null)
                    null
                },
            )
        } ?: error("The currency icon could not be fetched.")
    val bytes = Int8Array(buffer)
    return ByteArray(bytes.length) { index -> int8ArrayByteAt(bytes, index).toByte() }
}

private fun fetchArrayBuffer(url: String): Promise<ArrayBuffer?> =
    js("fetch(url).then(function (r) { return r.ok ? r.arrayBuffer() : null; })")

private fun int8ArrayByteAt(
    source: Int8Array,
    index: Int,
): Int = js("source[index]")
