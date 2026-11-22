package io.github.mehrdad_abdi.qintar.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
    NEEDS_SURAH(R.string.goto_error_needs_surah),
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
            GoToResult.Reason.NEEDS_SURAH -> NEEDS_SURAH
            GoToResult.Reason.SURAH_OUT_OF_RANGE -> SURAH_OUT_OF_RANGE
            GoToResult.Reason.AYAH_OUT_OF_RANGE -> AYAH_OUT_OF_RANGE
            GoToResult.Reason.PAGE_OUT_OF_RANGE -> PAGE_OUT_OF_RANGE
            GoToResult.Reason.JUZ_OUT_OF_RANGE -> JUZ_OUT_OF_RANGE
        }
    }
}

/**
 * The "go to" text field of §5.3 (`2:255`, `255`, `p300`, `j12`) with its Go button and the reason
 * a refused input gives. Shared by the Read tab, the reader's position sheet and the new-khatm form.
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
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
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
}
