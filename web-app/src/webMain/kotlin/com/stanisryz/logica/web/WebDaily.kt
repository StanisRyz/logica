package com.stanisryz.logica.web

import androidx.compose.runtime.compositionLocalOf
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV1
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV8
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.daily.toDailyEpochDay
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV2
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.ui.nonogram.DailyGalleryPicture

internal data class WebDailyEntryFacts(
    val failedSeen: Boolean = false,
    val solved: Boolean = false,
)

/**
 * One durable calendar day. Masks use [WebDailyPuzzleOrder] and contain lifecycle facts, never
 * attempts. [onTimeSolvedMask] holds the entries solved by an attempt started on the day itself,
 * never from the Daily archive: only those keep the streak, while [solvedMask] holds every solve.
 */
internal data class WebDailyDayRecord(
    val date: DailyDate,
    val policyVersion: DailyPolicyVersion,
    val failedMask: Int = 0,
    val solvedMask: Int = 0,
    val wordSolvedAttemptsUsed: Int? = null,
    val onTimeSolvedMask: Int = solvedMask,
) {
    init {
        require(date.getYear() in MIN_YEAR..MAX_YEAR) { "Web Daily dates must use years $MIN_YEAR..$MAX_YEAR." }
        require(policyVersion.value in MIN_POLICY_VERSION..MAX_POLICY_VERSION) {
            "Unsupported Web Daily policy version ${policyVersion.value}."
        }
        require(failedMask and WebDailyPuzzleOrder.ALL_MASK == failedMask) { "Web Daily failed mask is invalid." }
        require(solvedMask and WebDailyPuzzleOrder.ALL_MASK == solvedMask) { "Web Daily solved mask is invalid." }
        require(onTimeSolvedMask and solvedMask == onTimeSolvedMask) { "Web Daily on-time solves must be solves." }

        val definition = DailyChallengePolicyResolver.definitionFor(date, policyVersion)
        val requiredMask = WebDailyPuzzleOrder.maskOf(definition.entries.map { it.puzzleType })
        require(failedMask and requiredMask == failedMask) { "Web Daily failed mask contains a non-policy puzzle." }
        require(solvedMask and requiredMask == solvedMask) { "Web Daily solved mask contains a non-policy puzzle." }
        if (wordSolvedAttemptsUsed != null) {
            require(wordSolvedAttemptsUsed in 1..WordRules.MAXIMUM_ATTEMPTS) {
                "Web Daily Word attempts are outside the supported range."
            }
            require(solvedMask and WebDailyPuzzleOrder.bit(PuzzleType.WORD) != 0) {
                "Web Daily Word attempts require a solved Word entry."
            }
        }
    }

    fun facts(puzzleType: PuzzleType): WebDailyEntryFacts {
        val bit = WebDailyPuzzleOrder.bit(puzzleType)
        require(requiredMask and bit != 0) { "$puzzleType is not part of Daily Policy ${policyVersion.value}." }
        return WebDailyEntryFacts(
            failedSeen = failedMask and bit != 0,
            solved = solvedMask and bit != 0,
        )
    }

    val requiredMask: Int
        get() =
            WebDailyPuzzleOrder.maskOf(
                DailyChallengePolicyResolver.definitionFor(date, policyVersion).entries.map { it.puzzleType },
            )

    val completedEntryCount: Int
        get() = WebDailyPuzzleOrder.countBits(solvedMask and requiredMask)

    val fullyCompleted: Boolean
        get() = solvedMask and requiredMask == requiredMask

    /** Only solves made on the day itself keep the streak; archive play never does. */
    val qualifiedForStreak: Boolean
        get() =
            if (DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(policyVersion)) {
                onTimeSolvedMask != 0
            } else {
                onTimeSolvedMask and requiredMask == requiredMask
            }

    companion object {
        const val MIN_YEAR = 1
        const val MAX_YEAR = 9_999
        private val MIN_POLICY_VERSION = DailyChallengePolicyV1.VERSION.value
        private val MAX_POLICY_VERSION = DailyChallengePolicyV8.VERSION.value
    }
}

internal data class WebDailySnapshotV1(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val days: Map<DailyDate, WebDailyDayRecord> = emptyMap(),
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported Web Daily schema $schemaVersion." }
        require(days.all { (date, record) -> date == record.date }) { "Web Daily date keys must match their records." }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val EMPTY = WebDailySnapshotV1()
    }
}

/** Stable canonical bit mapping shared by the domain and binary codec. */
internal object WebDailyPuzzleOrder {
    val puzzleTypes =
        listOf(
            PuzzleType.BALANCE,
            PuzzleType.CROWNS,
            PuzzleType.WORD,
            PuzzleType.SUDOKU,
            PuzzleType.GAME_2048,
            // Appended for Policies V6 and V7: existing bits keep their meaning in stored snapshots.
            PuzzleType.NONOGRAM,
            PuzzleType.BLOCK_SUDOKU,
        )
    val ALL_MASK = (1 shl puzzleTypes.size) - 1

    fun bit(puzzleType: PuzzleType): Int {
        val index = puzzleTypes.indexOf(puzzleType)
        require(index >= 0) { "$puzzleType has no Web Daily bit." }
        return 1 shl index
    }

    fun maskOf(puzzleTypes: Iterable<PuzzleType>): Int = puzzleTypes.fold(0) { mask, puzzleType -> mask or bit(puzzleType) }

    fun countBits(mask: Int): Int {
        var remaining = mask
        var count = 0
        while (remaining != 0) {
            count += remaining and 1
            remaining = remaining ushr 1
        }
        return count
    }
}

/**
 * Idempotent union of monotonic Daily facts, commutative except on a date whose two records carry
 * different policies: that date keeps the first (preferred, local) record and the other side's
 * record is dropped, while every other date is still merged.
 */
internal object WebDailyMerger {
    data class Result(
        val snapshot: WebDailySnapshotV1,
        /** Dates whose second-side record was dropped for a policy conflict. */
        val policyConflicts: List<DailyDate>,
    )

    fun merge(
        first: WebDailySnapshotV1,
        second: WebDailySnapshotV1,
    ): WebDailySnapshotV1 = mergeReporting(first, second).snapshot

    fun mergeReporting(
        first: WebDailySnapshotV1,
        second: WebDailySnapshotV1,
    ): Result {
        val merged = linkedMapOf<DailyDate, WebDailyDayRecord>()
        val conflicts = mutableListOf<DailyDate>()
        (first.days.keys + second.days.keys).sortedWith(webDailyDateComparator).forEach { date ->
            val firstRecord = first.days[date]
            val secondRecord = second.days[date]
            merged[date] =
                when {
                    firstRecord == null -> checkNotNull(secondRecord)
                    secondRecord == null -> firstRecord
                    firstRecord.policyVersion != secondRecord.policyVersion -> {
                        conflicts += date
                        firstRecord
                    }
                    else ->
                        WebDailyDayRecord(
                            date = date,
                            policyVersion = firstRecord.policyVersion,
                            failedMask = firstRecord.failedMask or secondRecord.failedMask,
                            solvedMask = firstRecord.solvedMask or secondRecord.solvedMask,
                            onTimeSolvedMask = firstRecord.onTimeSolvedMask or secondRecord.onTimeSolvedMask,
                            wordSolvedAttemptsUsed =
                                listOfNotNull(
                                    firstRecord.wordSolvedAttemptsUsed,
                                    secondRecord.wordSolvedAttemptsUsed,
                                ).minOrNull(),
                        )
                }
        }
        return Result(WebDailySnapshotV1(days = merged), conflicts)
    }
}

/**
 * Eight-byte records keep complete history within a conservative 24 KiB raw payload budget. Schema 2
 * adds a ninth byte, the on-time solved mask; a snapshot whose solves were all made on their own day
 * is still written as schema 1, so older builds keep reading it, and a schema-1 record reads with
 * every solve on time, which is what every solve before the archive was.
 */
internal object WebDailyCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'D'.code.toByte(), 'Y'.code.toByte())
    private const val HEADER_SIZE = 8
    private const val LEGACY_SCHEMA_VERSION = 1
    private const val ON_TIME_SCHEMA_VERSION = 2
    private const val LEGACY_RECORD_SIZE = 8
    private const val ON_TIME_RECORD_SIZE = 9
    internal const val MAX_PAYLOAD_SIZE = 24 * 1024

    private fun recordSize(schema: Int): Int = if (schema == ON_TIME_SCHEMA_VERSION) ON_TIME_RECORD_SIZE else LEGACY_RECORD_SIZE

    private fun maxRecords(schema: Int): Int = (MAX_PAYLOAD_SIZE - HEADER_SIZE) / recordSize(schema)

    fun encode(snapshot: WebDailySnapshotV1): ByteArray {
        val records =
            snapshot.days.values.sortedWith(
                Comparator { first, second -> webDailyDateComparator.compare(first.date, second.date) },
            )
        val schema =
            if (records.all { it.onTimeSolvedMask == it.solvedMask }) LEGACY_SCHEMA_VERSION else ON_TIME_SCHEMA_VERSION
        val recordSize = recordSize(schema)
        require(records.size <= maxRecords(schema)) { "Web Daily payload is too large; history cannot be pruned." }
        val result = ByteArray(HEADER_SIZE + records.size * recordSize)
        magic.copyInto(result)
        result[4] = schema.toByte()
        result[5] = 0
        writeUnsignedShort(result, 6, records.size)
        var offset = HEADER_SIZE
        records.forEach { record ->
            writeUnsignedShort(result, offset, record.date.getYear())
            result[offset + 2] = record.date.getMonthValue().toByte()
            result[offset + 3] = record.date.getDayOfMonth().toByte()
            result[offset + 4] = record.policyVersion.value.toByte()
            result[offset + 5] = record.failedMask.toByte()
            result[offset + 6] = record.solvedMask.toByte()
            result[offset + 7] = (record.wordSolvedAttemptsUsed ?: 0).toByte()
            if (schema == ON_TIME_SCHEMA_VERSION) result[offset + 8] = record.onTimeSolvedMask.toByte()
            offset += recordSize
        }
        return result
    }

    fun decode(payload: ByteArray): WebDailySnapshotV1? =
        runCatching {
            require(payload.size in HEADER_SIZE..MAX_PAYLOAD_SIZE)
            require(magic.indices.all { payload[it] == magic[it] })
            val schema = payload[4].toInt() and 0xff
            require(schema == LEGACY_SCHEMA_VERSION || schema == ON_TIME_SCHEMA_VERSION)
            require(payload[5].toInt() == 0)
            val recordSize = recordSize(schema)
            val recordCount = readUnsignedShort(payload, 6)
            require(recordCount in 0..maxRecords(schema))
            require(payload.size == HEADER_SIZE + recordCount * recordSize)

            val records = linkedMapOf<DailyDate, WebDailyDayRecord>()
            var offset = HEADER_SIZE
            repeat(recordCount) {
                val date =
                    DailyDate(
                        readUnsignedShort(payload, offset),
                        payload[offset + 2].toInt() and 0xff,
                        payload[offset + 3].toInt() and 0xff,
                    )
                val attempts = (payload[offset + 7].toInt() and 0xff).takeUnless { it == 0 }
                val solvedMask = payload[offset + 6].toInt() and 0xff
                val record =
                    WebDailyDayRecord(
                        date = date,
                        policyVersion = DailyPolicyVersion(payload[offset + 4].toInt() and 0xff),
                        failedMask = payload[offset + 5].toInt() and 0xff,
                        solvedMask = solvedMask,
                        wordSolvedAttemptsUsed = attempts,
                        onTimeSolvedMask = if (schema == ON_TIME_SCHEMA_VERSION) payload[offset + 8].toInt() and 0xff else solvedMask,
                    )
                require(records.put(date, record) == null) { "Duplicate Web Daily date." }
                offset += recordSize
            }
            WebDailySnapshotV1(days = records)
        }.getOrNull()

    private fun writeUnsignedShort(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        require(value in 0..0xffff)
        destination[offset] = (value ushr 8).toByte()
        destination[offset + 1] = value.toByte()
    }

    private fun readUnsignedShort(
        source: ByteArray,
        offset: Int,
    ): Int = ((source[offset].toInt() and 0xff) shl 8) or (source[offset + 1].toInt() and 0xff)
}

internal val webDailyDateComparator =
    compareBy<DailyDate>(
        { it.getYear() },
        { it.getMonthValue() },
        { it.getDayOfMonth() },
    )

internal fun DailyDate.isAfter(other: DailyDate): Boolean = webDailyDateComparator.compare(this, other) > 0

/** The bound Player's solved Daily Nonogram pictures, newest first, for the gallery. */
internal val LocalWebDailyPictures = compositionLocalOf { emptyList<DailyGalleryPicture>() }

/** Every date whose Daily Nonogram is solved, rebuilt from its own policy entry (Generator V2). */
internal fun solvedDailyNonogramPictures(snapshot: WebDailySnapshotV1): List<DailyGalleryPicture> {
    val generator = NonogramGeneratorV2()
    return snapshot.days.values
        .filter { it.solvedMask and WebDailyPuzzleOrder.bit(PuzzleType.NONOGRAM) != 0 }
        .sortedByDescending { it.date.toDailyEpochDay() }
        .mapNotNull { record ->
            runCatching {
                val entry =
                    DailyChallengePolicyResolver
                        .definitionFor(record.date, record.policyVersion)
                        .entries
                        .first { it.puzzleType == PuzzleType.NONOGRAM }
                DailyGalleryPicture(formatWebDailyShortDate(record.date), generator.generate(entry.seed, entry.difficulty))
            }.getOrNull()
        }
}
