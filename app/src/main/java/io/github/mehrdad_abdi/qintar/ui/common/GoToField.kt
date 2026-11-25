package io.github.mehrdad_abdi.qintar.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.service.GoToResult
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing

/** Why a "go to" input was refused: the parser's reasons, plus "outside what this reader holds". */
enum class GoToProblem(@StringRes val message: Int) {
    EMPTY(R.string.goto_error_empty),
    UNRECOGNIZED(R.string.goto_error_unrecognized),
    SURAH_OUT_OF_RANGE(R.string.goto_error_surah),
    AYAH_OUT_OF_RANGE(R.string.goto_error_ayah),
    PAGE_OUT_OF_RANGE(R.string.goto_error_page),
    JUZ_OUT_OF_RANGE(R.string.goto_error_juz),

    /** Understood, but the place is not inside the reader's scope. */
    OUTSIDE_SCOPE(R.string.goto_error_outside);

    companion object {
        fun of(reason: GoToResult.Reason): GoToProblem = when (reason) {
            GoToResult.Reason.EMPTY -> EMPTY
            GoToResult.Reason.UNRECOGNIZED -> UNRECOGNIZED
            GoToResult.Reason.SURAH_OUT_OF_RANGE -> SURAH_OUT_OF_RANGE
            GoToResult.Reason.AYAH_OUT_OF_RANGE -> AYAH_OUT_OF_RANGE
            GoToResult.Reason.PAGE_OUT_OF_RANGE -> PAGE_OUT_OF_RANGE
            GoToResult.Reason.JUZ_OUT_OF_RANGE -> JUZ_OUT_OF_RANGE
        }
    }
}

/**
 * The "go to" text field of §5.3 (`2:255`, `36`, `yasin 12`, `p300`, `j12`) with its Go button, the
 * reason a refused input gives, and the surahs a typed name may be: tapping one puts its name in the
 * field, ready for an ayah number. Shared by the Read tab, the reader's position sheet and the
 * new-khatm form.
 *
 * @param actionLabel null hides the button (the keyboard's Go key still submits).
 */
@Composable
fun GoToField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    problem: GoToProblem? = null,
    label: String = stringResource(R.string.read_goto_label),
    placeholder: String = stringResource(R.string.read_goto_hint),
    actionLabel: String? = stringResource(R.string.read_goto_action),
    suggestions: List<SurahInfo> = emptyList(),
) {
    // The field keeps its cursor; text set from outside (a picked suggestion) puts it at the end, so the
    // ayah number typed next follows the name.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it; if (it.text != value) onValueChange(it.text) },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                isError = problem != null,
                // A tiny hint of the forms, in the user's own script; the error takes its place.
                supportingText = { Text(stringResource(problem?.message ?: R.string.goto_help)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                shape = QintarShapes.Inner,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null) {
                Button(onClick = onSubmit, modifier = Modifier.padding(top = Spacing.xs).heightIn(min = Sizes.touchTarget)) {
                    Text(actionLabel, style = QintarType.Label)
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().testTag(GOTO_SUGGESTIONS_TAG)) {
                for (surah in suggestions.take(MAX_SUGGESTIONS)) {
                    val name = surahName(surah)
                    Text(
                        localizeDigits("${surah.number}. ") + name,
                        style = QintarType.Body,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(QintarShapes.Inner)
                            .clickable(role = Role.Button) { onValueChange("$name ") }
                            .heightIn(min = Sizes.touchTarget)
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
            }
        }
    }
}

const val GOTO_SUGGESTIONS_TAG = "goto-suggestions"

/** How many surahs the drop-down under "Go to" lists. */
private const val MAX_SUGGESTIONS = 5
