package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow

/**
 * A labelled progress bar with the label above the bar: "Al-Faatiha            33%" over the bar.
 * Unlike [ProgressBarRow] the label has the whole width to itself, so long surah names and
 * Persian/Arabic labels are never truncated on a phone. Same semantics as [ProgressBarRow]:
 * one node described "label, value".
 */
@Composable
fun StackedProgressRow(
    label: String,
    valueText: String,
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$label, $valueText" },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = label,
                style = QintarType.Label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = valueText, style = QintarType.Numeric, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        QProgressBar(progress, Modifier.fillMaxWidth(), color = color, trackColor = trackColor)
    }
}
