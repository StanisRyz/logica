package com.stanisryz.logica.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.icon_gem
import com.stanisryz.logica.shared.ui.generated.resources.icon_heart
import com.stanisryz.logica.shared.ui.generated.resources.icon_heart_broken
import com.stanisryz.logica.shared.ui.generated.resources.icon_hint
import com.stanisryz.logica.shared.ui.generated.resources.icon_medal_bronze
import com.stanisryz.logica.shared.ui.generated.resources.icon_medal_gold
import com.stanisryz.logica.shared.ui.generated.resources.icon_medal_silver
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The small coloured pictures of the game's currencies and ranks. They stand where a wallet,
 * balance, or reward is shown; buttons, tools, and tabs keep their tinted vector icons.
 */
enum class GameIcon(
    internal val drawable: DrawableResource,
) {
    GEM(Res.drawable.icon_gem),
    HEART(Res.drawable.icon_heart),
    HEART_BROKEN(Res.drawable.icon_heart_broken),
    HINT(Res.drawable.icon_hint),
    MEDAL_GOLD(Res.drawable.icon_medal_gold),
    MEDAL_SILVER(Res.drawable.icon_medal_silver),
    MEDAL_BRONZE(Res.drawable.icon_medal_bronze),
    ;

    companion object {
        /** The heart of a wallet: broken while no life is left. */
        fun lives(lives: Int): GameIcon = if (lives > 0) HEART else HEART_BROKEN

        /** The medal of the first three places, or null below them. */
        fun medal(rank: Int): GameIcon? =
            when (rank) {
                1 -> MEDAL_GOLD
                2 -> MEDAL_SILVER
                3 -> MEDAL_BRONZE
                else -> null
            }
    }
}

/** One game icon; decorative, since its value or label is always next to it. */
@Composable
fun GameIconImage(
    icon: GameIcon,
    modifier: Modifier = Modifier,
    size: Dp = GAME_ICON_SIZE,
) {
    Image(painterResource(icon.drawable), contentDescription = null, modifier = modifier.size(size))
}

/** A top-three place as its medal alone; the place number is left to screen readers. */
@Composable
fun RankMedal(
    medal: GameIcon,
    rank: Int,
    modifier: Modifier = Modifier,
    size: Dp = MEDAL_SIZE,
) {
    GameIconImage(medal, modifier.semantics { contentDescription = rank.toString() }, size = size)
}

private val GAME_ICON_SIZE = 18.dp
private val MEDAL_SIZE = 30.dp
