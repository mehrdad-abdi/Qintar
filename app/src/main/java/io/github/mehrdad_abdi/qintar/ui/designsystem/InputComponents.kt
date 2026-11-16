package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mehrdad_abdi.qintar.R

/**
 * Numeric field with − and + steppers, for "Set count…" and repeat targets. Typing accepts
 * Latin, Persian and Arabic-Indic digits; the value is clamped to [min]..[max].
 */
@Composable
fun CountField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    min: Int = 0,
    max: Int = 9999,
) {
    val fieldDescription = label ?: stringResource(R.string.count_field_label)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        FilledTonalIconButton(
            onClick = { onValueChange((value - 1).coerceIn(min, max)) },
            enabled = value > min,
            modifier = Modifier.size(Sizes.touchTarget),
        ) { Icon(Icons.Rounded.Remove, stringResource(R.string.count_field_decrease)) }
        OutlinedTextField(
            value = formatNumber(value),
            onValueChange = { typed ->
                val digits = typed.mapNotNull { ch -> Character.digit(ch, 10).takeIf { it >= 0 } }
                val parsed = digits.take(6).fold(0) { acc, d -> acc * 10 + d }
                onValueChange(parsed.coerceIn(min, max))
            },
            label = if (label != null) ({ Text(label) }) else null,
            singleLine = true,
            textStyle = QintarType.Headline.copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = QintarShapes.Inner,
            modifier = Modifier.width(128.dp).semantics { contentDescription = fieldDescription },
        )
        FilledTonalIconButton(
            onClick = { onValueChange((value + 1).coerceIn(min, max)) },
            enabled = value < max,
            modifier = Modifier.size(Sizes.touchTarget),
        ) { Icon(Icons.Rounded.Add, stringResource(R.string.count_field_increase)) }
    }
}

/**
 * The "Set count" dialog (§4.2.1): choose exactly how many times something was read today.
 *
 * @param supportingText one line of context, e.g. "Al-Baqarah 255 · today".
 */
@Composable
fun SetCountDialog(
    initialCount: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.set_count_title),
    supportingText: String? = null,
    max: Int = 9999,
) {
    var count by rememberSaveable { mutableIntStateOf(initialCount) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = QintarType.Title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (supportingText != null) {
                    Text(supportingText, style = QintarType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.lg))
                }
                CountField(count, { count = it }, max = max)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(count) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        shape = QintarShapes.Card,
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/**
 * Seven round toggles for choosing weekdays. [labels] and the indices of [selected] are in the
 * order the caller wants them shown (the week-start setting); the row follows layout direction.
 *
 * @param contentDescriptions full weekday names for TalkBack, same order as [labels].
 */
@Composable
fun WeekdayChips(
    labels: List<String>,
    selected: Set<Int>,
    onToggle: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptions: List<String> = labels,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { index, label ->
            val isSelected = index in selected
            // 48dp target with a 40dp visual so that seven fit a 320dp screen.
            Box(Modifier.size(Sizes.touchTarget), contentAlignment = Alignment.Center) {
                Surface(
                    checked = isSelected,
                    onCheckedChange = { onToggle(index) },
                    modifier = Modifier.size(40.dp).semantics {
                        contentDescription = contentDescriptions.getOrElse(index) { label }
                    },
                    shape = QintarShapes.Pill,
                    color = if (isSelected) colors.primaryContainer else colors.surface,
                    contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    border = BorderStroke(Sizes.hairline, if (isSelected) colors.primaryContainer else colors.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, style = QintarType.Label, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun InputComponentsPreview() {
    QintarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
                CountField(3, {})
                WeekdayChips(listOf("S", "M", "T", "W", "T", "F", "S"), setOf(1, 5), {})
            }
        }
    }
}

@Preview(widthDp = 360, locale = "fa")
@Composable
private fun InputComponentsDarkRtlPreview() {
    QintarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
                CountField(100, {})
                WeekdayChips(listOf("ش", "ی", "د", "س", "چ", "پ", "ج"), setOf(6), {})
            }
        }
    }
}

@Preview
@Composable
private fun SetCountDialogPreview() {
    QintarTheme(darkTheme = false) { SetCountDialog(2, {}, {}, supportingText = "Al-Baqarah 255") }
}
