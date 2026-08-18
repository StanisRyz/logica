package com.stanisryz.logica.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

/** A decorative full-card image resolved by resource name so photo assets remain optional. */
@Composable
internal fun CatalogCardArtwork(
    drawableName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val drawableId =
        remember(context, drawableName) {
            context.resources.getIdentifier(drawableName, "drawable", context.packageName)
        }
    if (drawableId == 0) return
    Image(
        painter = painterResource(drawableId),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(androidx.compose.material3.MaterialTheme.shapes.medium),
    )
}

/** A photo-aware text field: pale at the left edge and fully transparent at the right edge. */
@Composable
internal fun CatalogCardLabelScrim(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.background(
                Brush.horizontalGradient(
                    0f to CATALOG_LABEL_SCRIM,
                    1f to Color.Transparent,
                ),
            ),
    )
}

private val CATALOG_LABEL_SCRIM = Color(0xFFF4F8FB).copy(alpha = 0.15f)

internal const val CATALOG_CARD_TITLE_SCALE = 1.40625f
