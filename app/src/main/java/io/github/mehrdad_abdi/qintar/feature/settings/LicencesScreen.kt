package io.github.mehrdad_abdi.qintar.feature.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.common.ScreenTopBar
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One licence text shipped in `assets/licenses/`; [name] is the file name without its extension. */
data class LicenceText(val name: String, val text: String)

const val LICENCES_ASSET_DIR = "licenses"
const val LICENCES_TAG = "licences-content"

/** Reads every every text file in `assets/licenses/`, in name order. */
fun readLicences(context: Context): List<LicenceText> =
    context.assets.list(LICENCES_ASSET_DIR).orEmpty()
        .filter { it.endsWith(".txt") }
        .sorted()
        .map { file ->
            val text = context.assets.open("$LICENCES_ASSET_DIR/$file").use { String(it.readBytes(), Charsets.UTF_8) }
            LicenceText(file.removeSuffix(".txt"), text.trim())
        }

/** The licences of the fonts and the Tanzil attribution (the terms require it to be shown). */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val licences by produceState(emptyList<LicenceText>(), context) { value = withContext(Dispatchers.IO) { readLicences(context) } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTopBar(stringResource(R.string.settings_licences), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.sm).testTag(LICENCES_TAG),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            QCard {
                Text(stringResource(R.string.settings_tanzil_title), style = QintarType.Title)
                Text(stringResource(R.string.settings_tanzil_body), style = QintarType.Body)
            }
            licences.forEach { licence ->
                QCard {
                    Text(licence.name, style = QintarType.Title)
                    Text(licence.text, style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
