package io.github.mehrdad_abdi.qintar.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import android.text.format.Formatter
import androidx.compose.ui.platform.LocalContext
import io.github.mehrdad_abdi.qintar.feature.player.PlayerBarViewModel
import io.github.mehrdad_abdi.qintar.feature.player.speedLabel
import io.github.mehrdad_abdi.qintar.playback.Reciters
import kotlin.math.abs
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.BuildConfig
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.data.backup.BackupDecodeResult
import io.github.mehrdad_abdi.qintar.domain.backup.BackupProblem
import io.github.mehrdad_abdi.qintar.domain.backup.ImportMode
import io.github.mehrdad_abdi.qintar.ui.common.dateText
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.model.WeekStart
import io.github.mehrdad_abdi.qintar.ui.common.ScreenTopBar
import io.github.mehrdad_abdi.qintar.ui.common.TimeButton
import io.github.mehrdad_abdi.qintar.ui.common.number
import io.github.mehrdad_abdi.qintar.ui.common.rememberNotificationGate
import io.github.mehrdad_abdi.qintar.ui.common.timeText
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankLadder
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.designsystem.quranTextStyle
import io.github.mehrdad_abdi.qintar.ui.designsystem.rankName
import io.github.mehrdad_abdi.qintar.ui.navigation.LicencesRoute
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Test tags of the controls, so that tests do not depend on wording. */
object SettingsTags {
    const val CONTENT = "settings-content"
    const val LANGUAGE = "setting-language"
    const val THEME = "setting-theme"
    const val PURE_BLACK = "setting-pure-black"
    const val LATIN_NUMERALS = "setting-latin-numerals"
    const val FONT_SIZE = "setting-font-size"
    const val BREAK_AT_PAUSE = "setting-break-pause"
    const val KEEP_SCREEN_ON = "setting-keep-screen-on"
    const val GOAL_PREFIX = "setting-goal-"
    const val GOAL_NONE = "setting-goal-none"
    const val DAY_START = "setting-day-start"
    const val WEEK_START = "setting-week-start"
    const val REMINDER = "setting-reminder"
    const val STREAK_REMINDER = "setting-streak-reminder"
    const val RECITER = "setting-reciter"
    const val AUDIO_QUALITY = "setting-audio-quality"
    const val PLAYBACK_SPEED = "setting-playback-speed"
    const val COUNT_LISTENED = "setting-count-listened"
    const val STOP_BEFORE_SAJDA = "setting-stop-before-sajda"
    const val WIFI_ONLY = "setting-wifi-only"
    const val CLEAR_AUDIO = "setting-clear-audio"
    const val EXPORT = "setting-export"
    const val IMPORT = "setting-import"
    const val MIGRATION_RETRY = "setting-migration-retry"
    const val REMOVE_V1 = "setting-remove-v1"
    const val REMOVE_V1_CONFIRM = "setting-remove-v1-confirm"
    const val LICENCES = "setting-licences"
    const val TANZIL_LINK = "setting-tanzil-link"
}

/**
 * Settings (§5.9). Every control writes straight to the preferences.
 *
 * "Calendar system" is deliberately absent: the preference exists but no screen shows Persian or
 * Hijri dates yet, so a control for it would do nothing.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (route: Any) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTopBar(stringResource(R.string.screen_settings), onBack)
        if (state.loading) return@Column
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
                .testTag(SettingsTags.CONTENT),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections),
        ) {
            AppearanceSection(state.prefs, viewModel)
            ReadingSection(state.prefs, viewModel)
            GoalSection(state.prefs, viewModel)
            RemindersSection(state.prefs, viewModel)
            AudioSection(state.prefs, state.audioBytes, viewModel)
            DataSection(state.prefs.legacyMigrationState, state.retrying, state.legacyDataPresent, viewModel, backupViewModel)
            AboutSection(onOpenLicences = { onNavigate(LicencesRoute) })
        }
    }
    BackupDialogs(backupViewModel)
}

// ---- building blocks ------------------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(title)
        QCard(contentPadding = PaddingValues(horizontal = Spacing.card, vertical = Spacing.xs), content = content)
    }
}

@Composable
private fun SwitchSetting(title: String, checked: Boolean, tag: String, summary: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Spacing.sm)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = QintarType.Body, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) Text(summary, style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = Spacing.md))
    }
}

/** A row showing the current choice; tapping opens the list of options. */
@Composable
private fun <T> ChoiceSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    tag: String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clickable(role = Role.Button) { open = true }
            .padding(vertical = Spacing.sm)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = QintarType.Body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(label(selected), style = QintarType.Label, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Spacing.md))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title, style = QintarType.Title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Sizes.touchTarget)
                                .selectable(option == selected, role = Role.RadioButton) {
                                    onSelect(option)
                                    open = false
                                }
                                .testTag("$tag-option-${option.toString()}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Text(label(option), style = QintarType.Body, modifier = Modifier.padding(start = Spacing.md))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

// ---- sections -------------------------------------------------------------------------------------

@Composable
private fun AppearanceSection(prefs: UserPrefs, vm: SettingsViewModel) = Section(stringResource(R.string.settings_section_appearance)) {
    ChoiceSetting(
        title = stringResource(R.string.settings_language),
        options = AppLanguage.entries,
        selected = prefs.language,
        label = {
            when (it) {
                AppLanguage.SYSTEM -> stringResource(R.string.settings_system_default)
                AppLanguage.ENGLISH -> stringResource(R.string.language_name_en)
                AppLanguage.ARABIC -> stringResource(R.string.language_name_ar)
                AppLanguage.PERSIAN -> stringResource(R.string.language_name_fa)
            }
        },
        tag = SettingsTags.LANGUAGE,
        onSelect = vm::setLanguage,
    )
    RowDivider()
    ChoiceSetting(
        title = stringResource(R.string.settings_theme),
        options = ThemeMode.entries,
        selected = prefs.theme,
        label = {
            when (it) {
                ThemeMode.SYSTEM -> stringResource(R.string.settings_system_default)
                ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
            }
        },
        tag = SettingsTags.THEME,
        onSelect = vm::setTheme,
    )
    RowDivider()
    SwitchSetting(
        stringResource(R.string.settings_pure_black),
        prefs.pureBlack,
        SettingsTags.PURE_BLACK,
        summary = stringResource(R.string.settings_pure_black_summary),
        onChange = vm::setPureBlack,
    )
    RowDivider()
    SwitchSetting(
        stringResource(R.string.settings_latin_numerals),
        prefs.latinNumerals,
        SettingsTags.LATIN_NUMERALS,
        summary = stringResource(R.string.settings_latin_numerals_summary),
        onChange = vm::setLatinNumerals,
    )
}

@Composable
private fun ReadingSection(prefs: UserPrefs, vm: SettingsViewModel) = Section(stringResource(R.string.settings_section_reading)) {
    Column(Modifier.padding(vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_font_size), style = QintarType.Body, modifier = Modifier.weight(1f))
            Text(formatNumber(prefs.quranFontSp), style = QintarType.Numeric, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = prefs.quranFontSp.toFloat(),
            onValueChange = { vm.setFontSp(it.toInt()) },
            valueRange = UserPrefs.MIN_FONT_SP.toFloat()..UserPrefs.MAX_FONT_SP.toFloat(),
            steps = UserPrefs.MAX_FONT_SP - UserPrefs.MIN_FONT_SP - 1,
            modifier = Modifier.testTag(SettingsTags.FONT_SIZE),
        )
        // The live sample: the reader's own style, so what is chosen here is what is read there.
        Text(
            stringResource(R.string.settings_font_sample),
            style = quranTextStyle(prefs.quranFontSp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    RowDivider()
    SwitchSetting(
        stringResource(R.string.settings_break_pause),
        prefs.breakAtPauseMarks,
        SettingsTags.BREAK_AT_PAUSE,
        summary = stringResource(R.string.settings_break_pause_summary),
        onChange = vm::setBreakAtPauseMarks,
    )
    RowDivider()
    SwitchSetting(stringResource(R.string.settings_keep_screen_on), prefs.keepScreenOn, SettingsTags.KEEP_SCREEN_ON, onChange = vm::setKeepScreenOn)
}

@Composable
private fun GoalSection(prefs: UserPrefs, vm: SettingsViewModel) = Section(stringResource(R.string.settings_section_goal)) {
    Column(Modifier.padding(vertical = Spacing.sm)) {
        Text(stringResource(R.string.settings_goal), style = QintarType.Body)
        Text(stringResource(R.string.settings_goal_summary), style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.md))
        val goal = prefs.goalRank
        RankLadder(currentRank = goal?.number ?: 0, goalRank = goal?.number, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.xs))
    }
    GoalOption(null, selected = prefs.goalRank == null, onSelect = { vm.setGoal(null) })
    Rank.ladder.forEach { rank -> GoalOption(rank, selected = prefs.goalRank == rank, onSelect = { vm.setGoal(rank) }) }
    RowDivider()

    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.padding(vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_day_start), style = QintarType.Body, modifier = Modifier.weight(1f))
            Text(timeText(LocalTime.ofSecondOfDay(prefs.dayStartMinutes * 60L)), style = QintarType.Numeric, color = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(R.string.settings_day_start_summary), style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = prefs.dayStartMinutes / DAY_START_STEP.toFloat(),
            onValueChange = { vm.setDayStartMinutes(it.toInt() * DAY_START_STEP) },
            valueRange = 0f..(UserPrefs.MAX_DAY_START_MINUTES / DAY_START_STEP).toFloat(),
            steps = UserPrefs.MAX_DAY_START_MINUTES / DAY_START_STEP - 1,
            modifier = Modifier.testTag(SettingsTags.DAY_START),
        )
    }
    RowDivider()
    ChoiceSetting(
        title = stringResource(R.string.settings_week_start),
        options = WeekStart.entries,
        selected = prefs.weekStart,
        label = {
            when (it) {
                WeekStart.SYSTEM -> stringResource(R.string.settings_system_default)
                WeekStart.SATURDAY -> DayOfWeek.SATURDAY.getDisplayName(TextStyle.FULL, locale)
                WeekStart.SUNDAY -> DayOfWeek.SUNDAY.getDisplayName(TextStyle.FULL, locale)
                WeekStart.MONDAY -> DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, locale)
            }
        },
        tag = SettingsTags.WEEK_START,
        onSelect = vm::setWeekStart,
    )
}

private const val DAY_START_STEP = 30

@Composable
internal fun GoalOption(rank: Rank?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(if (rank == null) SettingsTags.GOAL_NONE else SettingsTags.GOAL_PREFIX + rank.name),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = Spacing.md).weight(1f)) {
            if (rank == null) {
                Text(stringResource(R.string.settings_goal_none), style = QintarType.Body)
            } else {
                Text(rankName(rank.number), style = QintarType.Body)
                Text(stringResource(R.string.settings_goal_threshold, formatNumber(rank.threshold)), style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RemindersSection(prefs: UserPrefs, vm: SettingsViewModel) = Section(stringResource(R.string.settings_section_reminders)) {
    var blocked by remember { mutableStateOf(false) }
    val dailyGate = rememberNotificationGate { granted ->
        blocked = !granted
        if (granted) vm.setReminderEnabled(true)
    }
    val streakGate = rememberNotificationGate { granted ->
        blocked = !granted
        if (granted) vm.setStreakReminderEnabled(true)
    }
    SwitchSetting(
        stringResource(R.string.settings_reminder_daily),
        prefs.reminderEnabled,
        SettingsTags.REMINDER,
        summary = stringResource(R.string.settings_reminder_daily_summary),
    ) { on -> if (on) dailyGate() else vm.setReminderEnabled(false) }
    if (prefs.reminderEnabled) {
        TimeButton(stringResource(R.string.settings_reminder_time), prefs.reminderTime, vm::setReminderTime, Modifier.fillMaxWidth().padding(bottom = Spacing.sm))
    }
    RowDivider()
    SwitchSetting(
        stringResource(R.string.settings_reminder_streak),
        prefs.streakReminderEnabled,
        SettingsTags.STREAK_REMINDER,
        summary = stringResource(R.string.settings_reminder_streak_summary, formatNumber(10)),
    ) { on -> if (on) streakGate() else vm.setStreakReminderEnabled(false) }
    if (prefs.streakReminderEnabled) {
        TimeButton(stringResource(R.string.settings_reminder_time), prefs.streakReminderTime, vm::setStreakReminderTime, Modifier.fillMaxWidth().padding(bottom = Spacing.sm))
    }
    if (blocked) {
        Text(
            stringResource(R.string.settings_notifications_blocked),
            style = QintarType.Caption,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
    }
}

/** Audio (§5.9, §7.8): who recites, how it sounds, whether listening counts, and what is stored. */
@Composable
private fun AudioSection(prefs: UserPrefs, audioBytes: Long?, vm: SettingsViewModel) = Section(stringResource(R.string.settings_section_audio)) {
    val reciter = Reciters.of(prefs.reciterId)
    ChoiceSetting(
        title = stringResource(R.string.settings_reciter),
        options = Reciters.all.map { it.id },
        selected = reciter.id,
        label = { stringResource(Reciters.of(it).name) },
        tag = SettingsTags.RECITER,
        onSelect = vm::setReciter,
    )
    RowDivider()
    ChoiceSetting(
        title = stringResource(R.string.settings_audio_quality),
        options = reciter.bitrates,
        selected = reciter.bitrateFor(prefs.audioQuality),
        label = { stringResource(R.string.settings_audio_quality_value, formatNumber(it)) },
        tag = SettingsTags.AUDIO_QUALITY,
        onSelect = vm::setAudioQuality,
    )
    RowDivider()
    ChoiceSetting(
        title = stringResource(R.string.settings_playback_speed),
        options = PlayerBarViewModel.SPEEDS,
        selected = PlayerBarViewModel.SPEEDS.minBy { abs(it - prefs.playbackSpeed) },
        label = { speedLabel(it) },
        tag = SettingsTags.PLAYBACK_SPEED,
        onSelect = vm::setPlaybackSpeed,
    )
    RowDivider()
    SwitchSetting(
        stringResource(R.string.settings_count_listened),
        prefs.countListened,
        SettingsTags.COUNT_LISTENED,
        summary = stringResource(R.string.settings_count_listened_summary),
        onChange = vm::setCountListened,
    )
    RowDivider()
    SwitchSetting(
        stringResource(R.string.settings_stop_before_sajda),
        prefs.stopBeforeSajda,
        SettingsTags.STOP_BEFORE_SAJDA,
        summary = stringResource(R.string.settings_stop_before_sajda_summary),
        onChange = vm::setStopBeforeSajda,
    )
    RowDivider()
    SwitchSetting(stringResource(R.string.settings_wifi_only), prefs.wifiOnlyDownloads, SettingsTags.WIFI_ONLY, onChange = vm::setWifiOnlyDownloads)
    RowDivider()
    val context = LocalContext.current
    ActionRow(
        title = stringResource(R.string.settings_clear_audio),
        summary = when {
            audioBytes == null -> ""
            audioBytes <= 0L -> stringResource(R.string.settings_clear_audio_empty)
            else -> stringResource(R.string.settings_clear_audio_size, Formatter.formatShortFileSize(context, audioBytes))
        },
        tag = SettingsTags.CLEAR_AUDIO,
        onClick = vm::clearAudio,
    )
}

@Composable
private fun DataSection(
    migration: LegacyMigrationState,
    retrying: Boolean,
    legacyDataPresent: Boolean,
    vm: SettingsViewModel,
    backup: BackupViewModel,
) = Section(stringResource(R.string.settings_section_data)) {
    val context = LocalContext.current
    val khatmName = stringResource(R.string.khatm_migrated_name)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            backup.export { text ->
                withContext(Dispatchers.IO) {
                    val out = checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "no output stream" }
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            backup.chooseFile(khatmName) {
                withContext(Dispatchers.IO) {
                    val input = checkNotNull(context.contentResolver.openInputStream(uri)) { "no input stream" }
                    input.use { String(it.readBytes(), Charsets.UTF_8) }
                }
            }
        }
    }
    ActionRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_summary), SettingsTags.EXPORT) {
        exporter.launch(backup.suggestedFileName())
    }
    RowDivider()
    ActionRow(stringResource(R.string.settings_import), stringResource(R.string.settings_import_summary), SettingsTags.IMPORT) {
        importer.launch(arrayOf("*/*"))
    }
    RowDivider()
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(stringResource(R.string.settings_migration), style = QintarType.Body)
        Text(
            stringResource(
                when (migration) {
                    LegacyMigrationState.MIGRATED, LegacyMigrationState.ACKNOWLEDGED -> R.string.settings_migration_done
                    LegacyMigrationState.FAILED -> R.string.settings_migration_failed
                    LegacyMigrationState.NOT_NEEDED -> R.string.settings_migration_none
                    LegacyMigrationState.PENDING, LegacyMigrationState.IN_PROGRESS -> R.string.settings_migration_pending
                },
            ),
            style = QintarType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (migration == LegacyMigrationState.FAILED) {
            TextButton(onClick = { vm.retryMigration(khatmName) }, enabled = !retrying, modifier = Modifier.testTag(SettingsTags.MIGRATION_RETRY)) {
                Text(stringResource(R.string.notice_retry), style = QintarType.Label)
            }
        }
    }
    // v1's own database stays as a fallback after the carry-over, until the user lets it go.
    val carried = migration == LegacyMigrationState.MIGRATED || migration == LegacyMigrationState.ACKNOWLEDGED
    if (carried && legacyDataPresent) {
        var confirming by remember { mutableStateOf(false) }
        RowDivider()
        ActionRow(
            stringResource(R.string.settings_remove_v1),
            stringResource(R.string.settings_remove_v1_summary),
            SettingsTags.REMOVE_V1,
        ) { confirming = true }
        if (confirming) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(stringResource(R.string.settings_remove_v1_confirm_title), style = QintarType.Title) },
                text = { Text(stringResource(R.string.settings_remove_v1_confirm_body), style = QintarType.Body) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirming = false
                            vm.removeLegacyData()
                        },
                        modifier = Modifier.testTag(SettingsTags.REMOVE_V1_CONFIRM),
                    ) { Text(stringResource(R.string.settings_remove_v1_action), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable
private fun ActionRow(title: String, summary: String, tag: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm)
            .testTag(tag),
    ) {
        Text(title, style = QintarType.Body, color = MaterialTheme.colorScheme.onSurface)
        Text(summary, style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutSection(onOpenLicences: () -> Unit) = Section(stringResource(R.string.settings_section_about)) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(stringResource(R.string.app_name), style = QintarType.BodyStrong)
        Text(
            stringResource(R.string.settings_version, localizeDigits(BuildConfig.VERSION_NAME)),
            style = QintarType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    RowDivider()
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(stringResource(R.string.settings_privacy_title), style = QintarType.Body)
        Text(stringResource(R.string.settings_privacy_body), style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    RowDivider()
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(stringResource(R.string.settings_tanzil_title), style = QintarType.Body)
        Text(stringResource(R.string.settings_tanzil_body), style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { uriHandler.openUri(TANZIL_URL) }, modifier = Modifier.testTag(SettingsTags.TANZIL_LINK)) {
            Text(stringResource(R.string.settings_tanzil_link), style = QintarType.Label)
        }
    }
    RowDivider()
    ActionRow(stringResource(R.string.settings_licences), stringResource(R.string.settings_licences_summary), SettingsTags.LICENCES, onOpenLicences)
}

const val TANZIL_URL = "https://tanzil.net"

// ---- backup dialogs -------------------------------------------------------------------------------

/** Tags of the import flow. */
object BackupTags {
    const val PREVIEW = "backup-preview"
    const val REPLACE = "backup-replace"
    const val CONFIRM_REPLACE = "backup-confirm-replace"
    const val MERGE = "backup-merge"
    const val RESULT = "backup-result"
    const val ERROR = "backup-error"
    const val OK = "backup-ok"
}

@Composable
fun BackupDialogs(viewModel: BackupViewModel) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    BackupDialogs(phase, onConfirm = viewModel::confirm, onDismiss = viewModel::dismiss)
}

@Composable
fun BackupDialogs(phase: BackupPhase, onConfirm: (ImportMode) -> Unit, onDismiss: () -> Unit) {
    when (phase) {
        BackupPhase.Idle -> Unit
        BackupPhase.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = { Text(stringResource(R.string.backup_working), style = QintarType.Body) },
        )
        BackupPhase.Exported -> MessageDialog(stringResource(R.string.backup_exported), BackupTags.RESULT, onDismiss)
        is BackupPhase.Failed -> MessageDialog(backupErrorText(phase.error), BackupTags.ERROR, onDismiss)
        is BackupPhase.Imported -> MessageDialog(importedText(phase), BackupTags.RESULT, onDismiss)
        is BackupPhase.Preview -> PreviewDialog(phase, onConfirm, onDismiss)
    }
}

@Composable
private fun MessageDialog(text: String, tag: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text, style = QintarType.Body, modifier = Modifier.testTag(tag)) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag(BackupTags.OK)) { Text(stringResource(R.string.settings_ok)) } },
    )
}

@Composable
private fun PreviewDialog(
    phase: BackupPhase.Preview,
    onConfirm: (ImportMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val summary = phase.preview.summary
    var askReplace by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_preview_title), style = QintarType.Title) },
        text = {
            Column(Modifier.testTag(BackupTags.PREVIEW).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (phase.fromV1) Text(stringResource(R.string.backup_v1_notice), style = QintarType.Label, color = colors.primary)
                val first = summary.firstDay
                val last = summary.lastDay
                if (first != null && last != null) {
                    Text(
                        stringResource(R.string.backup_range, dateText(first), dateText(last)),
                        style = QintarType.Body,
                    )
                }
                Text(stringResource(R.string.backup_count_ayahs, formatNumber(summary.totalAyahs)), style = QintarType.Body)
                Text(stringResource(R.string.backup_count_bookmarks, formatNumber(summary.bookmarks), formatNumber(summary.groups)), style = QintarType.Body)
                Text(stringResource(R.string.backup_count_khatms, formatNumber(summary.khatms)), style = QintarType.Body)
                phase.legacy?.let { report ->
                    val skipped = report.skippedAyahIds + report.skippedBookmarks + report.skippedDays
                    if (skipped > 0) Text(pluralStringResource(R.plurals.notice_skipped, skipped, formatNumber(skipped)), style = QintarType.Caption)
                }
                Spacer(Modifier.height(Spacing.sm))
                if (phase.fromV1) {
                    Text(stringResource(R.string.backup_merge_summary), style = QintarType.Caption, color = colors.onSurfaceVariant)
                } else if (askReplace) {
                    Text(stringResource(R.string.backup_replace_warning), style = QintarType.BodyStrong, color = colors.error)
                } else {
                    Text(stringResource(R.string.backup_merge_summary), style = QintarType.Caption, color = colors.onSurfaceVariant)
                    Text(stringResource(R.string.backup_replace_summary), style = QintarType.Caption, color = colors.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (askReplace) {
                    TextButton(onClick = { onConfirm(ImportMode.REPLACE) }, modifier = Modifier.testTag(BackupTags.CONFIRM_REPLACE)) {
                        Text(stringResource(R.string.backup_replace_confirm), color = colors.error)
                    }
                } else {
                    TextButton(onClick = { onConfirm(ImportMode.MERGE) }, modifier = Modifier.testTag(BackupTags.MERGE)) {
                        Text(stringResource(R.string.backup_mode_merge))
                    }
                    if (!phase.fromV1) {
                        TextButton(onClick = { askReplace = true }, modifier = Modifier.testTag(BackupTags.REPLACE)) {
                            Text(stringResource(R.string.backup_mode_replace), color = colors.error)
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun importedText(phase: BackupPhase.Imported): String {
    val r = phase.result
    val added = stringResource(
        R.string.backup_done_added,
        formatNumber(r.eventsAdded),
        formatNumber(r.talliesAdded),
        formatNumber(r.bookmarksAdded),
        formatNumber(r.groupsAdded),
        formatNumber(r.khatmsAdded),
    )
    val skipped = r.eventsSkipped + r.talliesSkipped + r.itemsSkipped
    return if (skipped > 0) added + "\n" + stringResource(R.string.backup_done_skipped, formatNumber(skipped)) else added
}

@Composable
private fun backupErrorText(error: BackupError): String = when (error) {
    BackupError.Unreadable -> stringResource(R.string.backup_error_unreadable)
    BackupError.ExportFailed -> stringResource(R.string.backup_error_export)
    BackupError.ImportFailed -> stringResource(R.string.backup_error_import)
    is BackupError.Invalid -> when (error.reason) {
        BackupDecodeResult.Invalid.Reason.NOT_JSON -> stringResource(R.string.backup_error_not_json)
        BackupDecodeResult.Invalid.Reason.NOT_A_BACKUP -> stringResource(R.string.backup_error_not_a_backup)
        BackupDecodeResult.Invalid.Reason.NEWER_VERSION ->
            stringResource(R.string.backup_error_newer, localizeDigits(error.detail))
        BackupDecodeResult.Invalid.Reason.MALFORMED -> stringResource(R.string.backup_error_malformed, error.detail)
    }
    is BackupError.Rejected -> error.problems.map { problemText(it) }.joinToString("\n")
}

@Composable
private fun problemText(problem: BackupProblem): String = when (problem) {
    is BackupProblem.UnsupportedVersion -> stringResource(R.string.backup_error_newer, formatNumber(problem.version))
    is BackupProblem.FutureDays ->
        stringResource(R.string.backup_problem_future, formatNumber(problem.records), dateText(problem.latest))
    is BackupProblem.UnknownGroup -> stringResource(R.string.backup_problem_unknown_group)
    is BackupProblem.DuplicateId -> stringResource(R.string.backup_problem_duplicate)
    is BackupProblem.InvalidTally -> stringResource(R.string.backup_problem_tally)
}
