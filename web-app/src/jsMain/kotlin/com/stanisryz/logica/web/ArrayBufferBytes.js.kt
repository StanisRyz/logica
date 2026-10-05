package com.stanisryz.logica.web

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array

/** On Kotlin/JS a `ByteArray` is an `Int8Array`, so the fetched buffer is used as it is. */
internal actual fun ArrayBuffer.toByteArray(): ByteArray = Int8Array(this).unsafeCast<ByteArray>()
