@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.quest.LoginGift
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

    fun currentLevel(bucket: WebCatalogProgressBucket): CatalogLevelNumber = levels[bucket] ?: CatalogLevelPacks.FIRST_LEVEL

    /**
     * The bucket's level as gameplay sees it. The Nonogram's Level Pack V2 bucket continues the V1
     * numbering, so it stands at least at the V1 bucket — a V2 bucket that was never written starts
     * where V1 stopped, and an older game version advancing V1 on another device is never rewound.
     */
    fun playableLevel(bucket: WebCatalogProgressBucket): CatalogLevelNumber {
        val own = currentLevel(bucket)
        if (!CatalogLevelPacks.continuesFromV1(bucket.puzzleType, bucket.packVersion)) return own
        val v1 = currentLevel(bucket.copy(packVersion = CatalogLevelPackVersion.V1))
        return if (v1.value > own.value) v1 else own
    }

    /** A game's current level: its active pack's bucket ([playableLevel]). */
    fun gameLevel(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): CatalogLevelNumber = playableLevel(WebCatalogProgressBucket(puzzleType, difficulty, CatalogLevelPacks.activePackVersion(puzzleType)))

    /** The bucket a cleared or current [level] belongs to: below the V1 bucket's level it is a V1 level. */
    fun bucketForLevel(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        level: Int,
    ): WebCatalogProgressBucket {
        val v1 = currentLevel(WebCatalogProgressBucket(puzzleType, difficulty, CatalogLevelPackVersion.V1))
        return WebCatalogProgressBucket(
            puzzleType,
            difficulty,
            CatalogLevelPacks.packVersionForLevel(puzzleType, CatalogLevelNumber(level), v1),
        )
    }

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
            PuzzleType.NONOGRAM -> 6
            PuzzleType.BLOCK_SUDOKU -> 7
            else -> error("$puzzleType has no Web Catalog progress code.")
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

/** Outcome of merging one cloud value kept beside Catalog progress (stars, best 2048, rewards). */
internal sealed interface WebCloudValueMergeResult {
    /** Merged durably; [cloudLacksLocal] is true when the cloud copy misses something local. */
    data class Merged(
        val cloudLacksLocal: Boolean,
    ) : WebCloudValueMergeResult

    /** The merged value could not be saved locally, so the local copy still lacks the cloud's. */
    data class PersistenceFailed(
        val cause: Throwable,
    ) : WebCloudValueMergeResult
}

/** Authoritative Web-local Catalog levels and deterministic monotonic cloud merge. */
internal class WebCatalogProgressRepository(
    val scope: WebCatalogProgressScope,
    private val localStore: WebCatalogProgressStore,
    private val starsStore: WebCatalogStarsStore = WebCatalogStarsStore.InMemory(),
    private val bestScoreStore: WebBestScoreStore = WebBestScoreStore.InMemory(),
    private val rewardsStore: WebDailyRewardsStore = WebDailyRewardsStore.InMemory(),
) {
    private val mutableSnapshot = MutableStateFlow(WebCatalogProgressSnapshot.EMPTY)
    val snapshot: StateFlow<WebCatalogProgressSnapshot> = mutableSnapshot.asStateFlow()

    private val mutableStars = MutableStateFlow(WebCatalogStarsSnapshot.EMPTY)

    /** Best stars per level in this Player scope; lives beside progress and never gates it. */
    val stars: StateFlow<WebCatalogStarsSnapshot> = mutableStars.asStateFlow()

    private val mutableBest2048 = MutableStateFlow(0L)

    /** The best 2048 score in this Player scope, as last published at the end of a game. */
    val best2048: StateFlow<Long> = mutableBest2048.asStateFlow()

    /** Kept locally move by move; published (and so synced) only when a game ends or is left. */
    private var unpublishedBest2048 = 0L

    private val mutableRewards = MutableStateFlow(WebDailyRewardsSnapshot.EMPTY)

    /** Today's quest counters and claims plus the login gift, in this Player scope. */
    val rewards: StateFlow<WebDailyRewardsSnapshot> = mutableRewards.asStateFlow()

    /** Invoked after every successful durable local mutation; never after a cloud merge. */
    var onDurableChange: (() -> Unit)? = null

    fun loadLocal(): WebCatalogProgressSnapshot {
        mutableStars.value = runCatching { starsStore.load() }.getOrDefault(WebCatalogStarsSnapshot.EMPTY)
        mutableBest2048.value = runCatching { bestScoreStore.load() }.getOrDefault(0L)
        unpublishedBest2048 = mutableBest2048.value
        mutableRewards.value = runCatching { rewardsStore.load() }.getOrDefault(WebDailyRewardsSnapshot.EMPTY)
        return localStore.load().also { mutableSnapshot.value = it }
    }

    /**
     * The level's best stars from earlier solves, read before this solve records anything: `0` when
     * it was cleared without stars on record, `null` when it was never cleared.
     */
    fun previousBestStars(levelId: CatalogLevelId): Int? {
        val bucket = levelId.toProgressBucket()
        if (levelId.levelNumber.value >= currentLevel(bucket).value) return null
        return mutableStars.value.starsOf(bucket, levelId.levelNumber.value)
    }

    /**
     * Keeps [stars] for [levelId] when they beat the level's best. Stars are best-effort: a
     * failed browser write leaves them unchanged and never affects the level's completion.
     */
    fun recordStars(
        levelId: CatalogLevelId,
        stars: Int,
    ): Boolean {
        val updated = mutableStars.value.withBest(levelId.toProgressBucket(), levelId.levelNumber.value, stars)
        if (updated == mutableStars.value) return false
        if (runCatching { starsStore.save(updated) }.isFailure) return false
        mutableStars.value = updated
        onDurableChange?.invoke()
        return true
    }

    /** Per-level maximum with the cloud copy. */
    fun mergeCloudStars(cloud: WebCatalogStarsSnapshot): WebCloudValueMergeResult {
        val local = mutableStars.value
        val merged = local.mergedWith(cloud)
        if (merged != local) {
            runCatching { starsStore.save(merged) }
                .exceptionOrNull()
                ?.let { return WebCloudValueMergeResult.PersistenceFailed(it) }
            mutableStars.value = merged
        }
        return WebCloudValueMergeResult.Merged(cloudLacksLocal = merged != cloud)
    }

    /** Keeps [score] locally when it beats the best 2048 score; cheap enough to call on every move. */
    fun recordBest2048(score: Long) {
        if (score <= unpublishedBest2048) return
        if (runCatching { bestScoreStore.save(score) }.isSuccess) unpublishedBest2048 = score
    }

    /** Publishes a best score recorded since the last call, so it reaches the rating and the cloud. */
    fun publishBest2048() {
        if (unpublishedBest2048 <= mutableBest2048.value) return
        mutableBest2048.value = unpublishedBest2048
        onDurableChange?.invoke()
    }

    /** Maximum with the cloud copy. */
    fun mergeCloudBest2048(cloud: Long): WebCloudValueMergeResult {
        val local = maxOf(mutableBest2048.value, unpublishedBest2048)
        if (cloud > local) {
            runCatching { bestScoreStore.save(cloud) }
                .exceptionOrNull()
                ?.let { return WebCloudValueMergeResult.PersistenceFailed(it) }
            unpublishedBest2048 = cloud
            mutableBest2048.value = cloud
        }
        return WebCloudValueMergeResult.Merged(cloudLacksLocal = local > cloud)
    }

    /** Counts one recorded terminal attempt toward the quests of the local day [today]. */
    fun recordQuestActivity(
        today: Long,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        solved: Boolean,
    ) {
        saveRewards(mutableRewards.value.plus(today, puzzleType, difficulty, solved))
    }

    /**
     * Marks quest [index] of [today] claimed; true only for the first claim, and only once the claim
     * is durable, so the caller pays exactly once. A lost browser write pays nothing.
     */
    fun claimQuest(
        today: Long,
        index: Int,
    ): Boolean {
        if (!mutableRewards.value.acceptsDay(today)) return false // a clock moved back claims nothing
        val current = mutableRewards.value.on(today)
        val bit = 1 shl index
        if (current.claimedQuests and bit != 0) return false
        return saveRewards(current.copy(claimedQuests = current.claimedQuests or bit))
    }

    /**
     * Claims [today]'s login gift once; returns its gems, or null when it was already claimed. The
     * gift day only moves forward, so switching the clock between two days never pays twice.
     */
    fun claimLoginGift(today: Long): Int? {
        val current = mutableRewards.value
        if (today <= current.lastGiftEpochDay) return null
        val day = LoginGift.streakDay(current.lastGiftDayOrNull, current.giftStreakDay, today)
        return if (saveRewards(current.copy(lastGiftEpochDay = today, giftStreakDay = day))) LoginGift.gemsFor(day) else null
    }

    /** Marks [achievementId]'s reward paid; true only for the first, durable claim. */
    fun claimAchievement(achievementId: String): Boolean {
        val current = mutableRewards.value
        if (achievementId in current.claimedAchievements) return false
        return saveRewards(current.copy(claimedAchievements = current.claimedAchievements + achievementId))
    }

    /**
     * Saves the Daily streak day [epochDay]; true only for the first, durable save. The caller pays
     * only after this, so a lost browser write charges nothing, and a repeat charges nothing again.
     */
    fun claimStreakRestore(epochDay: Long): Boolean {
        val current = mutableRewards.value
        if (epochDay in current.restoredStreakDays) return false
        return saveRewards(current.copy(restoredStreakDays = current.restoredStreakDays + epochDay))
    }

    /** Day-aware merge with the cloud copy. */
    fun mergeCloudRewards(cloud: WebDailyRewardsSnapshot): WebCloudValueMergeResult {
        val local = mutableRewards.value
        val merged = local.mergedWith(cloud)
        if (merged != local) {
            runCatching { rewardsStore.save(merged) }
                .exceptionOrNull()
                ?.let { return WebCloudValueMergeResult.PersistenceFailed(it) }
            mutableRewards.value = merged
        }
        return WebCloudValueMergeResult.Merged(cloudLacksLocal = merged != cloud)
    }

    private fun saveRewards(updated: WebDailyRewardsSnapshot): Boolean {
        if (updated == mutableRewards.value) return true
        if (runCatching { rewardsStore.save(updated) }.isFailure) return false
        mutableRewards.value = updated
        onDurableChange?.invoke()
        return true
    }

    fun currentLevel(bucket: WebCatalogProgressBucket): CatalogLevelNumber = mutableSnapshot.value.playableLevel(bucket)

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
                onDurableChange?.invoke()
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

    private fun persist(snapshot: WebCatalogProgressSnapshot): Throwable? = runCatching { localStore.save(snapshot) }.exceptionOrNull()
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
