package io.github.mehrdad_abdi.qintar.diagnostics

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.view.drawToBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import java.io.ByteArrayOutputStream

/** Test tag of the floating "mark the problem" button. */
const val DIAGNOSTICS_MARKER_TAG = "diagnostics-marker"

/**
 * While the canary records, a small bug button floats over every screen: a tap writes "problem
 * here" with the current [screen] into the log and, if allowed, takes a screenshot of the app.
 */
@Composable
fun DiagnosticsMarker(diagnostics: Diagnostics, screen: String, modifier: Modifier = Modifier) {
    val state by diagnostics.state.collectAsStateWithLifecycle()
    if (!state.enabled) return
    val view = LocalView.current
    val context = LocalContext.current
    val marked = stringResource(if (state.screenshotsAllowed) R.string.diagnostics_marked_with_screenshot else R.string.diagnostics_marked)
    IconButton(
        onClick = {
            diagnostics.log("marker", "the user marked a problem on $screen")
            if (state.screenshotsAllowed) {
                runCatching {
                    val png = ByteArrayOutputStream().also { view.rootView.drawToBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    diagnostics.saveScreenshot(png.toByteArray())
                }.onFailure { diagnostics.log("marker", "screenshot failed: ${it.javaClass.simpleName}") }
            }
            Toast.makeText(context, marked, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f))
            .testTag(DIAGNOSTICS_MARKER_TAG),
    ) {
        Icon(Icons.Outlined.BugReport, stringResource(R.string.diagnostics_mark), tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier)
    }
}

