package io.github.mehrdad_abdi.qintar.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.WeekdayChips
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Why a schedule form cannot be turned into a [Schedule]. */
enum class ScheduleProblem(@StringRes val message: Int) {
    NO_DAYS(R.string.bm_err_schedule_days),
    SAME_TIMES(R.string.bm_err_window),
}

/**
 * What the schedule editor edits: "no own schedule" ([enabled] false), or weekdays, an optional time
 * window and, for a bookmark, "once". [toSchedule] is only valid when [problem] is null, so the
 * domain's `require`s are never reached from the UI.
 */
data class ScheduleForm(
    val enabled: Boolean = false,
    val days: Set<DayOfWeek> = Schedule.EVERY_DAY,
    val once: Boolean = false,
    val windowOn: Boolean = false,
    val from: LocalTime = LocalTime.of(8, 0),
    val to: LocalTime = LocalTime.of(12, 0),
) {
    val problem: ScheduleProblem?
        get() = when {
            !enabled -> null
            !once && days.isEmpty() -> ScheduleProblem.NO_DAYS
            windowOn && from == to -> ScheduleProblem.SAME_TIMES
            else -> null
        }

    fun toSchedule(): Schedule? {
        if (!enabled || problem != null) return null
        return Schedule(
            days = if (once) Schedule.EVERY_DAY else days,
            window = if (windowOn) TimeWindow(from, to) else null,
            once = once,
        )
    }

    fun toggleDay(day: DayOfWeek) = copy(days = if (day in days) days - day else days + day)

    companion object {
        fun of(schedule: Schedule?): ScheduleForm = if (schedule == null) {
            ScheduleForm()
        } else {
            ScheduleForm(
                enabled = true,
                days = schedule.days,
                once = schedule.once,
                windowOn = schedule.window != null,
                from = schedule.window?.from ?: LocalTime.of(8, 0),
                to = schedule.window?.to ?: LocalTime.of(12, 0),
            )
        }
    }
}

/** The weekdays in the order a week is shown, beginning with [first]. */
fun weekOrder(first: DayOfWeek): List<DayOfWeek> = List(7) { first.plus(it.toLong()) }

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** "08:00" in the app's digits. */
@Composable
fun timeText(time: LocalTime): String = localizeDigits(TimeFormat.format(time))

/** "08:00–12:00" in the app's digits. */
@Composable
fun windowText(window: TimeWindow): String =
    stringResource(R.string.sched_window_range, timeText(window.from), timeText(window.to))

/** What a schedule chip says: "Daily 08:00–12:00", "Fri", "Sat, Mon", "Once". */
@Composable
fun scheduleText(schedule: Schedule): String {
    val locale = LocalConfiguration.current.locales[0]
    val separator = if (locale.language == "fa" || locale.language == "ar") "، " else ", "
    val days = when {
        schedule.once -> stringResource(R.string.sched_once_chip)
        schedule.days.size == 7 -> stringResource(R.string.sched_daily)
        else -> DayOfWeek.entries.filter { it in schedule.days }.joinToString(separator) { it.getDisplayName(TextStyle.SHORT, locale) }
    }
    val window = schedule.window ?: return days
    return stringResource(R.string.sched_days_window, days, windowText(window))
}

/** Test tag of the "choose days" option. */
const val SCHEDULE_OWN_TAG = "schedule-own"

/** Test tag of the "once" switch. */
const val SCHEDULE_ONCE_TAG = "schedule-once"

/** Test tag of the window switch. */
const val SCHEDULE_WINDOW_TAG = "schedule-window"

/**
 * Weekdays and an optional time window, shared by the bookmark and the group editor.
 *
 * @param noneLabel what "no own schedule" means here: "No schedule", or "Same as the group · Daily".
 * @param allowOnce a bookmark can be "until read once"; a group cannot.
 * @param weekDays the weekdays in the order the week-start setting wants them.
 */
@Composable
fun ScheduleEditor(
    form: ScheduleForm,
    onChange: (ScheduleForm) -> Unit,
    weekDays: List<DayOfWeek>,
    noneLabel: String,
    modifier: Modifier = Modifier,
    allowOnce: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.fillMaxWidth()) {
        ChoiceRow(noneLabel, selected = !form.enabled, onClick = { onChange(form.copy(enabled = false)) })
        ChoiceRow(
            stringResource(R.string.sched_own),
            selected = form.enabled,
            onClick = { onChange(form.copy(enabled = true)) },
            modifier = Modifier.testTag(SCHEDULE_OWN_TAG),
        )
        if (!form.enabled) return@Column

        Spacer(Modifier.height(Spacing.sm))
        if (!(allowOnce && form.once)) {
            WeekdayChips(
                labels = weekDays.map { it.getDisplayName(TextStyle.NARROW, locale) },
                selected = weekDays.indices.filter { weekDays[it] in form.days }.toSet(),
                onToggle = { onChange(form.toggleDay(weekDays[it])) },
                contentDescriptions = weekDays.map { it.getDisplayName(TextStyle.FULL, locale) },
            )
        }
        if (allowOnce) {
            SwitchRow(stringResource(R.string.sched_once), form.once, SCHEDULE_ONCE_TAG) { onChange(form.copy(once = it)) }
        }
        SwitchRow(stringResource(R.string.sched_window), form.windowOn, SCHEDULE_WINDOW_TAG) { onChange(form.copy(windowOn = it)) }
        if (form.windowOn) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TimeButton(stringResource(R.string.sched_from), form.from, { onChange(form.copy(from = it)) }, Modifier.weight(1f))
                TimeButton(stringResource(R.string.sched_to), form.to, { onChange(form.copy(to = it)) }, Modifier.weight(1f))
            }
        }
        form.problem?.let {
            Text(stringResource(it.message), style = QintarType.Caption, color = colors.error, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, style = QintarType.Body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = Spacing.md))
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).toggleable(checked, role = Role.Switch, onValueChange = onChange).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = QintarType.Body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A labelled time that opens the clock dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeButton(label: String, time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        modifier = modifier.heightIn(min = Sizes.touchTarget),
        shape = QintarShapes.Inner,
        color = colors.surface,
        border = BorderStroke(Sizes.hairline, colors.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(label, style = QintarType.Caption, color = colors.onSurfaceVariant)
            Text(timeText(time), style = QintarType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = colors.onSurface)
        }
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    onChange(LocalTime.of(state.hour, state.minute))
                }) { Text(stringResource(R.string.sched_time_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
