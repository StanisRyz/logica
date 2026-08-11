package com.stanisryz.logica.puzzle.core.game2048

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Game2048DeterminismTest {
    private val puzzleId = Game2048PuzzleId(PuzzleSeed(-8_204_800_036L), Difficulty.EXPERT)
    private val engine = Game2048Engine(puzzleId)

    @Test
    fun `same seed and valid moves reproduce every state while invalid moves are no ops`() {
        var first = engine.start()
        var second = engine.start()
        assertEquals(first, second)
        assertEquals(listOf(2, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), first.board)

        repeat(24) {
            val direction = Game2048Direction.entries.first { engine.move(first, it) != first }
            first = engine.move(first, direction)
            second = engine.move(second, direction)
            assertEquals(first, second)
        }

        val aligned =
            engine.restore(
                board = listOf(2, 0, 0, 0, 4, 0, 0, 0) + List(8) { 0 },
                score = 12L,
                nextSpawnIndex = 9L,
            )
        assertEquals(aligned, engine.move(aligned, Game2048Direction.LEFT))
    }

    @Test
    fun `codec round trip preserves the exact next spawn and rejects malformed durable state`() {
        var uninterrupted = engine.start()
        repeat(8) {
            val direction = Game2048Direction.entries.first { engine.move(uninterrupted, it) != uninterrupted }
            uninterrupted = engine.move(uninterrupted, direction)
        }
        val encoded = Game2048SessionCodecV1.encode(uninterrupted)
        val restored = Game2048SessionCodecV1.decode(encoded)
        val nextDirection = Game2048Direction.entries.first { engine.move(uninterrupted, it) != uninterrupted }

        assertEquals(puzzleId, Game2048SessionCodecV1.puzzleId(encoded))
        assertEquals(uninterrupted, restored)
        assertEquals(engine.move(uninterrupted, nextDirection), engine.move(restored, nextDirection))

        assertRejected(encoded.copy(payload = encoded.payload.replace("spawn=${restored.nextSpawnIndex}", "spawn=-1")))
        assertRejected(encoded.copy(payload = encoded.payload.replace("score=${restored.score}", "score=-1")))
        assertRejected(encoded.copy(payload = encoded.payload.replaceFirst(Regex("board=[^,]+"), "board=3")))
        assertRejected(encoded.copy(payload = encoded.payload.replace("|1\n", "|2\n")))
    }

    private fun assertRejected(encoded: EncodedGame2048Session) {
        assertThrows(RuntimeException::class.java) { Game2048SessionCodecV1.decode(encoded) }
    }
}
