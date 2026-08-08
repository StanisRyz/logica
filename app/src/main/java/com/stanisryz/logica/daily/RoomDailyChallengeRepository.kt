package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.LocalDate

internal class RoomDailyChallengeRepository(
    private val dao: DailyChallengeDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : DailyChallengeRepository {
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is Command.Read ->
                        command.reply.complete(
                            runCatching {
                                dao
                                    .find(command.challengeDate.toString(), command.puzzleType.name)
                                    ?.toSavedDailyChallengeOrNull()
                            },
                        )
                    is Command.Save -> {
                        val result = runCatching { saveNow(command.challenge) }
                        command.reply.complete(result)
                    }
                }
            }
        }
    }

    override suspend fun read(
        challengeDate: LocalDate,
        puzzleType: PuzzleType,
    ): SavedDailyChallenge? {
        val reply = CompletableDeferred<Result<SavedDailyChallenge?>>()
        commands.send(Command.Read(challengeDate, puzzleType, reply))
        return reply.await().getOrThrow()
    }

    override suspend fun save(challenge: SavedDailyChallenge) {
        val reply = CompletableDeferred<Result<Unit>>()
        commands.send(Command.Save(challenge, reply))
        reply.await().getOrThrow()
    }

    private suspend fun saveNow(challenge: SavedDailyChallenge) {
        val now = currentTimeMillis()
        dao.upsertKeepingCreated(challenge.toEntity(now))
    }

    private fun SavedDailyChallenge.toEntity(now: Long): DailyChallengeEntity =
        DailyChallengeEntity(
            challengeDate = challengeDate.toString(),
            puzzleType = puzzleType.name,
            dailyPolicyVersion = policyVersion.value,
            difficulty = difficulty.name,
            puzzleSeed = seed.value,
            generatorVersion = generatorVersion.value,
            status = status.name,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )

    private fun DailyChallengeEntity.toSavedDailyChallengeOrNull(): SavedDailyChallenge? =
        runCatching {
            SavedDailyChallenge(
                challengeDate = LocalDate.parse(challengeDate),
                puzzleType = PuzzleType.valueOf(puzzleType),
                policyVersion = DailyPolicyVersion(dailyPolicyVersion),
                difficulty = Difficulty.valueOf(difficulty),
                seed = PuzzleSeed(puzzleSeed),
                generatorVersion = GeneratorVersion(generatorVersion),
                status = DailyChallengeStatus.valueOf(status),
            )
        }.getOrNull()

    private sealed interface Command {
        data class Read(
            val challengeDate: LocalDate,
            val puzzleType: PuzzleType,
            val reply: CompletableDeferred<Result<SavedDailyChallenge?>>,
        ) : Command

        data class Save(
            val challenge: SavedDailyChallenge,
            val reply: CompletableDeferred<Result<Unit>>,
        ) : Command
    }
}
