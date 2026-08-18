package com.stanisryz.logica.puzzle.core.internal

/** Small deterministic SHA-256 used by the frozen Sudoku data contracts. */
internal object Sha256 {
    fun digest(input: ByteArray): ByteArray {
        val paddedLength = ((input.size + 9 + BLOCK_BYTES - 1) / BLOCK_BYTES) * BLOCK_BYTES
        val padded = ByteArray(paddedLength)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        val bitLength = input.size.toLong() * Byte.SIZE_BITS
        repeat(Long.SIZE_BYTES) { index ->
            padded[padded.lastIndex - index] = (bitLength ushr (index * Byte.SIZE_BITS)).toByte()
        }

        val state = INITIAL.copyOf()
        val words = LongArray(WORDS_PER_BLOCK)
        for (blockOffset in padded.indices step BLOCK_BYTES) {
            for (index in 0 until INPUT_WORDS) {
                val offset = blockOffset + index * Int.SIZE_BYTES
                words[index] =
                    ((padded[offset].toLong() and BYTE_MASK) shl 24) or
                    ((padded[offset + 1].toLong() and BYTE_MASK) shl 16) or
                    ((padded[offset + 2].toLong() and BYTE_MASK) shl 8) or
                    (padded[offset + 3].toLong() and BYTE_MASK)
            }
            for (index in INPUT_WORDS until WORDS_PER_BLOCK) {
                val first = words[index - 15]
                val second = words[index - 2]
                val sigma0 = rotateRight(first, 7) xor rotateRight(first, 18) xor (first ushr 3)
                val sigma1 = rotateRight(second, 17) xor rotateRight(second, 19) xor (second ushr 10)
                words[index] = (words[index - 16] + sigma0 + words[index - 7] + sigma1) and WORD_MASK
            }

            var a = state[0]
            var b = state[1]
            var c = state[2]
            var d = state[3]
            var e = state[4]
            var f = state[5]
            var g = state[6]
            var h = state[7]

            for (index in 0 until WORDS_PER_BLOCK) {
                val sum1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
                val choose = (e and f) xor (e.inv() and g)
                val temporary1 = (h + sum1 + choose + ROUND_CONSTANTS[index] + words[index]) and WORD_MASK
                val sum0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
                val majority = (a and b) xor (a and c) xor (b and c)
                val temporary2 = (sum0 + majority) and WORD_MASK

                h = g
                g = f
                f = e
                e = (d + temporary1) and WORD_MASK
                d = c
                c = b
                b = a
                a = (temporary1 + temporary2) and WORD_MASK
            }

            state[0] = (state[0] + a) and WORD_MASK
            state[1] = (state[1] + b) and WORD_MASK
            state[2] = (state[2] + c) and WORD_MASK
            state[3] = (state[3] + d) and WORD_MASK
            state[4] = (state[4] + e) and WORD_MASK
            state[5] = (state[5] + f) and WORD_MASK
            state[6] = (state[6] + g) and WORD_MASK
            state[7] = (state[7] + h) and WORD_MASK
        }

        return ByteArray(DIGEST_BYTES).also { digest ->
            state.forEachIndexed { index, word ->
                val offset = index * Int.SIZE_BYTES
                digest[offset] = (word ushr 24).toByte()
                digest[offset + 1] = (word ushr 16).toByte()
                digest[offset + 2] = (word ushr 8).toByte()
                digest[offset + 3] = word.toByte()
            }
        }
    }

    private fun rotateRight(
        value: Long,
        bitCount: Int,
    ): Long = ((value ushr bitCount) or (value shl (Int.SIZE_BITS - bitCount))) and WORD_MASK

    private const val BLOCK_BYTES = 64
    private const val INPUT_WORDS = 16
    private const val WORDS_PER_BLOCK = 64
    private const val DIGEST_BYTES = 32
    private const val BYTE_MASK = 0xffL
    private const val WORD_MASK = 0xffffffffL

    private val INITIAL =
        longArrayOf(
            0x6a09e667L,
            0xbb67ae85L,
            0x3c6ef372L,
            0xa54ff53aL,
            0x510e527fL,
            0x9b05688cL,
            0x1f83d9abL,
            0x5be0cd19L,
        )

    private val ROUND_CONSTANTS =
        longArrayOf(
            0x428a2f98L,
            0x71374491L,
            0xb5c0fbcfL,
            0xe9b5dba5L,
            0x3956c25bL,
            0x59f111f1L,
            0x923f82a4L,
            0xab1c5ed5L,
            0xd807aa98L,
            0x12835b01L,
            0x243185beL,
            0x550c7dc3L,
            0x72be5d74L,
            0x80deb1feL,
            0x9bdc06a7L,
            0xc19bf174L,
            0xe49b69c1L,
            0xefbe4786L,
            0x0fc19dc6L,
            0x240ca1ccL,
            0x2de92c6fL,
            0x4a7484aaL,
            0x5cb0a9dcL,
            0x76f988daL,
            0x983e5152L,
            0xa831c66dL,
            0xb00327c8L,
            0xbf597fc7L,
            0xc6e00bf3L,
            0xd5a79147L,
            0x06ca6351L,
            0x14292967L,
            0x27b70a85L,
            0x2e1b2138L,
            0x4d2c6dfcL,
            0x53380d13L,
            0x650a7354L,
            0x766a0abbL,
            0x81c2c92eL,
            0x92722c85L,
            0xa2bfe8a1L,
            0xa81a664bL,
            0xc24b8b70L,
            0xc76c51a3L,
            0xd192e819L,
            0xd6990624L,
            0xf40e3585L,
            0x106aa070L,
            0x19a4c116L,
            0x1e376c08L,
            0x2748774cL,
            0x34b0bcb5L,
            0x391c0cb3L,
            0x4ed8aa4aL,
            0x5b9cca4fL,
            0x682e6ff3L,
            0x748f82eeL,
            0x78a5636fL,
            0x84c87814L,
            0x8cc70208L,
            0x90befffaL,
            0xa4506cebL,
            0xbef9a3f7L,
            0xc67178f2L,
        )
}
