package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Задача дня",
        message = "Здесь появится ежедневная задача.",
        modifier = modifier,
    )
}
