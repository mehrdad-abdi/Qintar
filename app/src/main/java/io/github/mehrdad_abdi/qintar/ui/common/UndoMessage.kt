package io.github.mehrdad_abdi.qintar.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

/**
 * A message with Undo. Material keeps a message with an action on screen until it is tapped; this
 * one goes away by itself after a few seconds (owner, 2026-11-24). Undo is offered only meanwhile.
 */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): SnackbarResult =
    showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long)

/** The app's message host: a swipe either way closes the message. */
@Composable
fun MessageHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        // A fresh swipe state for every message, so a swiped one does not leave the next one swiped.
        key(data) {
            val swipe = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) data.dismiss()
                    true
                },
            )
            SwipeToDismissBox(swipe, backgroundContent = {}) { androidx.compose.material3.Snackbar(data) }
        }
    }
}
