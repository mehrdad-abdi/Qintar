package io.github.mehrdad_abdi.qintar.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing

/**
 * Stand-in for a screen that a feature has not filled in yet. Delete the call when the real
 * screen lands; nothing else depends on this.
 *
 * @param detail debugging aid: the route arguments the destination received.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.xs).size(Sizes.touchTarget)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    title = title,
                    message = stringResource(R.string.placeholder_message),
                    icon = Icons.Outlined.AutoAwesome,
                )
                if (detail != null) {
                    Text(detail, style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
