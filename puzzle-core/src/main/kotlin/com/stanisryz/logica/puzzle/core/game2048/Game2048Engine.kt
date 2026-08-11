package com.stanisryz.logica.puzzle.core.game2048

/**
 * Frozen deterministic 2048 V1 engine. A spawn is a pure function of puzzle seed, spawn index,
 * and the row-major empty cells on the current board; it never depends on runtime randomness.
 */
class Game2048Engine(
    val puzzleId: Game2048PuzzleId,
) {
    init {
        require(puzzleId.generatorVersion == Game2048GeneratorVersion.V1) {
            "Unsupported 2048 generator version: ${puzzleId.generatorVersion.value}."
        }
    }

    fun start(): Game2048State {
        var board = List(Game2048State.CELL_COUNT) { 0 }
        board = spawn(board, 0L)
        board = spawn(board, 1L)
        return Game2048State(
            puzzleId = puzzleId,
            board = board,
            score = 0L,
            nextSpawnIndex = INITIAL_SPAWN_COUNT,
            status = Game2048Status.IN_PROGRESS,
        )
    }

    fun move(
        state: Game2048State,
        direction: Game2048Direction,
    ): Game2048State {
        require(state.puzzleId == puzzleId) { "2048 state belongs to another puzzle." }
        if (state.status.isTerminal) return state
        val movement = Game2048Rules.move(state.board, direction)
        if (movement.board == state.board) return state

        val updatedScore = state.score + movement.scoreGained
        if (movement.board.any { it >= puzzleId.targetTile }) {
            return state.copy(
                board = movement.board,
                score = updatedScore,
                status = Game2048Status.SOLVED,
            )
        }

        val spawned = spawn(movement.board, state.nextSpawnIndex)
        return state.copy(
            board = spawned,
            score = updatedScore,
            nextSpawnIndex = state.nextSpawnIndex + 1L,
            status = Game2048Rules.status(spawned, puzzleId.targetTile),
        )
    }

    fun retry(state: Game2048State): Game2048State {
        require(state.puzzleId == puzzleId) { "2048 state belongs to another puzzle." }
        require(state.status.isTerminal) { "Only a finished 2048 attempt can be retried." }
        return start()
    }

    fun restore(
        board: List<Int>,
        score: Long,
        nextSpawnIndex: Long,
    ): Game2048State =
        Game2048State(
            puzzleId = puzzleId,
            board = board.toList(),
            score = score,
            nextSpawnIndex = nextSpawnIndex,
            status = Game2048Rules.status(board, puzzleId.targetTile),
        )

    private fun spawn(
        board: List<Int>,
        spawnIndex: Long,
    ): List<Int> {
        require(spawnIndex >= 0L)
        val emptyCells = board.indices.filter { board[it] == 0 }
        require(emptyCells.isNotEmpty()) { "Cannot spawn a 2048 tile on a full board." }
        val samples = Game2048SpawnV1.samples(puzzleId.seed.value, spawnIndex)
        val selectedCell = emptyCells[(samples.positionSample % emptyCells.size.toULong()).toInt()]
        return board.toMutableList().also { it[selectedCell] = samples.tileValue }
    }

    private companion object {
        const val INITIAL_SPAWN_COUNT = 2L
    }
}

/**
 * V1 uses SplitMix64's published finalizer over unsigned 64-bit arithmetic. For spawn n, the base
 * input is seed + GOLDEN_GAMMA * (n + 1). Position uses mix(base), tile value uses
 * mix(base + SAMPLE_GAMMA). Samples are reduced unsigned: position modulo empty count and tile
 * sample modulo 10, where zero means 4 and all other values mean 2.
 */
internal object Game2048SpawnV1 {
    fun samples(
        seed: Long,
        spawnIndex: Long,
    ): SpawnSamples {
        require(spawnIndex >= 0L)
        val base = seed.toULong() + GOLDEN_GAMMA * (spawnIndex.toULong() + 1uL)
        val positionSample = mix64(base)
        val valueSample = mix64(base + SAMPLE_GAMMA)
        return SpawnSamples(
            positionSample = positionSample,
            tileValue = if (valueSample % 10uL == 0uL) 4 else 2,
        )
    }

    private fun mix64(input: ULong): ULong {
        var value = input
        value = (value xor (value shr 30)) * 0xbf58476d1ce4e5b9uL
        value = (value xor (value shr 27)) * 0x94d049bb133111ebuL
        return value xor (value shr 31)
    }

    data class SpawnSamples(
        val positionSample: ULong,
        val tileValue: Int,
    )

    private const val GOLDEN_GAMMA = 0x9e3779b97f4a7c15uL
    private const val SAMPLE_GAMMA = 0xd1b54a32d192ed03uL
}
