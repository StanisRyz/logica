package com.stanisryz.logica.statistics

import com.stanisryz.logica.daily.DailyRunDao
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV6
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResultDao
import com.stanisryz.logica.result.GameResultEntity
import com.stanisryz.logica.result.GameResultScope
import com.stanisryz.logica.result.toGameResultOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

internal interface StatisticsRepository {
    fun observe(currentDate: LocalDate): Flow<StatisticsSnapshot>

    /** Every solved Daily Nonogram real picture (Generator V2) as its date and seed, newest first. */
    fun observeSolvedDailyPictures(): Flow<List<Pair<LocalDate, Long>>> = flowOf(emptyList())
}

internal class RoomStatisticsRepository(
    private val gameResultDao: GameResultDao,
    private val dailyRunDao: DailyRunDao,
    /** Saved streak days from the economy ledger (`streak_restore:<day>`). */
    private val restoredStreakDays: Flow<Set<Long>> = flowOf(emptySet()),
    /** Opened Daily archive days from the economy ledger (`daily_archive:<day>`) with when each was opened. */
    private val archiveUnlocks: Flow<Map<Long, Long>> = flowOf(emptyMap()),
) : StatisticsRepository {
    override fun observe(currentDate: LocalDate): Flow<StatisticsSnapshot> =
        combine(
            gameResultDao.observeAll().map { entities -> entities.mapNotNull(GameResultEntity::toGameResultOrNull) },
            dailyRunDao.observeCompletedDates().map { dates -> dates.mapNotNull(::parseDateOrNull) },
            restoredStreakDays,
            archiveUnlocks.map { unlocks ->
                unlocks.entries.associate { (day, openedAt) -> LocalDate.ofEpochDay(day) to Instant.ofEpochMilli(openedAt) }
            },
        ) { results, completedDailyDates, restored, archive ->
            StatisticsAggregator.aggregate(currentDate, results, completedDailyDates, restored, archive)
        }

    override fun observeSolvedDailyPictures(): Flow<List<Pair<LocalDate, Long>>> =
        gameResultDao.observeAll().map { entities ->
            entities
                .filter {
                    it.puzzleType == PuzzleType.NONOGRAM.name &&
                        it.resultScope == GameResultScope.DAILY.name &&
                        it.outcome == GameOutcome.SOLVED.name &&
                        it.generatorVersion == DailyChallengePolicyV6.NONOGRAM_GENERATOR_VERSION.value
                }.mapNotNull { entity -> entity.challengeDate?.let(::parseDateOrNull)?.let { it to entity.puzzleSeed } }
                .distinctBy { it.first }
                .sortedByDescending { it.first }
        }

    private fun parseDateOrNull(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
}
