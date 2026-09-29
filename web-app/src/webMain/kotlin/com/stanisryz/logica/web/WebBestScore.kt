@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import kotlin.js.ExperimentalWasmJsInterop

/**
 * The best 2048 score of the bound Player — 2048's rating. One growing number beside Catalog
 * progress in the same Player scope, with its own browser key and unified-save section; local and
 * cloud copies merge by maximum, so no device can lower it.
 */
internal object WebBestScoreCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'B'.code.toByte(), 'S'.code.toByte())
    private const val SCHEMA_VERSION = 1
    private const val SIZE = 16

    fun encode(score: Long): ByteArray {
        val result = ByteArray(SIZE)
        magic.copyInto(result)
        writeInt(result, 4, SCHEMA_VERSION)
        for (index in 0 until Long.SIZE_BYTES) {
            result[8 + index] = (score ushr (8 * (Long.SIZE_BYTES - 1 - index))).toByte()
        }
        return result
    }

    fun decode(payload: ByteArray): Long? =
        runCatching {
            require(payload.size == SIZE && magic.indices.all { payload[it] == magic[it] })
            require(readInt(payload, 4) == SCHEMA_VERSION)
            var score = 0L
            for (index in 0 until Long.SIZE_BYTES) score = (score shl 8) or (payload[8 + index].toLong() and 0xff)
            require(score >= 0L)
            score
        }.getOrNull()

    private fun writeInt(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        for (index in 0 until Int.SIZE_BYTES) destination[offset + index] = (value ushr (8 * (Int.SIZE_BYTES - 1 - index))).toByte()
    }

    private fun readInt(
        source: ByteArray,
        offset: Int,
    ): Int {
        var value = 0
        for (index in 0 until Int.SIZE_BYTES) value = (value shl 8) or (source[offset + index].toInt() and 0xff)
        return value
    }
}

internal interface WebBestScoreStore {
    fun load(): Long

    fun save(score: Long)

    /** Keeps the score in memory only; the default for tests and hosts without browser storage. */
    class InMemory(
        private var score: Long = 0L,
    ) : WebBestScoreStore {
        override fun load(): Long = score

        override fun save(score: Long) {
            this.score = score
        }
    }
}

/** Browser-local best score beside Catalog progress; corrupt or missing data reads as none. */
internal class WebBestScoreLocalStore(
    scope: WebCatalogProgressScope,
) : WebBestScoreStore {
    private val storageKey = "logica_best_2048_v1:${scope.keySuffix}"

    override fun load(): Long =
        runCatching {
            bestScoreStorageGet(storageKey)?.let(WebBase64::decode)?.let(WebBestScoreCodec::decode) ?: 0L
        }.getOrDefault(0L)

    override fun save(score: Long) {
        bestScoreStorageSet(storageKey, WebBase64.encode(WebBestScoreCodec.encode(score)))
    }
}

private fun bestScoreStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun bestScoreStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}
