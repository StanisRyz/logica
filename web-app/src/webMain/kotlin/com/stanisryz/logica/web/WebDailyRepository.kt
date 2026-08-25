@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.DailyStreak
import com.stanisryz.logica.puzzle.core.daily.DailyStreakCalculator
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.js.ExperimentalWasmJsInterop

internal fun interface WebDailyDateProvider {
    fun currentDate(): DailyDate
}

/** Browser-local calendar boundary; puzzle-core receives an explicit date and never reads the clock. */
internal object BrowserLocalWebDailyDateProvider : WebDailyDateProvider {
    override fun currentDate(): DailyDate {
        val encoded = browserLocalDateCode()
        return DailyDate(
            year = encoded / 10_000,
            month = encoded / 100 % 100,
            day = encoded % 100,
        )
    }
}

internal enum class WebDailyEntryState {
    AVAILABLE,
    RETRY,
    COMPLETED,
}

internal data class WebDailyRunState(
    val definition: DailyChallengeDefinition,
    val isDurable: Boolean,
    val entries: Map<PuzzleType, WebDailyEntryState>,
    val completedEntryCount: Int,
    val fullyCompleted: Boolean,
    val qualifiedForStreak: Boolean,
    val wordSolvedAttemptsUsed: Int?,
)

internal data class WebDailyHistoryAggregate(
    val today: WebDailyRunState,
    val qualifiedDates: Set<DailyDate>,
    val fullyCompletedDailyCount: Int,
    val streak: DailyStreak,
)

internal interface WebDailyStore {
    fun load(): WebDailySnapshotV1

    fun save(snapshot: WebDailySnapshotV1)
}

/** Daily has a dedicated Player-scoped browser-local namespace and payload. */
internal class WebDailyLocalStore(
    scope: WebCatalogProgressScope,
) : WebDailyStore {
    internal val storageKey = "$LOCAL_STORAGE_KEY_PREFIX:${scope.keySuffix}"

    override fun load(): WebDailySnapshotV1 {
        val encoded = dailyLocalStorageGet(storageKey) ?: return WebDailySnapshotV1.EMPTY
        val payload = requireNotNull(WebBase64.decode(encoded)) { "Stored Web Daily data are not valid Base64." }
        return requireNotNull(WebDailyCodec.decode(payload)) { "Stored Web Daily data are invalid or over budget." }
    }

    override fun save(snapshot: WebDailySnapshotV1) {
        dailyLocalStorageSet(storageKey, WebBase64.encode(WebDailyCodec.encode(snapshot)))
    }

    private companion object {
        const val LOCAL_STORAGE_KEY_PREFIX = "logica_daily_v1"
    }
}

internal fun interface WebDailyRepositoryFactory {
    fun create(scope: WebCatalogProgressScope): WebDailyRepository
}

internal sealed interface WebDailyMutationResult {
    data class Updated(
        val snapshot: WebDailySnapshotV1,
    ) : WebDailyMutationResult

    data object Idempotent : WebDailyMutationResult

    data class Rejected(
        val cause: Throwable,
    ) : WebDailyMutationResult

    data class PersistenceFailed(
        val cause: Throwable,
    ) : WebDailyMutationResult
}

internal sealed interface WebDailyMergeResult {
    data class Merged(
        val snapshot: WebDailySnapshotV1,
        val cloudWriteRequired: Boolean,
    ) : WebDailyMergeResult

    data class PolicyConflict(
        val date: DailyDate,
    ) : WebDailyMergeResult

    data class PersistenceFailed(
        val cause: Throwable,
    ) : WebDailyMergeResult
}

/** Current-scope durable Daily history; active attempts and generated puzzle content never enter it. */
internal class WebDailyRepository(
    val scope: WebCatalogProgressScope,
    private val localStore: WebDailyStore,
    private val dateProvider: WebDailyDateProvider,
) {
    private val mutableSnapshot = MutableStateFlow(WebDailySnapshotV1.EMPTY)
    val snapshot: StateFlow<WebDailySnapshotV1> = mutableSnapshot.asStateFlow()

    /** Invoked after every successful durable local mutation; never after a cloud merge. */
    var onDurableChange: (() -> Unit)? = null

    fun loadLocal(): WebDailySnapshotV1 = localStore.load().also { mutableSnapshot.value = it }

    fun stateFor(date: DailyDate): WebDailyRunState {
        val record = mutableSnapshot.value.days[date]
        val policyVersion = record?.policyVersion ?: DailyChallengePolicyV5.VERSION
        return runState(
            DailyChallengePolicyResolver.definitionFor(date, policyVersion),
            record,
        )
    }

    fun history(currentDate: DailyDate = dateProvider.currentDate()): WebDailyHistoryAggregate {
        val relevant =
            mutableSnapshot.value.days.values
                .filterNot { it.date.isAfter(currentDate) }
        val qualifiedDates = relevant.filter(WebDailyDayRecord::qualifiedForStreak).mapTo(linkedSetOf()) { it.date }
        return WebDailyHistoryAggregate(
            today = stateFor(currentDate),
            qualifiedDates = qualifiedDates,
            fullyCompletedDailyCount = relevant.count(WebDailyDayRecord::fullyCompleted),
            streak = DailyStreakCalculator.calculate(currentDate, qualifiedDates),
        )
    }

    /** Creates only the durable identity; callers must invoke this when Daily gameplay actually starts. */
    fun ensureRun(definition: DailyChallengeDefinition): WebDailyMutationResult =
        mutate(definition, requireExisting = false) { record -> record }

    fun recordFailed(
        definition: DailyChallengeDefinition,
        puzzleType: PuzzleType,
    ): WebDailyMutationResult =
        mutate(definition, requireExisting = true) { record ->
            val bit = definition.requirePuzzleBit(puzzleType)
            record.copy(failedMask = record.failedMask or bit)
        }

    fun recordSolved(
        definition: DailyChallengeDefinition,
        puzzleType: PuzzleType,
        wordAttemptsUsed: Int? = null,
    ): WebDailyMutationResult =
        mutate(definition, requireExisting = true) { record ->
            val bit = definition.requirePuzzleBit(puzzleType)
            require(puzzleType == PuzzleType.WORD || wordAttemptsUsed == null) {
                "Daily attempts used belong only to Word."
            }
            if (wordAttemptsUsed != null) {
                require(wordAttemptsUsed in 1..WordRules.MAXIMUM_ATTEMPTS) {
                    "Daily Word attempts are outside the supported range."
                }
            }
            record.copy(
                solvedMask = record.solvedMask or bit,
                wordSolvedAttemptsUsed =
                    if (puzzleType == PuzzleType.WORD) {
                        listOfNotNull(record.wordSolvedAttemptsUsed, wordAttemptsUsed).minOrNull()
                    } else {
                        record.wordSolvedAttemptsUsed
                    },
            )
        }

    fun mergeCloud(cloud: WebDailySnapshotV1): WebDailyMergeResult {
        val local = mutableSnapshot.value
        val merged =
            try {
                WebDailyMerger.merge(local, cloud)
            } catch (conflict: WebDailyPolicyConflictException) {
                return WebDailyMergeResult.PolicyConflict(conflict.date)
            } catch (error: Throwable) {
                return WebDailyMergeResult.PersistenceFailed(error)
            }
        if (merged != local) {
            persist(merged)?.let { return WebDailyMergeResult.PersistenceFailed(it) }
            mutableSnapshot.value = merged
        }
        return WebDailyMergeResult.Merged(
            snapshot = merged,
            cloudWriteRequired = merged != cloud,
        )
    }

    private fun mutate(
        definition: DailyChallengeDefinition,
        requireExisting: Boolean,
        update: (WebDailyDayRecord) -> WebDailyDayRecord,
    ): WebDailyMutationResult {
        val updated =
            runCatching {
                requireCanonical(definition)
                val current = mutableSnapshot.value
                val existing = current.days[definition.challengeDate]
                require(existing == null || existing.policyVersion == definition.policyVersion) {
                    "A Daily date cannot change its persisted policy."
                }
                require(!requireExisting || existing != null) { "The Daily run must be ensured before recording a result." }
                val base =
                    existing ?: WebDailyDayRecord(definition.challengeDate, definition.policyVersion)
                val record = update(base)
                current.copy(days = current.days + (record.date to record))
            }.getOrElse { return WebDailyMutationResult.Rejected(it) }

        if (updated == mutableSnapshot.value) return WebDailyMutationResult.Idempotent
        persist(updated)?.let { return WebDailyMutationResult.PersistenceFailed(it) }
        mutableSnapshot.value = updated
        onDurableChange?.invoke()
        return WebDailyMutationResult.Updated(updated)
    }

    private fun persist(snapshot: WebDailySnapshotV1): Throwable? =
        runCatching {
            WebDailyCodec.encode(snapshot)
            localStore.save(snapshot)
        }.exceptionOrNull()

    private fun requireCanonical(definition: DailyChallengeDefinition) {
        require(
            definition ==
                DailyChallengePolicyResolver.definitionFor(
                    definition.challengeDate,
                    definition.policyVersion,
                ),
        ) { "Web Daily writes require the canonical deterministic policy definition." }
    }

    private fun runState(
        definition: DailyChallengeDefinition,
        record: WebDailyDayRecord?,
    ): WebDailyRunState {
        val entries =
            definition.entries.associate { entry ->
                val facts = record?.facts(entry.puzzleType) ?: WebDailyEntryFacts()
                entry.puzzleType to
                    when {
                        facts.solved -> WebDailyEntryState.COMPLETED
                        facts.failedSeen -> WebDailyEntryState.RETRY
                        else -> WebDailyEntryState.AVAILABLE
                    }
            }
        return WebDailyRunState(
            definition = definition,
            isDurable = record != null,
            entries = entries,
            completedEntryCount = record?.completedEntryCount ?: 0,
            fullyCompleted = record?.fullyCompleted == true,
            qualifiedForStreak = record?.qualifiedForStreak == true,
            wordSolvedAttemptsUsed = record?.wordSolvedAttemptsUsed,
        )
    }
}

private fun DailyChallengeDefinition.requirePuzzleBit(puzzleType: PuzzleType): Int {
    require(entries.any { it.puzzleType == puzzleType }) {
        "$puzzleType is not part of Daily Policy ${policyVersion.value}."
    }
    return WebDailyPuzzleOrder.bit(puzzleType)
}

private fun browserLocalDateCode(): Int =
    js(
        "(() => { const date = new Date(); " +
            "return date.getFullYear() * 10000 + (date.getMonth() + 1) * 100 + date.getDate(); })()",
    )

private fun dailyLocalStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun dailyLocalStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}
