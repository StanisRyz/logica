package com.stanisryz.logica.puzzle.core.word

/** Supplies the canonical bundled lexicon files without exposing platform resource APIs. */
internal expect object BundledWordResources {
    fun readText(resource: String): String
}
