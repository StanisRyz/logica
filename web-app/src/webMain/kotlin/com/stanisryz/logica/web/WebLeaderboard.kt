package com.stanisryz.logica.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.web.generated.resources.web_leaderboard_empty
import com.stanisryz.logica.web.generated.resources.web_leaderboard_join
import com.stanisryz.logica.web.generated.resources.web_leaderboard_loading
import com.stanisryz.logica.web.generated.resources.web_leaderboard_player
import com.stanisryz.logica.web.generated.resources.web_leaderboard_title
import com.stanisryz.logica.web.generated.resources.web_leaderboard_unavailable
import com.stanisryz.logica.web.generated.resources.web_leaderboard_you
import com.stanisryz.logica.web.generated.resources.web_refresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

/** The Yandex leaderboard calls the controller needs; `YandexGamesBridge` implements them. */
internal interface WebLeaderboardBridge {
    fun isLeaderboardsSupported(): Boolean

    suspend fun setLeaderboardScore(
        name: String,
        score: Int,
    ): Boolean

    suspend fun leaderboardEntries(name: String): WebLeaderboardSnapshot?
}

internal data class WebLeaderboardEntry(
    val rank: Int,
    val score: Int,
    val name: String?,
)

internal data class WebLeaderboardSnapshot(
    val entries: List<WebLeaderboardEntry>,
    val playerRank: Int?,
)

internal sealed interface WebLeaderboardState {
    data object Idle : WebLeaderboardState

    data object Loading : WebLeaderboardState

    data class Ready(
        val snapshot: WebLeaderboardSnapshot,
    ) : WebLeaderboardState

    data object Unavailable : WebLeaderboardState
}

/**
 * The Web leaderboards: "solved puzzles" for the Profile plus one rating table per game. Each
 * value is submitted best-effort whenever it grows (one call in flight, then the latest pending
 * value of every table, spaced for the platform's rate limit), and a table is read only when a
 * screen shows it. Nothing here is persisted or synced; a failed or unauthorized call — a guest
 * Player — simply leaves the table as it was, and the Player's own points stay visible regardless.
 */
internal class WebLeaderboardController(
    private val bridge: WebLeaderboardBridge,
    private val scope: CoroutineScope,
) {
    private val states = mutableMapOf<String, MutableStateFlow<WebLeaderboardState>>()

    val isSupported: Boolean get() = bridge.isLeaderboardsSupported()

    private var playerKey: Any? = null
    private val submitted = mutableMapOf<String, Long>()
    private val pending = linkedMapOf<String, Long>()
    private var submitting = false

    /** The table [board] as last read; Idle until a screen asks for it. */
    fun state(board: String): StateFlow<WebLeaderboardState> = mutableState(board).asStateFlow()

    /** Submits [value] to [board] for the Player identified by [player] when it beats the last value sent. */
    fun submit(
        board: String,
        player: Any,
        value: Long,
    ) {
        if (!isSupported) return
        if (player != playerKey) {
            // Another Player: forget what the previous one sent and the tables it saw.
            playerKey = player
            submitted.clear()
            pending.clear()
            states.values.forEach { it.value = WebLeaderboardState.Idle }
        }
        if (value <= 0L || value <= (submitted[board] ?: 0L) || value <= (pending[board] ?: 0L)) return
        pending[board] = value
        if (submitting) return
        submitting = true
        scope.launch {
            while (true) {
                val (name, next) = pending.entries.firstOrNull()?.toPair() ?: break
                pending.remove(name)
                val key = playerKey
                val sent = bridge.setLeaderboardScore(name, next.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                if (sent && key == playerKey) {
                    submitted[name] = maxOf(submitted[name] ?: 0L, next)
                    // A table read before this value is stale; the next look reads it again.
                    if (mutableState(name).value is WebLeaderboardState.Ready) mutableState(name).value = WebLeaderboardState.Idle
                }
                delay(MIN_CALL_SPACING_MS)
            }
            submitting = false
        }
    }

    /** Reads [board] once per explicit request (a screen opening it, or refresh). */
    fun load(board: String) {
        val state = mutableState(board)
        if (!isSupported || state.value == WebLeaderboardState.Loading) return
        state.value = WebLeaderboardState.Loading
        scope.launch {
            state.value = bridge.leaderboardEntries(board)?.let(WebLeaderboardState::Ready) ?: WebLeaderboardState.Unavailable
        }
    }

    private fun mutableState(board: String): MutableStateFlow<WebLeaderboardState> =
        states.getOrPut(board) { MutableStateFlow(WebLeaderboardState.Idle) }

    companion object {
        /** Technical names configured in the Yandex Games console. */
        const val SOLVED_LEADERBOARD = "solved"
        private const val MIN_CALL_SPACING_MS = 1_100L

        /** The rating table of [puzzleType]: points for cleared levels, or 2048's best score. */
        fun ratingLeaderboard(puzzleType: PuzzleType): String =
            when (puzzleType) {
                PuzzleType.BALANCE -> "rating_balance"
                PuzzleType.CROWNS -> "rating_crowns"
                PuzzleType.WORD -> "rating_word"
                PuzzleType.SUDOKU -> "rating_sudoku"
                PuzzleType.NONOGRAM -> "rating_nonogram"
                PuzzleType.GAME_2048 -> "best_2048"
                else -> error("$puzzleType has no rating leaderboard.")
            }
    }
}

/** The Profile's leaderboard card on Yandex Games; standalone development never shows it. */
@Composable
internal fun WebLeaderboardCard(controller: WebLeaderboardController) {
    val board = WebLeaderboardController.SOLVED_LEADERBOARD
    val state by controller.state(board).collectAsState()
    LaunchedEffect(controller) { if (controller.state(board).value == WebLeaderboardState.Idle) controller.load(board) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(WebRes.string.web_leaderboard_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { controller.load(board) },
                enabled = state != WebLeaderboardState.Loading,
            ) { Text(stringResource(WebRes.string.web_refresh)) }
        }
        LeaderboardTable(state)
    }
}

/** One game's rating table inside its rating sheet; read once when first shown. */
@Composable
internal fun WebRatingLeaderboard(
    controller: WebLeaderboardController,
    puzzleType: PuzzleType,
) {
    val board = WebLeaderboardController.ratingLeaderboard(puzzleType)
    val state by controller.state(board).collectAsState()
    LaunchedEffect(board) { if (controller.state(board).value == WebLeaderboardState.Idle) controller.load(board) }
    // The rating sheet already sits on the low container, so its table takes the lightest surface.
    LeaderboardTable(state, MaterialTheme.colorScheme.surfaceContainerLowest)
}

@Composable
private fun LeaderboardTable(
    state: WebLeaderboardState,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(LogicaSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (state) {
                WebLeaderboardState.Idle, WebLeaderboardState.Loading ->
                    Text(
                        stringResource(WebRes.string.web_leaderboard_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                WebLeaderboardState.Unavailable ->
                    Text(
                        stringResource(WebRes.string.web_leaderboard_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                is WebLeaderboardState.Ready -> LeaderboardRows(state.snapshot, containerColor)
            }
        }
    }
}

@Composable
private fun LeaderboardRows(
    snapshot: WebLeaderboardSnapshot,
    rowColor: Color,
) {
    val colors = MaterialTheme.colorScheme
    if (snapshot.entries.isEmpty()) {
        Text(
            stringResource(WebRes.string.web_leaderboard_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        return
    }
    val sorted = snapshot.entries.distinctBy { it.rank }.sortedBy { it.rank }
    sorted.forEachIndexed { index, entry ->
        if (index > 0 && entry.rank > sorted[index - 1].rank + 1) {
            Text("…", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
        }
        val mine = entry.rank == snapshot.playerRank
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(if (mine) colors.primaryContainer else rowColor)
                    .padding(horizontal = LogicaSpacing.item, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.rank.toString(),
                modifier = Modifier.width(36.dp),
                style = MaterialTheme.typography.titleSmall,
                color = if (mine) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
            Text(
                if (mine) {
                    stringResource(WebRes.string.web_leaderboard_you)
                } else {
                    entry.name
                        ?: stringResource(WebRes.string.web_leaderboard_player)
                },
                modifier = Modifier.weight(1f).padding(end = LogicaSpacing.item),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (mine) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (mine) colors.onPrimaryContainer else colors.onSurface,
            )
            Text(
                entry.score.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = if (mine) colors.onPrimaryContainer else colors.onSurface,
            )
        }
    }
    if (snapshot.playerRank == null) {
        Text(
            stringResource(WebRes.string.web_leaderboard_join),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}
