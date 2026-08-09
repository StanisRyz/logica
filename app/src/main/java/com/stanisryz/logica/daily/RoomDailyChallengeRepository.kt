package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
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
import java.time.Instant
import java.time.LocalDate

internal class RoomDailyChallengeRepository(
    private val dao: DailyChallengeDao,
    private val runDao: DailyRunDao,
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
                    is Command.ReadRun ->
                        command.reply.complete(
                            runCatching {
                                runDao.find(command.challengeDate.toString())?.toSavedDailyRunOrNull()
                            },
                        )
                    is Command.CreateRun ->
                        command.reply.complete(runCatching { createRunNow(command.definition) })
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

    override suspend fun readRun(challengeDate: LocalDate): SavedDailyRun? {
        val reply = CompletableDeferred<Result<SavedDailyRun?>>()
        commands.send(Command.ReadRun(challengeDate, reply))
        return reply.await().getOrThrow()
    }

    override suspend fun createRun(definition: DailyChallengeDefinition): SavedDailyRun {
        val reply = CompletableDeferred<Result<SavedDailyRun>>()
        commands.send(Command.CreateRun(definition, reply))
        return reply.await().getOrThrow()
    }

    private suspend fun createRunNow(definition: DailyChallengeDefinition): SavedDailyRun {
        val now = currentTimeMillis()
        val run =
            DailyRunEntity(
                challengeDate = definition.challengeDate.toString(),
                dailyPolicyVersion = definition.policyVersion.value,
                status = DailyRunStatus.IN_PROGRESS.name,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                completedAtEpochMillis = null,
            )
        val entries =
            definition.entries.map { entry ->
                DailyChallengeEntity(
                    challengeDate = definition.challengeDate.toString(),
                    puzzleType = entry.puzzleType.name,
                    dailyPolicyVersion = definition.policyVersion.value,
                    difficulty = entry.difficulty.name,
                    puzzleSeed = entry.seed.value,
                    generatorVersion = entry.generatorVersion.value,
                    status = DailyChallengeStatus.IN_PROGRESS.name,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            }
        return checkNotNull(runDao.createWithEntries(run, entries).toSavedDailyRunOrNull()) {
            "The stored Daily run is invalid."
        }
    }

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

    private fun DailyRunEntity.toSavedDailyRunOrNull(): SavedDailyRun? =
        runCatching {
            SavedDailyRun(
                challengeDate = LocalDate.parse(challengeDate),
                policyVersion = DailyPolicyVersion(dailyPolicyVersion),
                status = DailyRunStatus.valueOf(status),
                createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
                updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
                completedAt = completedAtEpochMillis?.let(Instant::ofEpochMilli),
            )
        }.getOrNull()

    private sealed interface Command {
        data class Read(
            val challengeDate: LocalDate,
            val puzzleType: PuzzleType,
            val reply: CompletableDeferred<Result<SavedDailyChallenge?>>,
        ) : Command

        data class ReadRun(
            val challengeDate: LocalDate,
            val reply: CompletableDeferred<Result<SavedDailyRun?>>,
        ) : Command

        data class CreateRun(
            val definition: DailyChallengeDefinition,
            val reply: CompletableDeferred<Result<SavedDailyRun>>,
        ) : Command
    }
}
