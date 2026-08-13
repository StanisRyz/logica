package com.stanisryz.logica.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R

/**
 * The single seam between the shell's Back handling and gameplay. Unfinished attempts are no longer
 * saved, so leaving one throws its board away — the shell asks the active gameplay screen first
 * instead of every screen intercepting Back on its own.
 */
internal class GameplayExitGuard {
    /** Returns true when gameplay took over the request and will decide what happens next. */
    private var interceptor: ((() -> Unit) -> Boolean)? = null

    fun requestBack(proceed: () -> Unit) {
        if (interceptor?.invoke(proceed) != true) proceed()
    }

    internal fun install(interceptor: (() -> Unit) -> Boolean) {
        this.interceptor = interceptor
    }

    internal fun uninstall(interceptor: (() -> Unit) -> Boolean) {
        if (this.interceptor === interceptor) this.interceptor = null
    }
}

/**
 * Confirms leaving a level that still has meaningful progress on it. It deliberately stays silent
 * before the first real move, after a terminal result, and for a 2048 level that is already cleared:
 * in those cases there is nothing left to lose.
 */
@Composable
internal fun LeaveLevelGuard(
    guard: GameplayExitGuard,
    hasProgress: Boolean,
    exitBlocked: Boolean = false,
) {
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showBlockedNotice by remember { mutableStateOf(false) }
    val currentHasProgress by rememberUpdatedState(hasProgress)
    val currentExitBlocked by rememberUpdatedState(exitBlocked)
    LaunchedEffect(exitBlocked) {
        if (!exitBlocked) showBlockedNotice = false
    }
    DisposableEffect(guard) {
        val interceptor: (() -> Unit) -> Boolean = { proceed ->
            when {
                currentExitBlocked -> {
                    showBlockedNotice = true
                    true
                }
                currentHasProgress -> {
                    pendingLeave = proceed
                    true
                }
                else -> false
            }
        }
        guard.install(interceptor)
        onDispose { guard.uninstall(interceptor) }
    }

    pendingLeave?.let { proceed ->
        AlertDialog(
            onDismissRequest = { pendingLeave = null },
            title = { Text(stringResource(R.string.leave_level_title)) },
            text = { Text(stringResource(R.string.leave_level_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLeave = null
                        proceed()
                    },
                ) { Text(stringResource(R.string.leave_level_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingLeave = null }) { Text(stringResource(R.string.leave_level_stay)) }
            },
        )
    }

    if (showBlockedNotice) {
        AlertDialog(
            onDismissRequest = { showBlockedNotice = false },
            title = { Text(stringResource(R.string.saving_completion)) },
            text = { Text(stringResource(R.string.completion_exit_saving_body)) },
            confirmButton = {
                TextButton(onClick = { showBlockedNotice = false }) { Text(stringResource(R.string.leave_level_stay)) }
            },
        )
    }
}
