package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.GameResultDao
import com.stanisryz.logica.result.toGameResultOrNull
import java.time.LocalDate

/** A focused read path for one Daily identity's terminal results, separate from all-time statistics. */
internal interface DailyResultRepository {
    suspend fun readResults(
        challengeDate: LocalDate,
        policyVersion: DailyPolicyVersion,
    ): List<GameResult>
}

internal class RoomDailyResultRepository(
    private val gameResultDao: GameResultDao,
) : DailyResultRepository {
    override suspend fun readResults(
        challengeDate: LocalDate,
        policyVersion: DailyPolicyVersion,
    ): List<GameResult> =
        gameResultDao
            .findDailyResults(challengeDate.toString(), policyVersion.value)
            .mapNotNull { it.toGameResultOrNull() }
}
