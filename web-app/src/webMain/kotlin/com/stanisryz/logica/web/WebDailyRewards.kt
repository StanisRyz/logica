@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.quest.DailyQuestActivity
import com.stanisryz.logica.puzzle.core.quest.LoginGift
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The bound Player's daily rewards: today's quest counters ([epochDay] is the local calendar day
 * they belong to), which of today's quests were claimed ([claimedQuests] as a bit mask), and the
 * last login gift claim. Only terminal attempts count; a new day starts the counters and claims
 * over while the gift keeps its cycle. Daily solves are not kept here: the hub reads them from
 * the durable Daily history.
 */
internal data class WebDailyRewardsSnapshot(
    val epochDay: Long = NO_DAY,
    val played: Int = 0,
    val solvedByType: Map<PuzzleType, Int> = emptyMap(),
    val solvedHard: Int = 0,
    val claimedQuests: Int = 0,
    val lastGiftEpochDay: Long = NO_DAY,
    val giftStreakDay: Int = 0,
) {
    fun activity(
        today: Long,
        dailySolved: Int,
    ): DailyQuestActivity =
        if (epochDay == today) {
            DailyQuestActivity(played, solvedByType, solvedHard, dailySolved)
        } else {
            DailyQuestActivity(dailySolved = dailySolved)
        }

    fun claimedQuests(today: Long): Set<Int> =
        if (epochDay == today) (0 until Int.SIZE_BITS).filter { claimedQuests and (1 shl it) != 0 }.toSet() else emptySet()

    val lastGiftDayOrNull: Long?
        get() = lastGiftEpochDay.takeIf { it != NO_DAY }

    /** The same snapshot with its quest part rolled over to [today]. */
    fun on(today: Long): WebDailyRewardsSnapshot =
        if (epochDay == today) this else copy(epochDay = today, played = 0, solvedByType = emptyMap(), solvedHard = 0, claimedQuests = 0)

    fun plus(
        today: Long,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        solved: Boolean,
    ): WebDailyRewardsSnapshot {
        val current = on(today)
        val activity =
            DailyQuestActivity(current.played, current.solvedByType, current.solvedHard)
                .plus(puzzleType, difficulty, solved, daily = false)
        return current.copy(played = activity.played, solvedByType = activity.solvedByType, solvedHard = activity.solvedHard)
    }

    /**
     * Local and cloud copies: the later day's quest part wins, the same day keeps each counter's
     * maximum and every claim; the later gift claim wins, the same day the longer cycle.
     */
    fun mergedWith(other: WebDailyRewardsSnapshot): WebDailyRewardsSnapshot {
        val quests =
            when {
                epochDay > other.epochDay -> this
                epochDay < other.epochDay -> other
                else ->
                    copy(
                        played = maxOf(played, other.played),
                        solvedByType =
                            (solvedByType.keys + other.solvedByType.keys).associateWith {
                                maxOf(solvedByType[it] ?: 0, other.solvedByType[it] ?: 0)
                            },
                        solvedHard = maxOf(solvedHard, other.solvedHard),
                        claimedQuests = claimedQuests or other.claimedQuests,
                    )
            }
        val gift =
            when {
                lastGiftEpochDay > other.lastGiftEpochDay -> this
                lastGiftEpochDay < other.lastGiftEpochDay -> other
                giftStreakDay >= other.giftStreakDay -> this
                else -> other
            }
        return quests.copy(lastGiftEpochDay = gift.lastGiftEpochDay, giftStreakDay = gift.giftStreakDay)
    }

    companion object {
        const val NO_DAY = Long.MIN_VALUE
        val EMPTY = WebDailyRewardsSnapshot()
    }
}

/** A small text record: `LGDR1|day|played|hard|claimed|giftDay|giftStreak|TYPE=n,…`. */
internal object WebDailyRewardsCodec {
    private const val HEADER = "LGDR1"

    fun encode(snapshot: WebDailyRewardsSnapshot): ByteArray =
        listOf(
            HEADER,
            snapshot.epochDay.toString(),
            snapshot.played.toString(),
            snapshot.solvedHard.toString(),
            snapshot.claimedQuests.toString(),
            snapshot.lastGiftEpochDay.toString(),
            snapshot.giftStreakDay.toString(),
            snapshot.solvedByType.entries.joinToString(",") { "${it.key.name}=${it.value}" },
        ).joinToString("|").encodeToByteArray()

    fun decode(payload: ByteArray): WebDailyRewardsSnapshot? =
        runCatching {
            val parts = payload.decodeToString().split("|")
            require(parts.size == 8 && parts[0] == HEADER)
            val solvedByType =
                parts[7].takeIf { it.isNotEmpty() }?.split(",")?.associate { entry ->
                    val (name, count) = entry.split("=")
                    PuzzleType.valueOf(name) to count.toInt().also { require(it >= 0) }
                } ?: emptyMap()
            WebDailyRewardsSnapshot(
                epochDay = parts[1].toLong(),
                played = parts[2].toInt().also { require(it >= 0) },
                solvedHard = parts[3].toInt().also { require(it >= 0) },
                claimedQuests = parts[4].toInt(),
                lastGiftEpochDay = parts[5].toLong(),
                giftStreakDay = parts[6].toInt().also { require(it in 0..LoginGift.CYCLE_DAYS) },
                solvedByType = solvedByType,
            )
        }.getOrNull()
}

internal interface WebDailyRewardsStore {
    fun load(): WebDailyRewardsSnapshot

    fun save(snapshot: WebDailyRewardsSnapshot)

    /** Keeps the record in memory only; the default for tests and hosts without browser storage. */
    class InMemory(
        private var snapshot: WebDailyRewardsSnapshot = WebDailyRewardsSnapshot.EMPTY,
    ) : WebDailyRewardsStore {
        override fun load(): WebDailyRewardsSnapshot = snapshot

        override fun save(snapshot: WebDailyRewardsSnapshot) {
            this.snapshot = snapshot
        }
    }
}

/** Browser-local daily rewards beside Catalog progress; corrupt or missing data reads as none. */
internal class WebDailyRewardsLocalStore(
    scope: WebCatalogProgressScope,
) : WebDailyRewardsStore {
    private val storageKey = "logica_daily_rewards_v1:${scope.keySuffix}"

    override fun load(): WebDailyRewardsSnapshot =
        runCatching {
            rewardsStorageGet(storageKey)?.let(WebBase64::decode)?.let(WebDailyRewardsCodec::decode)
        }.getOrNull() ?: WebDailyRewardsSnapshot.EMPTY

    override fun save(snapshot: WebDailyRewardsSnapshot) {
        rewardsStorageSet(storageKey, WebBase64.encode(WebDailyRewardsCodec.encode(snapshot)))
    }
}

private fun rewardsStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun rewardsStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}

/**
 * Counts every recorded terminal attempt toward today's quests, beside the statistics it
 * decorates: quests count exactly what Statistics records, and a quest failure never touches it.
 */
internal class WebQuestCountingStatistics(
    private val delegate: WebGameplayStatistics,
    private val onRecorded: (PuzzleType, Difficulty, Boolean) -> Unit,
) : WebGameplayStatistics by delegate {
    override fun recordTerminalResult(
        attempt: WebStatisticsAttempt,
        outcome: WebStatisticsTerminalOutcome,
        hintsUsed: Int,
        wordAttemptsUsed: Int?,
    ): WebStatisticsAttemptRecordResult {
        val result = delegate.recordTerminalResult(attempt, outcome, hintsUsed, wordAttemptsUsed)
        if (result == WebStatisticsAttemptRecordResult.Recorded) {
            runCatching { onRecorded(attempt.puzzleType, attempt.difficulty, outcome == WebStatisticsTerminalOutcome.SOLVED) }
        }
        return result
    }
}
