package com.stanisryz.logica.puzzle.core.catalog

/** Narrow streaming input required by the frozen Catalog pack reader. */
expect abstract class CatalogLevelPackInput protected constructor() {
    abstract fun read(): Int

    open fun read(
        target: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    open fun skip(bytes: Long): Long

    open fun close()
}
