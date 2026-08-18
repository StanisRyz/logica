package com.stanisryz.logica.puzzle.core.catalog

/** In-memory input for a single already-loaded Catalog Level Pack bucket. */
class ByteArrayCatalogLevelPackInput(
    bytes: ByteArray,
) : CatalogLevelPackInput() {
    private val content = bytes.copyOf()
    private var position = 0
    private var closed = false

    override fun read(): Int {
        checkOpen()
        if (position >= content.size) return -1
        return content[position++].toInt() and 0xFF
    }

    override fun read(
        target: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        checkOpen()
        require(offset >= 0 && length >= 0 && offset <= target.size - length) {
            "Invalid target range: offset=$offset, length=$length, size=${target.size}."
        }
        if (length == 0) return 0
        if (position >= content.size) return -1
        val count = minOf(length, content.size - position)
        content.copyInto(target, offset, position, position + count)
        position += count
        return count
    }

    override fun skip(bytes: Long): Long {
        checkOpen()
        if (bytes <= 0L) return 0L
        val count = minOf(bytes, (content.size - position).toLong()).toInt()
        position += count
        return count.toLong()
    }

    override fun close() {
        closed = true
    }

    private fun checkOpen() {
        check(!closed) { "Catalog Level Pack input is closed." }
    }
}
