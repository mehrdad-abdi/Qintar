package io.github.mehrdad_abdi.qintar.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The notification permission, asked for at the moment a reminder is turned on (and only then).
 * Returns the function to call when the user flips a reminder on: it reports `true` at once when no
 * permission is needed (before Android 13) or it is already granted, and otherwise shows the system
 * dialog and reports its answer.
 */
@Composable
fun rememberNotificationGate(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { callback.value(it) }
    return {
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val granted = !needed ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) callback.value(true) else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
