package com.stanisryz.logica.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.state_empty_gallery
import com.stanisryz.logica.shared.ui.generated.resources.state_load_failed
import com.stanisryz.logica.shared.ui.generated.resources.state_no_games
import com.stanisryz.logica.shared.ui.generated.resources.state_no_hints
import com.stanisryz.logica.shared.ui.generated.resources.state_no_lives
import com.stanisryz.logica.shared.ui.generated.resources.state_second_chance
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource

/** The pictures of empty states and dialogs; hosts pick one, while the drawables stay shared. */
enum class StateArtwork(
    internal val drawable: DrawableResource,
) {
    NO_GAMES(Res.drawable.state_no_games),
    NO_LIVES(Res.drawable.state_no_lives),
    SECOND_CHANCE(Res.drawable.state_second_chance),
    NO_HINTS(Res.drawable.state_no_hints),
    LOAD_FAILED(Res.drawable.state_load_failed),
    EMPTY_GALLERY(Res.drawable.state_empty_gallery),
}

/** One state picture; decorative, since the text next to it always says the same. */
@Composable
fun StateArtworkImage(
    artwork: StateArtwork,
    modifier: Modifier = Modifier,
    size: Dp = STATE_ARTWORK_SIZE,
) {
    Image(
        imageResource(artwork.drawable),
        contentDescription = null,
        modifier = modifier.size(size),
        filterQuality = ArtworkFilterQuality,
    )
}

/** The size of a state picture in a dialog. */
val STATE_ARTWORK_DIALOG_SIZE = 88.dp

private val STATE_ARTWORK_SIZE = 120.dp

/**
 * Filtering for the generated artwork, which is always drawn well below its source size (a 128 px
 * icon at 18 dp, a 400 px card at a third of it). The default filter samples without mipmaps and
 * turns such pictures jagged on 1x desktop screens; mipmapped sampling keeps them smooth.
 */
val ArtworkFilterQuality: FilterQuality = FilterQuality.Medium
