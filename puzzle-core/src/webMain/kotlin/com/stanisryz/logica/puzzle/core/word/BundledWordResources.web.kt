package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.web.WebPuzzleData

internal actual object BundledWordResources {
    actual fun readText(resource: String): String = WebPuzzleData.readWordResource(resource)
}
