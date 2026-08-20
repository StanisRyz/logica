package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV1
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules

internal data class WebDailyEntryFacts(
    val failedSeen: Boolean = false,
    val solved: Boolean = false,
)

/** One durable calendar day. Masks use [WebDailyPuzzleOrder] and contain lifecycle facts, never attempts. */
internal data class WebDailyDayRecord(
    val date: DailyDate,
    val policyVersion: DailyPolicyVersion,
    val failedMask: Int = 0,
    val solvedMask: Int = 0,
    val wordSolvedAttemptsUsed: Int? = null,
) {
    init {
        require(date.getYear() in MIN_YEAR..MAX_YEAR) { "Web Daily dates must use years $MIN_YEAR..$MAX_YEAR." }
        require(policyVersion.value in MIN_POLICY_VERSION..MAX_POLICY_VERSION) {
            "Unsupported Web Daily policy version ${policyVersion.value}."
        }
        require(failedMask and WebDailyPuzzleOrder.ALL_MASK == failedMask) { "Web Daily failed mask is invalid." }
        require(solvedMask and WebDailyPuzzleOrder.ALL_MASK == solvedMask) { "Web Daily solved mask is invalid." }

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

    val qualifiedForStreak: Boolean
        get() =
            if (DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(policyVersion)) {
                solvedMask != 0
            } else {
                fullyCompleted
            }

    companion object {
        const val MIN_YEAR = 1
        const val MAX_YEAR = 9_999
        private val MIN_POLICY_VERSION = DailyChallengePolicyV1.VERSION.value
        private val MAX_POLICY_VERSION = DailyChallengePolicyV5.VERSION.value
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

internal class WebDailyPolicyConflictException(
    val date: DailyDate,
    val firstPolicyVersion: DailyPolicyVersion,
    val secondPolicyVersion: DailyPolicyVersion,
) : IllegalArgumentException("Conflicting Web Daily policies for $date.")

/** Commutative, idempotent union of monotonic Daily facts. */
internal object WebDailyMerger {
    fun merge(
        first: WebDailySnapshotV1,
        second: WebDailySnapshotV1,
    ): WebDailySnapshotV1 {
        val merged = linkedMapOf<DailyDate, WebDailyDayRecord>()
        (first.days.keys + second.days.keys).sortedWith(webDailyDateComparator).forEach { date ->
            val firstRecord = first.days[date]
            val secondRecord = second.days[date]
            merged[date] =
                when {
                    firstRecord == null -> checkNotNull(secondRecord)
                    secondRecord == null -> firstRecord
                    firstRecord.policyVersion != secondRecord.policyVersion ->
                        throw WebDailyPolicyConflictException(
                            date,
                            firstRecord.policyVersion,
                            secondRecord.policyVersion,
                        )
                    else ->
                        WebDailyDayRecord(
                            date = date,
                            policyVersion = firstRecord.policyVersion,
                            failedMask = firstRecord.failedMask or secondRecord.failedMask,
                            solvedMask = firstRecord.solvedMask or secondRecord.solvedMask,
                            wordSolvedAttemptsUsed =
                                listOfNotNull(
                                    firstRecord.wordSolvedAttemptsUsed,
                                    secondRecord.wordSolvedAttemptsUsed,
                                ).minOrNull(),
                        )
                }
        }
        return WebDailySnapshotV1(days = merged)
    }
}

/** Eight-byte records keep complete history within a conservative 24 KiB raw payload budget. */
internal object WebDailyCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'D'.code.toByte(), 'Y'.code.toByte())
    private const val HEADER_SIZE = 8
    private const val RECORD_SIZE = 8
    internal const val MAX_PAYLOAD_SIZE = 24 * 1024
    private const val MAX_RECORDS = (MAX_PAYLOAD_SIZE - HEADER_SIZE) / RECORD_SIZE

    fun encode(snapshot: WebDailySnapshotV1): ByteArray {
        val records =
            snapshot.days.values.sortedWith(
                Comparator { first, second -> webDailyDateComparator.compare(first.date, second.date) },
            )
        require(records.size <= MAX_RECORDS) { "Web Daily payload is too large; history cannot be pruned." }
        val result = ByteArray(HEADER_SIZE + records.size * RECORD_SIZE)
        magic.copyInto(result)
        result[4] = snapshot.schemaVersion.toByte()
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
            offset += RECORD_SIZE
        }
        return result
    }

    fun decode(payload: ByteArray): WebDailySnapshotV1? =
        runCatching {
            require(payload.size in HEADER_SIZE..MAX_PAYLOAD_SIZE)
            require(magic.indices.all { payload[it] == magic[it] })
            require((payload[4].toInt() and 0xff) == WebDailySnapshotV1.CURRENT_SCHEMA_VERSION)
            require(payload[5].toInt() == 0)
            val recordCount = readUnsignedShort(payload, 6)
            require(recordCount in 0..MAX_RECORDS)
            require(payload.size == HEADER_SIZE + recordCount * RECORD_SIZE)

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
                val record =
                    WebDailyDayRecord(
                        date = date,
                        policyVersion = DailyPolicyVersion(payload[offset + 4].toInt() and 0xff),
                        failedMask = payload[offset + 5].toInt() and 0xff,
                        solvedMask = payload[offset + 6].toInt() and 0xff,
                        wordSolvedAttemptsUsed = attempts,
                    )
                require(records.put(date, record) == null) { "Duplicate Web Daily date." }
                offset += RECORD_SIZE
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
