package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.components.catalogTitleResource
import com.stanisryz.logica.ui.tutorial.BalanceTutorial
import com.stanisryz.logica.ui.tutorial.CrownsTutorial
import com.stanisryz.logica.ui.tutorial.Game2048Tutorial
import com.stanisryz.logica.ui.tutorial.NonogramTutorial
import com.stanisryz.logica.ui.tutorial.SudokuTutorial
import com.stanisryz.logica.ui.tutorial.WordTutorial
import com.stanisryz.logica.web.generated.resources.web_back
import com.stanisryz.logica.web.generated.resources.web_how_to_play_title
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

/** Opens the shared onboarding for a game; provided by the host around the game routes. */
internal val LocalOpenTutorial = staticCompositionLocalOf<(PuzzleType) -> Unit> { {} }

/**
 * Which games already offered their tutorial in this browser. A per-browser convenience only:
 * it is never Player-scoped, never synced, and unavailable storage merely offers again next visit.
 */
internal object WebTutorialOffers {
    private var offered by mutableStateOf(readOffered())

    fun isPending(puzzleType: PuzzleType): Boolean = puzzleType.name !in offered

    fun markOffered(puzzleType: PuzzleType) {
        if (!isPending(puzzleType)) return
        offered = offered + puzzleType.name
        runCatching { tutorialOffersStorageSet(TUTORIAL_OFFERS_KEY, offered.sorted().joinToString(",")) }
    }

    private fun readOffered(): Set<String> =
        runCatching { tutorialOffersStorageGet(TUTORIAL_OFFERS_KEY) }
            .getOrNull()
            ?.split(',')
            ?.filter(String::isNotEmpty)
            ?.toSet()
            .orEmpty()
}

private const val TUTORIAL_OFFERS_KEY = "logica_tutorial_offers_v1"

private fun tutorialOffersStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun tutorialOffersStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}

/**
 * Web host for the shared onboarding. Tutorials never touch Catalog progress, Daily, statistics,
 * the economy, or hint stock, so leaving one at any step simply returns to difficulty selection.
 */
@Composable
internal fun WebTutorialScreen(
    puzzleType: PuzzleType,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(TUTORIAL_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text(stringResource(WebRes.string.web_back)) }
            Text(
                text = stringResource(WebRes.string.web_how_to_play_title, stringResource(puzzleType.catalogTitleResource())),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val modifier = Modifier.weight(1f)
        when (puzzleType) {
            PuzzleType.BALANCE -> BalanceTutorial(onDone = onClose, modifier = modifier)
            PuzzleType.CROWNS -> CrownsTutorial(onDone = onClose, modifier = modifier)
            PuzzleType.WORD -> WordTutorial(onDone = onClose, modifier = modifier)
            PuzzleType.SUDOKU -> SudokuTutorial(onDone = onClose, modifier = modifier)
            PuzzleType.GAME_2048 ->
                Game2048Tutorial(onDone = onClose, modifier = modifier, hardwareKeys = LocalWebKeyboard.current?.keys)
            PuzzleType.NONOGRAM -> NonogramTutorial(onDone = onClose, modifier = modifier)
            else -> Unit
        }
    }
}

private val TUTORIAL_HEADER_HEIGHT = 52.dp
