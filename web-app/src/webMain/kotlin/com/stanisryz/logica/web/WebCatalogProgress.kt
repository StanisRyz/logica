@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.js.ExperimentalWasmJsInterop

internal data class WebCatalogProgressBucket(
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val packVersion: CatalogLevelPackVersion,
) {
    init {
        require(puzzleType in CatalogLevelPacks.PUZZLE_TYPES) { "$puzzleType has no Catalog level pack." }
    }
}

/** Versioned, session-free Web progress. A missing bucket always means Catalog Level 1. */
internal data class WebCatalogProgressSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val levels: Map<WebCatalogProgressBucket, CatalogLevelNumber> = emptyMap(),
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported Web progress schema $schemaVersion." }
        require(levels.keys.all { it.puzzleType in CatalogLevelPacks.PUZZLE_TYPES })
    }

    fun currentLevel(bucket: WebCatalogProgressBucket): CatalogLevelNumber =
        levels[bucket] ?: CatalogLevelPacks.FIRST_LEVEL

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val EMPTY = WebCatalogProgressSnapshot()
    }
}

/** Deterministic compact binary format with stable, explicit puzzle/difficulty codes. */
internal object WebCatalogProgressCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'P'.code.toByte(), 'R'.code.toByte())
    private const val HEADER_SIZE = 12
    private const val ENTRY_SIZE = 10
    private const val MAX_ENTRIES = 10_000

    fun encode(snapshot: WebCatalogProgressSnapshot): ByteArray {
        val entries =
            snapshot.levels.entries.sortedWith(
                compareBy(
                    { puzzleCode(it.key.puzzleType) },
                    { difficultyCode(it.key.difficulty) },
                    { it.key.packVersion.value },
                ),
            )
        val result = ByteArray(HEADER_SIZE + entries.size * ENTRY_SIZE)
        magic.copyInto(result)
        writeInt(result, 4, snapshot.schemaVersion)
        writeInt(result, 8, entries.size)
        var offset = HEADER_SIZE
        entries.forEach { (bucket, level) ->
            result[offset] = puzzleCode(bucket.puzzleType).toByte()
            result[offset + 1] = difficultyCode(bucket.difficulty).toByte()
            writeInt(result, offset + 2, bucket.packVersion.value)
            writeInt(result, offset + 6, level.value)
            offset += ENTRY_SIZE
        }
        return result
    }

    fun decode(payload: ByteArray): WebCatalogProgressSnapshot? =
        runCatching {
            require(payload.size >= HEADER_SIZE)
            require(magic.indices.all { payload[it] == magic[it] })
            val schemaVersion = readInt(payload, 4)
            require(schemaVersion == WebCatalogProgressSnapshot.CURRENT_SCHEMA_VERSION)
            val entryCount = readInt(payload, 8)
            require(entryCount in 0..MAX_ENTRIES)
            require(payload.size == HEADER_SIZE + entryCount * ENTRY_SIZE)

            val levels = linkedMapOf<WebCatalogProgressBucket, CatalogLevelNumber>()
            var offset = HEADER_SIZE
            repeat(entryCount) {
                val puzzleType = puzzleType(payload[offset].toInt() and 0xff)
                val difficulty = difficulty(payload[offset + 1].toInt() and 0xff)
                val packVersion = CatalogLevelPackVersion(readInt(payload, offset + 2))
                val currentLevel = CatalogLevelNumber(readInt(payload, offset + 6))
                val bucket = WebCatalogProgressBucket(puzzleType, difficulty, packVersion)
                require(levels.put(bucket, currentLevel) == null) { "Duplicate Web progress bucket." }
                offset += ENTRY_SIZE
            }
            WebCatalogProgressSnapshot(schemaVersion, levels)
        }.getOrNull()

    private fun puzzleCode(puzzleType: PuzzleType): Int =
        when (puzzleType) {
            PuzzleType.BALANCE -> 1
            PuzzleType.CROWNS -> 2
            PuzzleType.WORD -> 3
            PuzzleType.SUDOKU -> 4
            PuzzleType.GAME_2048 -> 5
            else -> error("$puzzleType has no Web Catalog progress code.")
        }

    private fun puzzleType(code: Int): PuzzleType =
        when (code) {
            1 -> PuzzleType.BALANCE
            2 -> PuzzleType.CROWNS
            3 -> PuzzleType.WORD
            4 -> PuzzleType.SUDOKU
            5 -> PuzzleType.GAME_2048
            else -> error("Unknown Web Catalog puzzle code $code.")
        }

    private fun difficultyCode(difficulty: Difficulty): Int =
        when (difficulty) {
            Difficulty.EASY -> 1
            Difficulty.MEDIUM -> 2
            Difficulty.HARD -> 3
            Difficulty.EXPERT -> 4
        }

    private fun difficulty(code: Int): Difficulty =
        when (code) {
            1 -> Difficulty.EASY
            2 -> Difficulty.MEDIUM
            3 -> Difficulty.HARD
            4 -> Difficulty.EXPERT
            else -> error("Unknown Web Catalog difficulty code $code.")
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

internal interface WebCatalogProgressStore {
    fun load(): WebCatalogProgressSnapshot

    fun save(snapshot: WebCatalogProgressSnapshot)
}

/** A local-storage namespace, separate from the schema payload and safe for use in a browser key. */
internal data class WebCatalogProgressScope private constructor(
    val keySuffix: String,
) {
    companion object {
        val STANDALONE = WebCatalogProgressScope("standalone")

        fun yandexPlayer(uniqueId: String): WebCatalogProgressScope {
            require(uniqueId.isNotBlank()) { "A Yandex Player scope requires a stable unique ID." }
            val encoded =
                WebBase64
                    .encode(uniqueId.encodeToByteArray())
                    .trimEnd('=')
                    .replace('+', '-')
                    .replace('/', '_')
            return WebCatalogProgressScope("yandex-$encoded")
        }
    }
}

/** Browser-local durable source; corrupt or missing data safely resolves to an empty snapshot. */
internal class WebCatalogProgressLocalStore(
    scope: WebCatalogProgressScope,
) : WebCatalogProgressStore {
    internal val storageKey = "$LOCAL_STORAGE_KEY_PREFIX:${scope.keySuffix}"

    override fun load(): WebCatalogProgressSnapshot =
        runCatching {
            val encoded = localStorageGet(storageKey) ?: return@runCatching WebCatalogProgressSnapshot.EMPTY
            val payload = WebBase64.decode(encoded) ?: return@runCatching WebCatalogProgressSnapshot.EMPTY
            WebCatalogProgressCodec.decode(payload) ?: WebCatalogProgressSnapshot.EMPTY
        }.getOrElse { WebCatalogProgressSnapshot.EMPTY }

    override fun save(snapshot: WebCatalogProgressSnapshot) {
        localStorageSet(storageKey, WebBase64.encode(WebCatalogProgressCodec.encode(snapshot)))
    }

    private companion object {
        const val LOCAL_STORAGE_KEY_PREFIX = "logica_catalog_progress_v1"
    }
}

internal fun interface WebCatalogProgressRepositoryFactory {
    fun create(scope: WebCatalogProgressScope): WebCatalogProgressRepository
}

internal sealed interface WebCatalogAdvanceResult {
    data class Advanced(
        val currentLevel: CatalogLevelNumber,
    ) : WebCatalogAdvanceResult

    data object Idempotent : WebCatalogAdvanceResult

    data object Rejected : WebCatalogAdvanceResult

    data class PersistenceFailed(
        val cause: Throwable,
    ) : WebCatalogAdvanceResult
}

internal sealed interface WebCatalogMergeResult {
    data class Merged(
        val snapshot: WebCatalogProgressSnapshot,
        val cloudWriteRequired: Boolean,
    ) : WebCatalogMergeResult

    data class PersistenceFailed(
        val cause: Throwable,
    ) : WebCatalogMergeResult
}

/** Authoritative Web-local Catalog levels and deterministic monotonic cloud merge. */
internal class WebCatalogProgressRepository(
    val scope: WebCatalogProgressScope,
    private val localStore: WebCatalogProgressStore,
) {
    private val mutableSnapshot = MutableStateFlow(WebCatalogProgressSnapshot.EMPTY)
    val snapshot: StateFlow<WebCatalogProgressSnapshot> = mutableSnapshot.asStateFlow()

    fun loadLocal(): WebCatalogProgressSnapshot =
        localStore.load().also { mutableSnapshot.value = it }

    fun currentLevel(bucket: WebCatalogProgressBucket): CatalogLevelNumber =
        mutableSnapshot.value.currentLevel(bucket)

    fun advanceSolved(levelId: CatalogLevelId): WebCatalogAdvanceResult {
        val bucket = levelId.toProgressBucket()
        val stored = currentLevel(bucket)
        return when {
            stored.value > levelId.levelNumber.value -> WebCatalogAdvanceResult.Idempotent
            stored.value < levelId.levelNumber.value -> WebCatalogAdvanceResult.Rejected
            stored.value == Int.MAX_VALUE -> WebCatalogAdvanceResult.Rejected
            else -> {
                val next = CatalogLevelNumber(stored.value + 1)
                val updated =
                    mutableSnapshot.value.copy(
                        levels = mutableSnapshot.value.levels + (bucket to next),
                    )
                persist(updated)?.let { return WebCatalogAdvanceResult.PersistenceFailed(it) }
                mutableSnapshot.value = updated
                WebCatalogAdvanceResult.Advanced(next)
            }
        }
    }

    fun mergeCloud(cloud: WebCatalogProgressSnapshot): WebCatalogMergeResult {
        val local = mutableSnapshot.value
        val buckets = local.levels.keys + cloud.levels.keys
        val mergedLevels =
            buckets.associateWith { bucket ->
                val localLevel = local.currentLevel(bucket)
                val cloudLevel = cloud.currentLevel(bucket)
                if (localLevel.value >= cloudLevel.value) localLevel else cloudLevel
            }
        val merged = WebCatalogProgressSnapshot(levels = mergedLevels)
        if (merged != local) {
            persist(merged)?.let { return WebCatalogMergeResult.PersistenceFailed(it) }
            mutableSnapshot.value = merged
        }
        return WebCatalogMergeResult.Merged(
            snapshot = merged,
            cloudWriteRequired = merged != cloud,
        )
    }

    private fun persist(snapshot: WebCatalogProgressSnapshot): Throwable? =
        runCatching { localStore.save(snapshot) }.exceptionOrNull()
}

private fun CatalogLevelId.toProgressBucket(): WebCatalogProgressBucket =
    WebCatalogProgressBucket(
        puzzleType = puzzleType,
        difficulty = difficulty,
        packVersion = packVersion,
    )

private fun localStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun localStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}
