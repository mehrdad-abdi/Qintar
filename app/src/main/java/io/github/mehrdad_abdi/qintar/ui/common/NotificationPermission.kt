package io.github.mehrdad_abdi.qintar.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType

/** Test tag of the red "notifications are blocked" text. */
const val NOTIFICATIONS_BLOCKED_TAG = "notifications-blocked"

object NotificationGate {
    /**
     * Whether reminders can be shown, after the permission dialog answered [reported]. The system's
     * own state decides: on some phones the dialog reports "no" although the user allowed (the
     * owner's bug, 2026-11-22), and an allowed permission is not enough when the app's notifications
     * are switched off in the system.
     */
    fun resolve(reported: Boolean, allowedNow: Boolean): Boolean = allowedNow

    /** The permission (Android 13+) is granted and the app's notifications are not switched off. */
    fun allowed(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** The system screen with this app's notification settings. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * The notification permission, asked for at the moment a reminder is turned on (and only then).
 * Returns the function to call when the user flips a reminder on: it reports `true` at once when
 * notifications are already allowed, shows the system dialog when the permission is missing, and
 * otherwise (the app's notifications are off in the system) reports `false`.
 */
@Composable
fun rememberNotificationGate(onResult: (allowed: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reported ->
        callback.value(NotificationGate.resolve(reported, NotificationGate.allowed(context)))
    }
    return {
        val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            NotificationGate.allowed(context) -> callback.value(true)
            permissionMissing -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> callback.value(false)
        }
    }
}

/**
 * The red "notifications are blocked" text. A tap opens the app's notification settings; when the
 * screen comes back and notifications are allowed, [onAllowed] runs (the reminder goes on).
 */
@Composable
fun NotificationsBlockedNotice(onAllowed: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val allowed = rememberUpdatedState(onAllowed)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (NotificationGate.allowed(context)) allowed.value()
    }
    Text(
        stringResource(R.string.settings_notifications_blocked),
        style = QintarType.Caption,
        color = MaterialTheme.colorScheme.error,
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.settings_notifications_open)) {
                context.startActivity(NotificationGate.settingsIntent(context))
            }
            .testTag(NOTIFICATIONS_BLOCKED_TAG),
    )
}
