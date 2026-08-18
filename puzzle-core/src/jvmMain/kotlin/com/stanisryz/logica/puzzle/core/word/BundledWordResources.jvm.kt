package com.stanisryz.logica.puzzle.core.word

internal actual object BundledWordResources {
    actual fun readText(resource: String): String {
        val stream =
            checkNotNull(BundledWordResources::class.java.getResourceAsStream(resource)) {
                "Bundled Word lexicon resource $resource is missing."
            }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
