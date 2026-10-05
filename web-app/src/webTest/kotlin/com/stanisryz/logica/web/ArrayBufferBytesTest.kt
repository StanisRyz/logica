package com.stanisryz.logica.web

import org.khronos.webgl.Int8Array
import org.khronos.webgl.set
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** The fetched bytes arrive exactly, chunk edges and the sign boundary included. */
class ArrayBufferBytesTest {
    @Test
    fun everyByteSurvivesTheCopy() {
        val size = 2 * 64 * 1024 + 3
        val pattern = byteArrayOf(0, 0x7F, 0x80.toByte(), 0xFF.toByte(), 1, 0x55)
        val expected = ByteArray(size) { pattern[it % pattern.size] }
        val source = Int8Array(size)
        expected.forEachIndexed { index, byte -> source[index] = byte }

        assertContentEquals(expected, source.buffer.toByteArray())
    }

    @Test
    fun anEmptyBufferIsAnEmptyArray() {
        assertContentEquals(ByteArray(0), Int8Array(0).buffer.toByteArray())
    }
}
