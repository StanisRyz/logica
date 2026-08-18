package com.stanisryz.logica.puzzle.core.catalog

/** Browser implementation of the small synchronous input contract used by the common parser. */
actual abstract class CatalogLevelPackInput protected actual constructor() {
    actual abstract fun read(): Int

    actual open fun read(
        target: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        require(offset >= 0 && length >= 0 && offset <= target.size - length) {
            "Invalid target range: offset=$offset, length=$length, size=${target.size}."
        }
        if (length == 0) return 0
        val first = read()
        if (first < 0) return -1
        target[offset] = first.toByte()
        var count = 1
        while (count < length) {
            val next = read()
            if (next < 0) break
            target[offset + count] = next.toByte()
            count++
        }
        return count
    }

    actual open fun skip(bytes: Long): Long {
        if (bytes <= 0L) return 0L
        var skipped = 0L
        while (skipped < bytes && read() >= 0) skipped++
        return skipped
    }

    actual open fun close() = Unit
}
