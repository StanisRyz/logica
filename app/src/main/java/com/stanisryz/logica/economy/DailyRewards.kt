package com.stanisryz.logica.economy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.platform.AppLog
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.quest.DailyQuestActivity
import com.stanisryz.logica.puzzle.core.quest.DailyQuests
import com.stanisryz.logica.puzzle.core.quest.LoginGift
import com.stanisryz.logica.result.GameResultDao
import com.stanisryz.logica.result.GameResultEntity
import com.stanisryz.logica.result.GameResultScope
import com.stanisryz.logica.runCatchingCancellable
import com.stanisryz.logica.ui.components.DailyRewardsUiState
import com.stanisryz.logica.ui.components.dailyRewardsUiState
import com.stanisryz.logica.ui.profile.Achievement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Daily quests and the login gift over durable truth only: quest progress is derived from the
 * local day's `game_results`, and every claim is one `economy_events` row (`quest:<day>:<index>`,
 * `login_gift:<day>`, the gift's cycle day as its source), so a repeated claim pays nothing and
 * no new table or column exists for either. A device clock turned back behind [timeMark] pauses
 * every claim, and a gift is never paid for a day before the last one paid.
 */
internal class DailyRewardsRepository(
    private val resultDao: GameResultDao,
    private val economyDao: EconomyDao,
    private val clock: EconomyClock = EconomyClock.SYSTEM,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val timeMark: EconomyTimeMark = InMemoryEconomyTimeMark(),
) {
    fun today(): Long =
        Instant
            .ofEpochMilli(clock.nowEpochMillis())
            .atZone(zone())
            .toLocalDate()
            .toEpochDay()

    fun observe(epochDay: Long): Flow<DailyRewardsUiState> =
        combine(
            observeActivity(epochDay),
            economyDao.observeEventIds("quest:$epochDay:"),
            economyDao.observeLastLoginGift(),
        ) { activity, claimedIds, lastGift ->
            val gift = lastGift?.toGiftClaim()
            dailyRewardsUiState(
                epochDay = epochDay,
                activity = activity,
                claimedQuests = claimedIds.mapNotNull { it.substringAfterLast(':').toIntOrNull() }.toSet(),
                lastGiftEpochDay = gift?.first,
                lastGiftStreakDay = gift?.second ?: 0,
                claimsPaused = timeMark.clockTurnedBack(clock.nowEpochMillis()),
            )
        }

    /**
     * Claims [epochDay]'s login gift once; false when it was already claimed, when [epochDay] lies
     * before the last gift's day, or while the device clock is turned back.
     */
    suspend fun claimLoginGift(epochDay: Long): Boolean {
        val now = clock.nowEpochMillis()
        if (timeMark.clockTurnedBack(now)) return false
        val last = economyDao.findLastLoginGift()?.toGiftClaim()
        if (last != null && epochDay < last.first) return false
        val day = LoginGift.streakDay(last?.first, last?.second ?: 0, epochDay)
        return economyDao.grantDailyReward(
            eventId = EconomyEvent.loginGiftEventId(epochDay),
            type = EconomyEventType.LOGIN_GIFT,
            sourceId = day.toString(),
            gems = LoginGift.gemsFor(day),
            nowEpochMillis = now,
        )
    }

    /**
     * Pays quest [index] of [epochDay] once, and only when the durable results complete it and the
     * device clock is not turned back.
     */
    suspend fun claimQuest(
        epochDay: Long,
        index: Int,
    ): Boolean {
        val now = clock.nowEpochMillis()
        if (timeMark.clockTurnedBack(now)) return false
        val quest = DailyQuests.forDay(epochDay).firstOrNull { it.index == index } ?: return false
        if (!quest.isComplete(observeActivity(epochDay).first())) return false
        return economyDao.grantDailyReward(
            eventId = EconomyEvent.questEventId(epochDay, index),
            type = EconomyEventType.DAILY_QUEST_REWARD,
            sourceId = "$epochDay:$index",
            gems = quest.gems,
            nowEpochMillis = now,
        )
    }

    /** Ids of the achievements whose reward was already paid. */
    fun observeClaimedAchievements(): Flow<Set<String>> =
        economyDao.observeEventIds("achievement:").map { ids -> ids.mapTo(mutableSetOf()) { it.removePrefix("achievement:") } }

    /**
     * Pays [achievement]'s reward once; the caller has checked it against the durable statistics,
     * and the ledger row makes a repeat pay nothing.
     */
    suspend fun claimAchievement(achievement: Achievement): Boolean =
        economyDao.grantDailyReward(
            eventId = EconomyEvent.achievementEventId(achievement.id),
            type = EconomyEventType.ACHIEVEMENT_REWARD,
            sourceId = achievement.id,
            gems = achievement.gems,
            nowEpochMillis = clock.nowEpochMillis(),
        )

    private fun observeActivity(epochDay: Long): Flow<DailyQuestActivity> {
        val date = LocalDate.ofEpochDay(epochDay)
        val from = date.atStartOfDay(zone()).toInstant().toEpochMilli()
        val until =
            date
                .plusDays(1)
                .atStartOfDay(zone())
                .toInstant()
                .toEpochMilli()
        return resultDao.observeCompletedBetween(from, until).map { it.toActivity() }
    }

    private fun List<GameResultEntity>.toActivity(): DailyQuestActivity =
        fold(DailyQuestActivity()) { activity, result ->
            val puzzleType = runCatching { PuzzleType.valueOf(result.puzzleType) }.getOrNull() ?: return@fold activity
            val difficulty = runCatching { Difficulty.valueOf(result.difficulty) }.getOrNull() ?: return@fold activity
            activity.plus(
                puzzleType = puzzleType,
                difficulty = difficulty,
                solved = result.outcome == "SOLVED",
                daily = result.resultScope == GameResultScope.DAILY.name,
            )
        }

    /** The claimed day from the row's ID and the cycle day from its source. */
    private fun EconomyEventEntity.toGiftClaim(): Pair<Long, Int>? {
        val day = eventId.substringAfter("login_gift:").toLongOrNull() ?: return null
        return day to (sourceId?.toIntOrNull() ?: 1)
    }
}

/**
 * The hub's daily rewards. It follows the local day it is told about — the hub refreshes it on
 * resume and at midnight — and claims only through the repository's idempotent ledger rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DailyRewardsViewModel(
    private val repository: DailyRewardsRepository,
) : ViewModel() {
    /** The day, plus a count so every refresh also re-checks the device clock on the same day. */
    private data class Refresh(
        val epochDay: Long,
        val count: Int,
    )

    private val refreshes = MutableStateFlow(Refresh(repository.today(), 0))

    val uiState: StateFlow<DailyRewardsUiState?> =
        refreshes
            .flatMapLatest { repository.observe(it.epochDay) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun refresh() {
        refreshes.value = Refresh(repository.today(), refreshes.value.count + 1)
    }

    fun claimLoginGift() {
        val today = refreshes.value.epochDay
        viewModelScope.launch {
            runCatchingCancellable { repository.claimLoginGift(today) }.onFailure { AppLog.warn(TAG, "The login gift claim failed.", it) }
        }
    }

    fun claimQuest(index: Int) {
        val today = refreshes.value.epochDay
        viewModelScope.launch {
            runCatchingCancellable { repository.claimQuest(today, index) }.onFailure { AppLog.warn(TAG, "A quest claim failed.", it) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Achievement rewards for the Profile and the achievements screen. */
internal class AchievementRewardsViewModel(
    private val repository: DailyRewardsRepository,
) : ViewModel() {
    val claimed: StateFlow<Set<String>?> =
        repository
            .observeClaimedAchievements()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    fun claim(achievement: Achievement) {
        viewModelScope.launch {
            runCatchingCancellable { repository.claimAchievement(achievement) }
                .onFailure { AppLog.warn(TAG, "An achievement reward claim failed.", it) }
        }
    }
}

internal class AchievementRewardsViewModelFactory(
    private val repository: DailyRewardsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AchievementRewardsViewModel(repository) as T
}

internal class DailyRewardsViewModelFactory(
    private val repository: DailyRewardsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DailyRewardsViewModel(repository) as T
}

private const val TAG = "DailyRewards"
