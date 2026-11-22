package io.github.mehrdad_abdi.qintar.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.designsystem.BasmalaRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing

/**
 * The hadith the seven ranks come from: the Bismillah, then the narration in Arabic, English and
 * Persian, whatever the app language (onboarding's first page, Settings → About).
 */
@Composable
fun HadithText(modifier: Modifier = Modifier, withBasmala: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (withBasmala) BasmalaRow()
        Text(stringResource(R.string.hadith_intro), style = QintarType.Body, color = colors.onSurfaceVariant)
        HadithPart(stringResource(R.string.hadith_label_ar), stringResource(R.string.hadith_text_ar), LayoutDirection.Rtl, arabic = true)
        HadithPart(stringResource(R.string.hadith_label_en), stringResource(R.string.hadith_text_en), LayoutDirection.Ltr)
        HadithPart(stringResource(R.string.hadith_label_fa), stringResource(R.string.hadith_text_fa), LayoutDirection.Rtl)
    }
}

@Composable
private fun HadithPart(label: String, text: String, direction: LayoutDirection, arabic: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = QintarType.Label, color = colors.primary)
        // Each text keeps its own direction, so English reads left to right in a Persian app and back.
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Text(
                text,
                style = if (arabic) QintarType.Title else QintarType.Body,
                color = colors.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
