package com.stanisryz.logica.ui.crowns

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.ic_crown
import org.jetbrains.compose.resources.painterResource

/** The canonical Crown artwork shared by boards, tools, and Android catalog presentation. */
@Composable
fun CrownIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(Res.drawable.ic_crown),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}
