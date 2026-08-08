package com.stanisryz.logica.session

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal class RoomGameSessionRepository(
    private val dao: GameSessionDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : GameSessionRepository {
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launchWriter()
    }

    override suspend fun readActiveSession(puzzleType: PuzzleType): SavedGameSession? {
        val reply = CompletableDeferred<Result<SavedGameSession?>>()
        commands.send(Command.Read(puzzleType, reply))
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
        sessionId: String,
    ) {
        check(commands.trySend(Command.Delete(puzzleType, sessionId)).isSuccess) { "Session writer is unavailable." }
    }

    override fun observeHasActiveSession(puzzleType: PuzzleType): Flow<Boolean> = dao.observeExists(puzzleType.name)

    private fun CoroutineScope.launchWriter() =
        launch {
            for (command in commands) {
                when (command) {
                    is Command.Read -> read(command)
                    is Command.Replace -> runCatching { replace(command.session) }
                    is Command.Update -> runCatching { update(command.session) }
                    is Command.Delete ->
                        runCatching {
                            dao.deleteIfCurrent(command.puzzleType.name, command.sessionId)
                        }
                }
            }
        }

    private suspend fun read(command: Command.Read) {
        command.reply.complete(
            runCatching {
                val entity = dao.find(command.puzzleType.name) ?: return@runCatching null
                entity.toSavedGameSessionOrNull()
                    ?: run {
                        dao.delete(command.puzzleType.name)
                        null
                    }
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
            sessionId = sessionId,
            difficulty = difficulty.name,
            puzzleSeed = puzzleSeed.value,
            generatorVersion = generatorVersion.value,
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
                difficulty = Difficulty.valueOf(difficulty),
                puzzleSeed = PuzzleSeed(puzzleSeed),
                generatorVersion = GeneratorVersion(generatorVersion),
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
            val sessionId: String,
        ) : Command
    }
}
