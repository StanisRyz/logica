package com.stanisryz.logica.statistics

import com.stanisryz.logica.daily.DailyChallengeDao
import com.stanisryz.logica.result.GameResultDao
import com.stanisryz.logica.result.GameResultEntity
import com.stanisryz.logica.result.toGameResultOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

internal interface StatisticsRepository {
    fun observe(currentDate: LocalDate): Flow<StatisticsSnapshot>
}

internal class RoomStatisticsRepository(
    private val gameResultDao: GameResultDao,
    private val dailyChallengeDao: DailyChallengeDao,
) : StatisticsRepository {
    override fun observe(currentDate: LocalDate): Flow<StatisticsSnapshot> =
        combine(
            gameResultDao.observeAll().map { entities -> entities.mapNotNull(GameResultEntity::toGameResultOrNull) },
            dailyChallengeDao.observeCompletedDates().map { dates -> dates.mapNotNull(::parseDateOrNull) },
        ) { results, completedDailyDates ->
            StatisticsAggregator.aggregate(currentDate, results, completedDailyDates)
        }

    private fun parseDateOrNull(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
}
