package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules

internal data class WebStatisticsBucket(
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
) {
    init {
        require(puzzleType in WEB_STATISTICS_PUZZLE_TYPES) { "$puzzleType has no Web statistics bucket." }
    }
}

internal data class WebStatisticsCounters(
    val played: Long = 0L,
    val solved: Long = 0L,
    val failed: Long = 0L,
    val hints: Long = 0L,
    val wordSolvedAttempts: Map<Int, Long> = emptyMap(),
) {
    init {
        require(played >= 0L && solved >= 0L && failed >= 0L && hints >= 0L) {
            "Web statistics counters must be non-negative."
        }
        require(
            wordSolvedAttempts.all { (attempt, count) ->
                attempt in 1..WordRules.MAXIMUM_ATTEMPTS && count > 0L
            },
        ) { "Word solved-attempt counters must use valid attempts and positive stored values." }
    }

    internal fun mergeMaximum(other: WebStatisticsCounters): WebStatisticsCounters =
        WebStatisticsCounters(
            played = maxOf(played, other.played),
            solved = maxOf(solved, other.solved),
            failed = maxOf(failed, other.failed),
            hints = maxOf(hints, other.hints),
            wordSolvedAttempts =
                (wordSolvedAttempts.keys + other.wordSolvedAttempts.keys).associateWithNotNull { attempt ->
                    maxOf(wordSolvedAttempts[attempt] ?: 0L, other.wordSolvedAttempts[attempt] ?: 0L)
                        .takeIf { it > 0L }
                },
        )

    internal fun addChecked(other: WebStatisticsCounters): WebStatisticsCounters =
        WebStatisticsCounters(
            played = addCounterChecked(played, other.played),
            solved = addCounterChecked(solved, other.solved),
            failed = addCounterChecked(failed, other.failed),
            hints = addCounterChecked(hints, other.hints),
            wordSolvedAttempts =
                (wordSolvedAttempts.keys + other.wordSolvedAttempts.keys).associateWithNotNull { attempt ->
                    addCounterChecked(
                        wordSolvedAttempts[attempt] ?: 0L,
                        other.wordSolvedAttempts[attempt] ?: 0L,
                    ).takeIf { it > 0L }
                },
        )
}

internal data class WebStatisticsDeviceComponent(
    val buckets: Map<WebStatisticsBucket, WebStatisticsCounters> = emptyMap(),
) {
    init {
        require(buckets.size <= MAX_BUCKETS) { "A Web statistics device has too many buckets." }
        buckets.forEach { (bucket, counters) ->
            require(bucket.puzzleType in WEB_STATISTICS_PUZZLE_TYPES)
            require(bucket.puzzleType == PuzzleType.WORD || counters.wordSolvedAttempts.isEmpty()) {
                "Solved-attempt distribution is valid only for Word."
            }
        }
    }

    companion object {
        const val MAX_BUCKETS = 7 * 4
    }
}

/**
 * The sums of device components folded away to keep the section small, plus the ids folded into
 * them. A component whose id is folded is dropped by every merge, so no play is counted twice; two
 * archives merge by per-counter maximum, which may undercount but never double-counts.
 */
internal data class WebStatisticsArchive(
    val totals: WebStatisticsDeviceComponent = WebStatisticsDeviceComponent(),
    val foldedIds: Set<String> = emptySet(),
) {
    init {
        require(foldedIds.all(WebInstallationId::isValid)) { "Invalid folded Web statistics device ID." }
    }

    val isEmpty: Boolean
        get() = totals.buckets.isEmpty() && foldedIds.isEmpty()

    companion object {
        val EMPTY = WebStatisticsArchive()
    }
}

/**
 * Versioned Web-only statistics. Map keys are stable browser-installation component IDs; a
 * component appears only once its installation records a terminal attempt.
 */
internal data class WebStatisticsSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val components: Map<String, WebStatisticsDeviceComponent> = emptyMap(),
    val archive: WebStatisticsArchive = WebStatisticsArchive.EMPTY,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported Web statistics schema $schemaVersion." }
        require(components.size <= MAX_DEVICE_COMPONENTS) { "Too many Web statistics device components." }
        require(components.keys.all(WebInstallationId::isValid)) { "Invalid Web statistics device ID." }
        require(components.keys.none { it in archive.foldedIds }) { "A folded Web statistics component is still present." }
        WebStatisticsAggregator.aggregate(this).totals()
    }

    companion object {
        /** Schema 2 adds the archive; a snapshot without one is still written as schema 1. */
        const val CURRENT_SCHEMA_VERSION = 2
        const val MAX_DEVICE_COMPONENTS = WebStatisticsPayloadLimits.MAX_DEVICE_COMPONENTS
        val EMPTY = WebStatisticsSnapshot()
    }
}

internal data class WebStatisticsAggregate(
    val buckets: Map<WebStatisticsBucket, WebStatisticsCounters>,
) {
    fun totals(
        puzzleType: PuzzleType? = null,
        difficulty: Difficulty? = null,
    ): WebStatisticsCounters =
        buckets.entries
            .asSequence()
            .filter { (bucket, _) ->
                (puzzleType == null || bucket.puzzleType == puzzleType) &&
                    (difficulty == null || bucket.difficulty == difficulty)
            }.fold(WebStatisticsCounters()) { total, (_, counters) -> total.addChecked(counters) }
}

internal object WebStatisticsAggregator {
    fun aggregate(snapshot: WebStatisticsSnapshot): WebStatisticsAggregate {
        val totals = linkedMapOf<WebStatisticsBucket, WebStatisticsCounters>()
        (snapshot.components.values + snapshot.archive.totals).forEach { component ->
            component.buckets.forEach { (bucket, counters) ->
                totals[bucket] = (totals[bucket] ?: WebStatisticsCounters()).addChecked(counters)
            }
        }
        return WebStatisticsAggregate(totals)
    }
}

internal object WebStatisticsMerger {
    fun merge(
        local: WebStatisticsSnapshot,
        cloud: WebStatisticsSnapshot,
    ): WebStatisticsSnapshot {
        // Folded ids union; their components are already counted in an archive and are dropped.
        val folded = local.archive.foldedIds + cloud.archive.foldedIds
        val components = linkedMapOf<String, WebStatisticsDeviceComponent>()
        (local.components.keys + cloud.components.keys).filterNot { it in folded }.sorted().forEach { deviceId ->
            val localComponent = local.components[deviceId]
            val cloudComponent = cloud.components[deviceId]
            components[deviceId] =
                when {
                    localComponent == null -> checkNotNull(cloudComponent)
                    cloudComponent == null -> localComponent
                    else -> mergeComponent(localComponent, cloudComponent)
                }
        }
        val archive =
            WebStatisticsArchive(
                totals = mergeComponent(local.archive.totals, cloud.archive.totals),
                foldedIds = folded,
            )
        return WebStatisticsSnapshot(components = components, archive = archive)
    }

    private fun mergeComponent(
        local: WebStatisticsDeviceComponent,
        cloud: WebStatisticsDeviceComponent,
    ): WebStatisticsDeviceComponent {
        val buckets = linkedMapOf<WebStatisticsBucket, WebStatisticsCounters>()
        (local.buckets.keys + cloud.buckets.keys)
            .sortedWith(compareBy({ puzzleCode(it.puzzleType) }, { difficultyCode(it.difficulty) }))
            .forEach { bucket ->
                val localCounters = local.buckets[bucket]
                val cloudCounters = cloud.buckets[bucket]
                buckets[bucket] =
                    when {
                        localCounters == null -> checkNotNull(cloudCounters)
                        cloudCounters == null -> localCounters
                        else -> localCounters.mergeMaximum(cloudCounters)
                    }
            }
        return WebStatisticsDeviceComponent(buckets)
    }
}

/**
 * Keeps the section small: beyond [MAX_ACTIVE_COMPONENTS] components the ones with the fewest plays
 * are folded into the archive (their sums added, their ids remembered). The current installation's
 * own component is never folded, and folding stops once the folded ids reach their byte budget.
 */
internal object WebStatisticsCompaction {
    /** With the archive and id budgets this keeps every section this build writes below ~40 KB. */
    const val MAX_ACTIVE_COMPONENTS = 12

    /** Bytes the folded ids may take in the payload (2-byte length plus the id each). */
    const val MAX_FOLDED_ID_BYTES = 8 * 1024

    fun compact(
        snapshot: WebStatisticsSnapshot,
        protectedId: String,
    ): WebStatisticsSnapshot {
        val excess = snapshot.components.size - MAX_ACTIVE_COMPONENTS
        if (excess <= 0) return snapshot
        var idBytes = snapshot.archive.foldedIds.sumOf(::foldedIdBytes)
        val candidates =
            snapshot.components.entries
                .filter { it.key != protectedId }
                .sortedWith(compareBy({ played(it.value) }, { it.key }))
        val folding = mutableListOf<String>()
        for (candidate in candidates) {
            if (folding.size == excess) break
            val bytes = foldedIdBytes(candidate.key)
            if (idBytes + bytes > MAX_FOLDED_ID_BYTES) break
            idBytes += bytes
            folding += candidate.key
        }
        if (folding.isEmpty()) return snapshot
        val totals = LinkedHashMap(snapshot.archive.totals.buckets)
        folding.forEach { id ->
            snapshot.components.getValue(id).buckets.forEach { (bucket, counters) ->
                totals[bucket] = (totals[bucket] ?: WebStatisticsCounters()).addChecked(counters)
            }
        }
        return snapshot.copy(
            components = snapshot.components - folding.toSet(),
            archive = WebStatisticsArchive(WebStatisticsDeviceComponent(totals), snapshot.archive.foldedIds + folding),
        )
    }

    private fun played(component: WebStatisticsDeviceComponent): Long = component.buckets.values.sumOf { it.played }

    private fun foldedIdBytes(id: String): Int = Short.SIZE_BYTES + id.encodeToByteArray().size
}

/**
 * Compact deterministic binary format. All lifetime counters are signed-safe 64-bit values. Schema 1
 * is the components alone; schema 2 (written only when an archive exists) appends the archive's
 * buckets and folded ids. Both decode.
 */
internal object WebStatisticsCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte())
    private const val HEADER_SIZE = WebStatisticsPayloadLimits.HEADER_SIZE
    private const val COMPONENT_HEADER_SIZE = WebStatisticsPayloadLimits.COMPONENT_HEADER_SIZE
    private const val BUCKET_SIZE = WebStatisticsPayloadLimits.BUCKET_SIZE
    internal const val MAX_PAYLOAD_SIZE = WebStatisticsPayloadLimits.MAX_RAW_PAYLOAD_SIZE

    /** The encoded size of [snapshot] in bytes. */
    fun encodedSize(snapshot: WebStatisticsSnapshot): Long {
        val archiveSize =
            if (snapshot.archive.isEmpty) {
                0L
            } else {
                Int.SIZE_BYTES.toLong() + snapshot.archive.totals.buckets.size
                    .toLong() * BUCKET_SIZE +
                    Int.SIZE_BYTES + snapshot.archive.foldedIds.sumOf { Short.SIZE_BYTES + it.encodeToByteArray().size }
            }
        return HEADER_SIZE.toLong() +
            snapshot.components.entries.sumOf { (deviceId, component) ->
                COMPONENT_HEADER_SIZE.toLong() +
                    deviceId.encodeToByteArray().size +
                    component.buckets.size.toLong() * BUCKET_SIZE
            } + archiveSize
    }

    fun encode(snapshot: WebStatisticsSnapshot): ByteArray {
        val components = snapshot.components.entries.sortedBy(Map.Entry<String, *>::key)
        val size = encodedSize(snapshot)
        require(size <= MAX_PAYLOAD_SIZE) { "Web statistics payload is too large." }
        val result = ByteArray(size.toInt())
        magic.copyInto(result)
        // Without an archive the payload stays schema 1, so older builds keep reading it.
        writeInt(result, 4, if (snapshot.archive.isEmpty) LEGACY_SCHEMA_VERSION else WebStatisticsSnapshot.CURRENT_SCHEMA_VERSION)
        writeInt(result, 8, components.size)
        var offset = HEADER_SIZE
        components.forEach { (deviceId, component) ->
            val encodedDeviceId = deviceId.encodeToByteArray()
            writeUnsignedShort(result, offset, encodedDeviceId.size)
            offset += Short.SIZE_BYTES
            encodedDeviceId.copyInto(result, offset)
            offset += encodedDeviceId.size
            writeInt(result, offset, component.buckets.size)
            offset += Int.SIZE_BYTES
            offset = writeBuckets(result, offset, component.buckets)
        }
        if (!snapshot.archive.isEmpty) {
            writeInt(result, offset, snapshot.archive.totals.buckets.size)
            offset = writeBuckets(result, offset + Int.SIZE_BYTES, snapshot.archive.totals.buckets)
            val folded = snapshot.archive.foldedIds.sorted()
            writeInt(result, offset, folded.size)
            offset += Int.SIZE_BYTES
            folded.forEach { id ->
                val encoded = id.encodeToByteArray()
                writeUnsignedShort(result, offset, encoded.size)
                offset += Short.SIZE_BYTES
                encoded.copyInto(result, offset)
                offset += encoded.size
            }
        }
        check(offset == result.size)
        return result
    }

    private fun writeBuckets(
        result: ByteArray,
        startOffset: Int,
        buckets: Map<WebStatisticsBucket, WebStatisticsCounters>,
    ): Int {
        var offset = startOffset
        buckets.entries
            .sortedWith(compareBy({ puzzleCode(it.key.puzzleType) }, { difficultyCode(it.key.difficulty) }))
            .forEach { (bucket, counters) ->
                result[offset++] = puzzleCode(bucket.puzzleType).toByte()
                result[offset++] = difficultyCode(bucket.difficulty).toByte()
                writeLong(result, offset, counters.played)
                offset += Long.SIZE_BYTES
                writeLong(result, offset, counters.solved)
                offset += Long.SIZE_BYTES
                writeLong(result, offset, counters.failed)
                offset += Long.SIZE_BYTES
                writeLong(result, offset, counters.hints)
                offset += Long.SIZE_BYTES
                for (attempt in 1..WordRules.MAXIMUM_ATTEMPTS) {
                    writeLong(result, offset, counters.wordSolvedAttempts[attempt] ?: 0L)
                    offset += Long.SIZE_BYTES
                }
            }
        return offset
    }

    private const val LEGACY_SCHEMA_VERSION = 1

    fun decode(payload: ByteArray): WebStatisticsSnapshot? =
        runCatching {
            require(payload.size in HEADER_SIZE..MAX_PAYLOAD_SIZE)
            require(magic.indices.all { payload[it] == magic[it] })
            val reader = Reader(payload, magic.size)
            val schemaVersion = reader.readInt()
            require(schemaVersion == LEGACY_SCHEMA_VERSION || schemaVersion == WebStatisticsSnapshot.CURRENT_SCHEMA_VERSION)
            val componentCount = reader.readInt()
            require(componentCount in 0..WebStatisticsSnapshot.MAX_DEVICE_COMPONENTS)
            val components = linkedMapOf<String, WebStatisticsDeviceComponent>()
            repeat(componentCount) {
                val deviceIdLength = reader.readUnsignedShort()
                require(deviceIdLength in WebInstallationId.MIN_LENGTH..WebInstallationId.MAX_LENGTH)
                val deviceId = reader.readBytes(deviceIdLength).decodeToString()
                require(WebInstallationId.isValid(deviceId))
                require(components.put(deviceId, readComponent(reader)) == null) {
                    "Duplicate Web statistics device component."
                }
            }
            var archive = WebStatisticsArchive.EMPTY
            if (schemaVersion == WebStatisticsSnapshot.CURRENT_SCHEMA_VERSION) {
                val totals = readComponent(reader)
                val foldedCount = reader.readInt()
                require(foldedCount in 0..MAX_PAYLOAD_SIZE / (Short.SIZE_BYTES + WebInstallationId.MIN_LENGTH))
                val folded = linkedSetOf<String>()
                repeat(foldedCount) {
                    val length = reader.readUnsignedShort()
                    require(length in WebInstallationId.MIN_LENGTH..WebInstallationId.MAX_LENGTH)
                    require(folded.add(reader.readBytes(length).decodeToString())) { "Duplicate folded Web statistics ID." }
                }
                archive = WebStatisticsArchive(totals, folded)
            }
            require(reader.isAtEnd)
            WebStatisticsSnapshot(components = components, archive = archive)
        }.getOrNull()

    private fun readComponent(reader: Reader): WebStatisticsDeviceComponent {
        val bucketCount = reader.readInt()
        require(bucketCount in 0..WebStatisticsDeviceComponent.MAX_BUCKETS)
        val buckets = linkedMapOf<WebStatisticsBucket, WebStatisticsCounters>()
        repeat(bucketCount) {
            val bucket = WebStatisticsBucket(puzzleType(reader.readByte()), difficulty(reader.readByte()))
            val played = reader.readCounter()
            val solved = reader.readCounter()
            val failed = reader.readCounter()
            val hints = reader.readCounter()
            val attempts = linkedMapOf<Int, Long>()
            for (attempt in 1..WordRules.MAXIMUM_ATTEMPTS) {
                val count = reader.readCounter()
                if (count > 0L) attempts[attempt] = count
            }
            val counters = WebStatisticsCounters(played, solved, failed, hints, attempts)
            require(buckets.put(bucket, counters) == null) { "Duplicate Web statistics bucket." }
        }
        return WebStatisticsDeviceComponent(buckets)
    }

    private class Reader(
        private val payload: ByteArray,
        private var offset: Int,
    ) {
        val isAtEnd: Boolean
            get() = offset == payload.size

        fun readByte(): Int {
            requireAvailable(1)
            return payload[offset++].toInt() and 0xff
        }

        fun readUnsignedShort(): Int {
            requireAvailable(Short.SIZE_BYTES)
            val value =
                ((payload[offset].toInt() and 0xff) shl 8) or
                    (payload[offset + 1].toInt() and 0xff)
            offset += Short.SIZE_BYTES
            return value
        }

        fun readInt(): Int {
            requireAvailable(Int.SIZE_BYTES)
            val value =
                ((payload[offset].toInt() and 0xff) shl 24) or
                    ((payload[offset + 1].toInt() and 0xff) shl 16) or
                    ((payload[offset + 2].toInt() and 0xff) shl 8) or
                    (payload[offset + 3].toInt() and 0xff)
            offset += Int.SIZE_BYTES
            return value
        }

        fun readCounter(): Long {
            requireAvailable(Long.SIZE_BYTES)
            var value = 0L
            repeat(Long.SIZE_BYTES) {
                value = (value shl 8) or (payload[offset++].toLong() and 0xffL)
            }
            require(value >= 0L) { "Web statistics counter overflow." }
            return value
        }

        fun readBytes(count: Int): ByteArray {
            requireAvailable(count)
            return payload.copyOfRange(offset, offset + count).also { offset += count }
        }

        private fun requireAvailable(count: Int) {
            require(count >= 0 && offset <= payload.size - count) { "Truncated Web statistics payload." }
        }
    }

    private fun writeUnsignedShort(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        require(value in 0..0xffff)
        destination[offset] = (value ushr 8).toByte()
        destination[offset + 1] = value.toByte()
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

    private fun writeLong(
        destination: ByteArray,
        offset: Int,
        value: Long,
    ) {
        require(value >= 0L)
        repeat(Long.SIZE_BYTES) { index ->
            destination[offset + index] = (value ushr (56 - index * 8)).toByte()
        }
    }
}

/** Leaves headroom for Base64, the Catalog payload, property names, and Yandex Player data overhead. */
private object WebStatisticsPayloadLimits {
    const val HEADER_SIZE = 12
    const val COMPONENT_HEADER_SIZE = 6
    const val BUCKET_SIZE = 2 + (4 + WordRules.MAXIMUM_ATTEMPTS) * Long.SIZE_BYTES
    const val MAX_RAW_PAYLOAD_SIZE = 120 * 1024
    private const val MAX_COMPONENT_SIZE =
        COMPONENT_HEADER_SIZE +
            WebInstallationId.MAX_LENGTH +
            WebStatisticsDeviceComponent.MAX_BUCKETS * BUCKET_SIZE
    const val MAX_DEVICE_COMPONENTS = (MAX_RAW_PAYLOAD_SIZE - HEADER_SIZE) / MAX_COMPONENT_SIZE
}

internal object WebInstallationId {
    const val MIN_LENGTH = 16
    const val MAX_LENGTH = 128

    fun isValid(value: String): Boolean =
        value.length in MIN_LENGTH..MAX_LENGTH &&
            value.all {
                it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_'
            }
}

private fun addCounterChecked(
    first: Long,
    second: Long,
): Long {
    require(first >= 0L && second >= 0L)
    require(first <= Long.MAX_VALUE - second) { "Web statistics counter overflow." }
    return first + second
}

private fun <K, V : Any> Iterable<K>.associateWithNotNull(valueSelector: (K) -> V?): Map<K, V> {
    val destination = linkedMapOf<K, V>()
    forEach { key -> valueSelector(key)?.let { destination[key] = it } }
    return destination
}

private val WEB_STATISTICS_PUZZLE_TYPES =
    setOf(
        PuzzleType.BALANCE,
        PuzzleType.CROWNS,
        PuzzleType.WORD,
        PuzzleType.SUDOKU,
        PuzzleType.GAME_2048,
        PuzzleType.NONOGRAM,
        PuzzleType.BLOCK_SUDOKU,
    )

private fun puzzleCode(puzzleType: PuzzleType): Int =
    when (puzzleType) {
        PuzzleType.BALANCE -> 1
        PuzzleType.CROWNS -> 2
        PuzzleType.WORD -> 3
        PuzzleType.SUDOKU -> 4
        PuzzleType.GAME_2048 -> 5
        PuzzleType.NONOGRAM -> 6
        PuzzleType.BLOCK_SUDOKU -> 7
        else -> error("$puzzleType has no Web statistics code.")
    }

private fun puzzleType(code: Int): PuzzleType =
    when (code) {
        1 -> PuzzleType.BALANCE
        2 -> PuzzleType.CROWNS
        3 -> PuzzleType.WORD
        4 -> PuzzleType.SUDOKU
        5 -> PuzzleType.GAME_2048
        6 -> PuzzleType.NONOGRAM
        7 -> PuzzleType.BLOCK_SUDOKU
        else -> error("Unknown Web statistics puzzle code $code.")
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
        else -> error("Unknown Web statistics difficulty code $code.")
    }
