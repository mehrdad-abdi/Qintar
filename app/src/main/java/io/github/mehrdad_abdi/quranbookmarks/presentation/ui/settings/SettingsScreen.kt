package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.mehrdad_abdi.quranbookmarks.R
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppLanguage
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.RtlIcons
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppTheme
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ThemeColor
import io.github.mehrdad_abdi.quranbookmarks.util.LocaleHelper
import androidx.compose.foundation.layout.heightIn
import android.app.Activity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReciterSelection: () -> Unit,
    onNavigateToBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(RtlIcons.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Audio Settings Section
            SettingsSectionHeader(stringResource(R.string.settings_audio))

            SettingsItem(
                title = stringResource(R.string.settings_reciter),
                subtitle = uiState.availableReciters.find { it.identifier == uiState.settings.reciterEdition }?.name
                    ?: uiState.settings.reciterEdition,
                onClick = onNavigateToReciterSelection
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            // Notification Settings Section
            SettingsSectionHeader(stringResource(R.string.settings_notifications))

            SettingsSwitchItem(
                title = stringResource(R.string.settings_daily_reminder),
                subtitle = stringResource(R.string.settings_daily_reminder_desc),
                checked = uiState.settings.notificationSettings.enabled,
                onCheckedChange = { viewModel.updateNotificationEnabled(it) }
            )

            if (uiState.settings.notificationSettings.enabled) {
                SettingsItem(
                    title = stringResource(R.string.settings_reminder_time),
                    subtitle = uiState.settings.notificationSettings.time,
                    onClick = { /* TODO: Implement time picker */ }
                )
            }

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            // Theme Settings Section
            SettingsSectionHeader(stringResource(R.string.settings_appearance))

            SettingsItem(
                title = stringResource(R.string.settings_theme),
                subtitle = when (uiState.settings.theme) {
                    AppTheme.LIGHT -> stringResource(R.string.settings_theme_light)
                    AppTheme.DARK -> stringResource(R.string.settings_theme_dark)
                    AppTheme.SYSTEM -> stringResource(R.string.settings_theme_system)
                },
                onClick = { showThemeDialog = true }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsItem(
                title = stringResource(R.string.settings_primary_color),
                subtitle = ThemeColor.entries.find { it.hex == uiState.settings.primaryColorHex }?.displayName
                    ?: "Custom",
                onClick = { showColorDialog = true }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsItem(
                title = stringResource(R.string.settings_language),
                subtitle = uiState.settings.language.nativeName,
                onClick = { showLanguageDialog = true }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            // Data Management Section
            SettingsSectionHeader(stringResource(R.string.settings_data))

            SettingsItem(
                title = stringResource(R.string.settings_backup_restore),
                subtitle = stringResource(R.string.settings_backup_restore_desc),
                onClick = onNavigateToBackup
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Theme Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.dialog_select_theme)) },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateTheme(theme)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.settings.theme == theme,
                                onClick = {
                                    viewModel.updateTheme(theme)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = when (theme) {
                                    AppTheme.LIGHT -> stringResource(R.string.settings_theme_light)
                                    AppTheme.DARK -> stringResource(R.string.settings_theme_dark)
                                    AppTheme.SYSTEM -> stringResource(R.string.settings_theme_system)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }


    // Color Selection Dialog
    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text(stringResource(R.string.dialog_select_color)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ThemeColor.entries.forEach { themeColor ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updatePrimaryColor(themeColor.hex)
                                    showColorDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Color preview circle
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(themeColor.hex)))
                                    .border(
                                        width = if (uiState.settings.primaryColorHex == themeColor.hex) 3.dp else 1.dp,
                                        color = if (uiState.settings.primaryColorHex == themeColor.hex)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (uiState.settings.primaryColorHex == themeColor.hex) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = themeColor.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.dialog_select_language)) },
            text = {
                Column {
                    AppLanguage.values().forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateLanguage(language)
                                    showLanguageDialog = false
                                    // Restart activity to apply language change
                                    activity?.let { act -> LocaleHelper.restartActivity(act) }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.settings.language == language,
                                onClick = {
                                    viewModel.updateLanguage(language)
                                    showLanguageDialog = false
                                    // Restart activity to apply language change
                                    activity?.let { act -> LocaleHelper.restartActivity(act) }
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = language.nativeName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Error Snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
