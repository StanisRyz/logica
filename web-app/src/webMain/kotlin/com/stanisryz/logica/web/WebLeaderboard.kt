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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 * The one Web leaderboard, "solved puzzles": the bound Player's solved total is submitted
 * best-effort whenever it grows (one call in flight, then the latest value, spaced for the
 * platform's rate limit), and the table is read only when the Profile shows it. Nothing here is
 * persisted or synced; a failed or unauthorized call simply leaves the table as it was.
 */
internal class WebLeaderboardController(
    private val bridge: WebLeaderboardBridge,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<WebLeaderboardState>(WebLeaderboardState.Idle)
    val state: StateFlow<WebLeaderboardState> = mutableState.asStateFlow()

    val isSupported: Boolean get() = bridge.isLeaderboardsSupported()

    private var playerKey: Any? = null
    private var submitted = 0L
    private var pending: Long? = null
    private var submitting = false

    /** Submits [solvedTotal] for the Player identified by [player] when it beats the last value sent. */
    fun submitSolved(
        player: Any,
        solvedTotal: Long,
    ) {
        if (!isSupported) return
        if (player != playerKey) {
            // Another Player: forget what the previous one sent and the table it saw.
            playerKey = player
            submitted = 0L
            pending = null
            mutableState.value = WebLeaderboardState.Idle
        }
        if (solvedTotal <= submitted || solvedTotal <= (pending ?: 0L)) return
        pending = solvedTotal
        if (submitting) return
        submitting = true
        scope.launch {
            while (true) {
                val value = pending ?: break
                pending = null
                val key = playerKey
                val sent = bridge.setLeaderboardScore(SOLVED_LEADERBOARD, value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                if (sent && key == playerKey) submitted = maxOf(submitted, value)
                delay(MIN_CALL_SPACING_MS)
            }
            submitting = false
        }
    }

    /** Reads the table once per explicit request (Profile open or refresh). */
    fun load() {
        if (!isSupported || mutableState.value == WebLeaderboardState.Loading) return
        mutableState.value = WebLeaderboardState.Loading
        scope.launch {
            mutableState.value =
                bridge.leaderboardEntries(SOLVED_LEADERBOARD)?.let(WebLeaderboardState::Ready) ?: WebLeaderboardState.Unavailable
        }
    }

    companion object {
        /** Technical name configured in the Yandex Games console. */
        const val SOLVED_LEADERBOARD = "solved"
        private const val MIN_CALL_SPACING_MS = 1_100L
    }
}

/** The Profile's leaderboard card on Yandex Games; standalone development never shows it. */
@Composable
internal fun WebLeaderboardCard(controller: WebLeaderboardController) {
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) { if (controller.state.value == WebLeaderboardState.Idle) controller.load() }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Рейтинг: решено головоломок",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = controller::load, enabled = state != WebLeaderboardState.Loading) { Text("Обновить") }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        ) {
            Column(Modifier.padding(LogicaSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (val current = state) {
                    WebLeaderboardState.Idle, WebLeaderboardState.Loading ->
                        Text("Загружаем рейтинг…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    WebLeaderboardState.Unavailable ->
                        Text(
                            "Рейтинг сейчас недоступен. Попробуйте позже.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    is WebLeaderboardState.Ready -> LeaderboardRows(current.snapshot)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRows(snapshot: WebLeaderboardSnapshot) {
    val colors = MaterialTheme.colorScheme
    if (snapshot.entries.isEmpty()) {
        Text("Пока в рейтинге никого нет.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
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
                    .background(if (mine) colors.primaryContainer else colors.surfaceContainerLow)
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
                if (mine) "Вы" else entry.name ?: "Игрок",
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
            "Решайте головоломки, чтобы попасть в рейтинг.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}
