@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import androidx.compose.runtime.compositionLocalOf
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleStars
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.profile.LevelStarRecord
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The best stars of every Web Catalog level, per progress bucket: index `level - 1` holds 0 (no
 * stars yet) to 3. It sits beside Catalog progress in the same Player scope, has its own local key
 * and unified-save section, and merges by per-level maximum, so no device can lower a level.
 */
internal data class WebCatalogStarsSnapshot(
    val levels: Map<WebCatalogProgressBucket, List<Int>> = emptyMap(),
) {
    init {
        require(levels.values.all { stars -> stars.size <= MAX_LEVELS && stars.all { it in 0..PuzzleStars.MAX_STARS } })
    }

    fun starsOf(
        bucket: WebCatalogProgressBucket,
        level: Int,
    ): Int = levels[bucket]?.getOrNull(level - 1) ?: 0

    /** A copy with [stars] recorded for [level] when they beat what the level already has. */
    fun withBest(
        bucket: WebCatalogProgressBucket,
        level: Int,
        stars: Int,
    ): WebCatalogStarsSnapshot {
        if (level !in 1..MAX_LEVELS || stars !in 1..PuzzleStars.MAX_STARS || starsOf(bucket, level) >= stars) return this
        val current = levels[bucket].orEmpty()
        val updated = List(maxOf(current.size, level)) { index -> if (index == level - 1) stars else current.getOrElse(index) { 0 } }
        return copy(levels = levels + (bucket to updated))
    }

    /** Per-level maximum of both snapshots. */
    fun mergedWith(other: WebCatalogStarsSnapshot): WebCatalogStarsSnapshot =
        WebCatalogStarsSnapshot(
            (levels.keys + other.levels.keys).associateWith { bucket ->
                val mine = levels[bucket].orEmpty()
                val theirs = other.levels[bucket].orEmpty()
                List(maxOf(mine.size, theirs.size)) { index -> maxOf(mine.getOrElse(index) { 0 }, theirs.getOrElse(index) { 0 }) }
            },
        )

    fun levelRecords(): List<LevelStarRecord> =
        levels.flatMap { (bucket, stars) ->
            stars.mapIndexedNotNull { index, value ->
                value.takeIf { it > 0 }?.let { LevelStarRecord(bucket.puzzleType, bucket.difficulty, it, index + 1) }
            }
        }

    companion object {
        /** Far beyond any real play; keeps a corrupt payload from allocating without bound. */
        const val MAX_LEVELS = 100_000
        val EMPTY = WebCatalogStarsSnapshot()
    }
}

/** Compact binary format: four levels per byte (two bits each), one block per bucket. */
internal object WebCatalogStarsCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte())
    private const val SCHEMA_VERSION = 1
    private const val HEADER_SIZE = 12
    private const val BUCKET_HEADER_SIZE = 10
    private const val MAX_BUCKETS = 1_000

    fun encode(snapshot: WebCatalogStarsSnapshot): ByteArray {
        val buckets =
            snapshot.levels.entries
                .filter { it.value.isNotEmpty() }
                .sortedWith(
                    compareBy({ puzzleCode(it.key.puzzleType) }, { difficultyCode(it.key.difficulty) }, { it.key.packVersion.value }),
                )
        val size = HEADER_SIZE + buckets.sumOf { BUCKET_HEADER_SIZE + packedSize(it.value.size) }
        val result = ByteArray(size)
        magic.copyInto(result)
        writeInt(result, 4, SCHEMA_VERSION)
        writeInt(result, 8, buckets.size)
        var offset = HEADER_SIZE
        buckets.forEach { (bucket, stars) ->
            result[offset] = puzzleCode(bucket.puzzleType).toByte()
            result[offset + 1] = difficultyCode(bucket.difficulty).toByte()
            writeInt(result, offset + 2, bucket.packVersion.value)
            writeInt(result, offset + 6, stars.size)
            offset += BUCKET_HEADER_SIZE
            stars.forEachIndexed { index, value ->
                val byteIndex = offset + index / 4
                result[byteIndex] = (result[byteIndex].toInt() or (value shl ((index % 4) * 2))).toByte()
            }
            offset += packedSize(stars.size)
        }
        return result
    }

    fun decode(payload: ByteArray): WebCatalogStarsSnapshot? =
        runCatching {
            require(payload.size >= HEADER_SIZE && magic.indices.all { payload[it] == magic[it] })
            require(readInt(payload, 4) == SCHEMA_VERSION)
            val bucketCount = readInt(payload, 8)
            require(bucketCount in 0..MAX_BUCKETS)
            val levels = linkedMapOf<WebCatalogProgressBucket, List<Int>>()
            var offset = HEADER_SIZE
            repeat(bucketCount) {
                require(offset + BUCKET_HEADER_SIZE <= payload.size)
                val bucket =
                    WebCatalogProgressBucket(
                        puzzleType(payload[offset].toInt() and 0xff),
                        difficulty(payload[offset + 1].toInt() and 0xff),
                        CatalogLevelPackVersion(readInt(payload, offset + 2)),
                    )
                val count = readInt(payload, offset + 6)
                require(count in 1..WebCatalogStarsSnapshot.MAX_LEVELS)
                offset += BUCKET_HEADER_SIZE
                require(offset + packedSize(count) <= payload.size)
                val stars = List(count) { index -> ((payload[offset + index / 4].toInt() and 0xff) shr ((index % 4) * 2)) and 0b11 }
                require(levels.put(bucket, stars) == null) { "Duplicate Web stars bucket." }
                offset += packedSize(count)
            }
            require(offset == payload.size)
            WebCatalogStarsSnapshot(levels)
        }.getOrNull()

    private fun packedSize(count: Int): Int = (count + 3) / 4

    private fun puzzleCode(puzzleType: PuzzleType): Int =
        when (puzzleType) {
            PuzzleType.BALANCE -> 1
            PuzzleType.CROWNS -> 2
            PuzzleType.WORD -> 3
            PuzzleType.SUDOKU -> 4
            PuzzleType.GAME_2048 -> 5
            PuzzleType.NONOGRAM -> 6
            else -> error("$puzzleType has no Web stars code.")
        }

    private fun puzzleType(code: Int): PuzzleType =
        when (code) {
            1 -> PuzzleType.BALANCE
            2 -> PuzzleType.CROWNS
            3 -> PuzzleType.WORD
            4 -> PuzzleType.SUDOKU
            5 -> PuzzleType.GAME_2048
            6 -> PuzzleType.NONOGRAM
            else -> error("Unknown Web stars puzzle code $code.")
        }

    private fun difficultyCode(difficulty: Difficulty): Int = difficulty.ordinalCode()

    private fun difficulty(code: Int): Difficulty =
        Difficulty.entries.firstOrNull { it.ordinalCode() == code } ?: error("Unknown Web stars difficulty code $code.")

    /** Explicit stable codes, never enum ordinals. */
    private fun Difficulty.ordinalCode(): Int =
        when (this) {
            Difficulty.EASY -> 1
            Difficulty.MEDIUM -> 2
            Difficulty.HARD -> 3
            Difficulty.EXPERT -> 4
        }

    private fun writeInt(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        destination[offset] = (value ushr 24).toByte()
        destination[offset + 1] = (value ushr 16).toByte()
        destination[offset + 2] = (value ushr 8).toByte()
        destination[offset + 3] = value.toByte()
    }

    private fun readInt(
        source: ByteArray,
        offset: Int,
    ): Int =
        ((source[offset].toInt() and 0xff) shl 24) or
            ((source[offset + 1].toInt() and 0xff) shl 16) or
            ((source[offset + 2].toInt() and 0xff) shl 8) or
            (source[offset + 3].toInt() and 0xff)
}

/** Star totals per difficulty of one game, for its difficulty cards. */
internal fun WebCatalogStarsSnapshot.starsByDifficulty(puzzleType: PuzzleType): Map<Difficulty, Long> =
    levels
        .filterKeys { it.puzzleType == puzzleType }
        .entries
        .groupBy({ it.key.difficulty }, { it.value.sum().toLong() })
        .mapValues { (_, sums) -> sums.sum() }

/** The bound Player's stars; empty until a Player context is bound. */
internal val LocalWebCatalogStars = compositionLocalOf { WebCatalogStarsSnapshot.EMPTY }

internal interface WebCatalogStarsStore {
    fun load(): WebCatalogStarsSnapshot

    fun save(snapshot: WebCatalogStarsSnapshot)

    /** Keeps stars in memory only; the default for tests and hosts without browser storage. */
    class InMemory(
        private var snapshot: WebCatalogStarsSnapshot = WebCatalogStarsSnapshot.EMPTY,
    ) : WebCatalogStarsStore {
        override fun load(): WebCatalogStarsSnapshot = snapshot

        override fun save(snapshot: WebCatalogStarsSnapshot) {
            this.snapshot = snapshot
        }
    }
}

/** Browser-local stars beside Catalog progress; corrupt or missing data reads as no stars. */
internal class WebCatalogStarsLocalStore(
    scope: WebCatalogProgressScope,
) : WebCatalogStarsStore {
    private val storageKey = "logica_catalog_stars_v1:${scope.keySuffix}"

    override fun load(): WebCatalogStarsSnapshot =
        runCatching {
            val encoded = starsStorageGet(storageKey) ?: return@runCatching WebCatalogStarsSnapshot.EMPTY
            WebBase64.decode(encoded)?.let(WebCatalogStarsCodec::decode) ?: WebCatalogStarsSnapshot.EMPTY
        }.getOrElse { WebCatalogStarsSnapshot.EMPTY }

    override fun save(snapshot: WebCatalogStarsSnapshot) {
        starsStorageSet(storageKey, WebBase64.encode(WebCatalogStarsCodec.encode(snapshot)))
    }
}

private fun starsStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun starsStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}
