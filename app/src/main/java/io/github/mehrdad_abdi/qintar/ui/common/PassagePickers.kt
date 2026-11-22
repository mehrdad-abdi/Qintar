package io.github.mehrdad_abdi.qintar.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber

/*
 * Pickers for a position in the Quran, shared by the bookmark editor and any screen that asks for one:
 *  - [SurahPicker]: a field that opens a searchable list of the 114 surahs;
 *  - [AyahNumberField], [PageField], [JuzField]: number fields with the reason a number was refused.
 * They hold no rules of their own; the view model that owns the form validates and passes [error].
 */

/** The number typed in [text], whatever digits it uses (Latin, Persian, Arabic-Indic); null when empty or not a number. */
fun parseWholeNumber(text: String): Int? {
    val digits = text.trim().map { Character.digit(it, 10) }
    if (digits.isEmpty() || digits.any { it < 0 } || digits.size > 6) return null
    return digits.fold(0) { acc, d -> acc * 10 + d }
}

/** A field showing the chosen surah ("2 · Al-Baqarah") that opens the list when tapped. */
@Composable
fun SurahPicker(
    surahs: List<SurahInfo>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.bm_surah),
) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val current = surahs.firstOrNull { it.number == selected }
    Surface(
        onClick = { open = true },
        modifier = modifier.heightIn(min = 56.dp),
        shape = QintarShapes.Inner,
        color = colors.surface,
        border = BorderStroke(Sizes.hairline, colors.outline),
    ) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = QintarType.Caption, color = colors.onSurfaceVariant)
                if (current != null) {
                    Text(
                        stringResource(R.string.bm_surah_entry, formatNumber(current.number), surahName(current)),
                        style = QintarType.BodyStrong,
                        color = colors.onSurface,
                        maxLines = 1,
                    )
                }
            }
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = colors.onSurfaceVariant)
        }
    }
    if (open) {
        SurahListDialog(surahs, onSelect = { open = false; onSelect(it) }, onDismiss = { open = false })
    }
}

@Composable
private fun SurahListDialog(surahs: List<SurahInfo>, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val shown = remember(filter, surahs) {
        val query = filter.trim()
        val number = parseWholeNumber(query)
        if (query.isEmpty()) {
            surahs
        } else {
            surahs.filter { number == it.number || it.nameLatin.contains(query, ignoreCase = true) || it.nameAr.contains(query) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bm_pick_surah), style = QintarType.Title) },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.bm_surah)) },
                    shape = QintarShapes.Inner,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = Spacing.sm)) {
                    items(shown, key = { it.number }) { surah ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).clickable(role = Role.Button) { onSelect(surah.number) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Text(formatNumber(surah.number), style = QintarType.Numeric, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(surahName(surah), style = QintarType.Body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        shape = QintarShapes.Card,
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/** A whole-number text field with its refusal reason; the digits may be typed in any numeral system. */
@Composable
fun NumberField(
    text: String,
    onTextChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    @StringRes error: Int? = null,
) {
    OutlinedTextField(
        value = text,
        onValueChange = { typed -> onTextChange(typed.filter { Character.digit(it, 10) >= 0 }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = QintarShapes.Inner,
        modifier = modifier,
    )
}

/** The ayah number inside a surah. */
@Composable
fun AyahNumberField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.bm_ayah_number),
    @StringRes error: Int? = null,
) = NumberField(text, onTextChange, label, modifier, error)

/** A mushaf page, 1–604. */
@Composable
fun PageField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes error: Int? = null,
) = NumberField(text, onTextChange, stringResource(R.string.bm_page_number), modifier, error)

/** A juz, 1–30. */
@Composable
fun JuzField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes error: Int? = null,
) = NumberField(text, onTextChange, stringResource(R.string.bm_juz_number), modifier, error)
