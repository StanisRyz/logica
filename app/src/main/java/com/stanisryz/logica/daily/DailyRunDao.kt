package com.stanisryz.logica.daily

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DailyRunDao {
    @Query("SELECT challenge_date FROM daily_runs WHERE status = 'COMPLETED'")
    fun observeCompletedDates(): Flow<List<String>>

    @Query("SELECT * FROM daily_runs WHERE challenge_date = :challengeDate LIMIT 1")
    suspend fun find(challengeDate: String): DailyRunEntity?

    @Query("SELECT * FROM daily_challenges WHERE challenge_date = :challengeDate ORDER BY puzzle_type")
    suspend fun findEntries(challengeDate: String): List<DailyChallengeEntity>

    @Insert
    suspend fun insertRun(entity: DailyRunEntity)

    @Insert
    suspend fun insertEntries(entities: List<DailyChallengeEntity>)

    @Transaction
    suspend fun createWithEntries(
        run: DailyRunEntity,
        entries: List<DailyChallengeEntity>,
    ): DailyRunEntity {
        val existing = find(run.challengeDate)
        if (existing != null) {
            require(existing.dailyPolicyVersion == run.dailyPolicyVersion) {
                "The Daily date already belongs to another policy version."
            }
            require(findEntries(run.challengeDate).identities() == entries.identities()) {
                "The persisted Daily entry set does not match its policy definition."
            }
            return existing
        }
        insertRun(run)
        insertEntries(entries)
        return run
    }

    private fun List<DailyChallengeEntity>.identities(): Set<List<Any>> =
        mapTo(mutableSetOf()) { entry ->
            listOf(
                entry.challengeDate,
                entry.puzzleType,
                entry.dailyPolicyVersion,
                entry.difficulty,
                entry.puzzleSeed,
                entry.generatorVersion,
            )
        }
}
