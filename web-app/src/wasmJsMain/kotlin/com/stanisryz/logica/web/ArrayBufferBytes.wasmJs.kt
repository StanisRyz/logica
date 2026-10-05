@file:OptIn(ExperimentalWasmJsInterop::class, UnsafeWasmMemoryApi::class)

package com.stanisryz.logica.web

import org.khronos.webgl.ArrayBuffer
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * Copies the buffer through Wasm linear memory in chunks: JavaScript writes each chunk with one
 * `set`, and Wasm reads it without a bridge call per byte (the way Compose resources load bytes).
 */
internal actual fun ArrayBuffer.toByteArray(): ByteArray {
    val size = byteLength
    val bytes = ByteArray(size)
    if (size == 0) return bytes
    withScopedMemoryAllocator { allocator ->
        val chunk = minOf(size, COPY_CHUNK_BYTES)
        val pointer = allocator.allocate(chunk)
        var offset = 0
        while (offset < size) {
            val length = minOf(chunk, size - offset)
            copyIntoWasmMemory(this, offset, length, pointer.address.toInt())
            for (index in 0 until length) bytes[offset + index] = (pointer + index).loadByte()
            offset += length
        }
    }
    return bytes
}

private fun copyIntoWasmMemory(
    buffer: ArrayBuffer,
    offset: Int,
    length: Int,
    address: Int,
) {
    js("new Uint8Array(wasmExports.memory.buffer, address, length).set(new Uint8Array(buffer, offset, length))")
}

private const val COPY_CHUNK_BYTES = 64 * 1024
