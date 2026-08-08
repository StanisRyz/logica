package com.stanisryz.logica.session

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionDao
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.toEntity
import com.stanisryz.logica.result.toGameResultOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate

internal class RoomGameSessionRepository(
    private val dao: GameSessionDao,
    private val completionDao: GameCompletionDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : GameSessionRepository,
    GameCompletionRepository {
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launchWriter()
    }

    override suspend fun readActiveSession(
        puzzleType: PuzzleType,
        sessionScope: GameSessionScope,
    ): SavedGameSession? {
        val reply = CompletableDeferred<Result<SavedGameSession?>>()
        commands.send(Command.Read(puzzleType, sessionScope, reply))
        return reply.await().getOrThrow()
    }

    override fun replaceActiveSession(session: SavedGameSession) {
        check(commands.trySend(Command.Replace(session)).isSuccess) { "Session writer is unavailable." }
    }

    override fun updateActiveSession(session: SavedGameSession) {
        check(commands.trySend(Command.Update(session)).isSuccess) { "Session writer is unavailable." }
    }

    override fun deleteActiveSession(
        puzzleType: PuzzleType,
        sessionScope: GameSessionScope,
        sessionId: String,
    ) {
        check(commands.trySend(Command.Delete(puzzleType, sessionScope, sessionId)).isSuccess) {
            "Session writer is unavailable."
        }
    }

    override fun observeHasActiveSession(
        puzzleType: PuzzleType,
        sessionScope: GameSessionScope,
    ): Flow<Boolean> = dao.observeExists(puzzleType.name, sessionScope.name)

    override suspend fun complete(completion: GameCompletion): GameResult {
        val reply = CompletableDeferred<Result<GameResult>>()
        commands.send(Command.Complete(completion, reply))
        return reply.await().getOrThrow()
    }

    private fun CoroutineScope.launchWriter() =
        launch {
            for (command in commands) {
                when (command) {
                    is Command.Read -> read(command)
                    is Command.Replace -> runCatching { replace(command.session) }
                    is Command.Update -> runCatching { update(command.session) }
                    is Command.Delete ->
                        runCatching {
                            dao.deleteIfCurrent(
                                command.puzzleType.name,
                                command.sessionScope.name,
                                command.sessionId,
                            )
                        }
                    is Command.Complete -> complete(command)
                }
            }
        }

    private suspend fun read(command: Command.Read) {
        command.reply.complete(
            runCatching {
                val entity = dao.find(command.puzzleType.name, command.sessionScope.name) ?: return@runCatching null
                entity.toSavedGameSessionOrNull()
                    ?: run {
                        dao.delete(command.puzzleType.name, command.sessionScope.name)
                        null
                    }
            },
        )
    }

    private suspend fun complete(command: Command.Complete) {
        command.reply.complete(
            runCatching {
                completionDao
                    .complete(command.completion.toEntity(currentTimeMillis()))
                    .toGameResultOrNull()
                    ?: error("The stored completion result is invalid.")
            },
        )
    }

    private suspend fun replace(session: SavedGameSession) {
        val now = currentTimeMillis()
        dao.upsert(session.toEntity(createdAtEpochMillis = now, updatedAtEpochMillis = now))
    }

    private suspend fun update(session: SavedGameSession) {
        val now = currentTimeMillis()
        dao.updateIfCurrent(
            entity = session.toEntity(createdAtEpochMillis = now, updatedAtEpochMillis = now),
            updatedAtEpochMillis = now,
        )
    }

    private fun SavedGameSession.toEntity(
        createdAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ): GameSessionEntity =
        GameSessionEntity(
            puzzleType = puzzleType.name,
            sessionScope = sessionScope.name,
            sessionId = sessionId,
            difficulty = difficulty.name,
            puzzleSeed = puzzleSeed.value,
            generatorVersion = generatorVersion.value,
            challengeDate = dailyIdentity?.challengeDate?.toString(),
            dailyPolicyVersion = dailyIdentity?.policyVersion,
            sessionFormatVersion = sessionFormatVersion,
            gameplayPayload = gameplayPayload,
            moveHistoryPayload = moveHistoryPayload,
            hintsUsed = hintsUsed,
            status = status,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )

    private fun GameSessionEntity.toSavedGameSessionOrNull(): SavedGameSession? =
        runCatching {
            SavedGameSession(
                sessionId = sessionId.also { require(it.isNotBlank()) },
                puzzleType = PuzzleType.valueOf(puzzleType),
                sessionScope = GameSessionScope.valueOf(sessionScope),
                difficulty = Difficulty.valueOf(difficulty),
                puzzleSeed = PuzzleSeed(puzzleSeed),
                generatorVersion = GeneratorVersion(generatorVersion),
                dailyIdentity =
                    challengeDate
                        ?.let { date ->
                            DailyGameSessionIdentity(
                                challengeDate = LocalDate.parse(date),
                                policyVersion = requireNotNull(dailyPolicyVersion),
                            )
                        }.also { identity ->
                            require((sessionScope == GameSessionScope.DAILY.name) == (identity != null))
                        },
                sessionFormatVersion = sessionFormatVersion.also { require(it > 0) },
                gameplayPayload = gameplayPayload,
                moveHistoryPayload = moveHistoryPayload,
                hintsUsed = hintsUsed.also { require(it >= 0) },
                status = status,
            )
        }.getOrNull()

    private sealed interface Command {
        data class Read(
            val puzzleType: PuzzleType,
            val sessionScope: GameSessionScope,
            val reply: CompletableDeferred<Result<SavedGameSession?>>,
        ) : Command

        data class Replace(
            val session: SavedGameSession,
        ) : Command

        data class Update(
            val session: SavedGameSession,
        ) : Command

        data class Delete(
            val puzzleType: PuzzleType,
            val sessionScope: GameSessionScope,
            val sessionId: String,
        ) : Command

        data class Complete(
            val completion: GameCompletion,
            val reply: CompletableDeferred<Result<GameResult>>,
        ) : Command
    }
}
