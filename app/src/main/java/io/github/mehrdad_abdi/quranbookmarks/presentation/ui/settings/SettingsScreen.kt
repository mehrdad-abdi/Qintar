package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppTheme
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ThemeColor
import androidx.compose.foundation.layout.heightIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showReciterDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
            SettingsSectionHeader("Audio")

            SettingsItem(
                title = "Reciter",
                subtitle = uiState.availableReciters.find { it.identifier == uiState.settings.reciterEdition }?.name
                    ?: uiState.settings.reciterEdition,
                onClick = { showReciterDialog = true }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            // Notification Settings Section
            SettingsSectionHeader("Notifications")

            SettingsSwitchItem(
                title = "Daily Reminder",
                subtitle = "Receive daily Quran reading reminders",
                checked = uiState.settings.notificationSettings.enabled,
                onCheckedChange = { viewModel.updateNotificationEnabled(it) }
            )

            if (uiState.settings.notificationSettings.enabled) {
                SettingsItem(
                    title = "Reminder Time",
                    subtitle = uiState.settings.notificationSettings.time,
                    onClick = { /* TODO: Implement time picker */ }
                )
            }

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            // Theme Settings Section
            SettingsSectionHeader("Appearance")

            SettingsItem(
                title = "Theme",
                subtitle = when (uiState.settings.theme) {
                    AppTheme.LIGHT -> "Light"
                    AppTheme.DARK -> "Dark"
                    AppTheme.SYSTEM -> "System Default"
                },
                onClick = { showThemeDialog = true }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsItem(
                title = "Primary Color",
                subtitle = ThemeColor.entries.find { it.hex == uiState.settings.primaryColorHex }?.displayName
                    ?: "Custom",
                onClick = { showColorDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Theme Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Theme") },
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
                                    AppTheme.LIGHT -> "Light"
                                    AppTheme.DARK -> "Dark"
                                    AppTheme.SYSTEM -> "System Default"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reciter Selection Dialog
    if (showReciterDialog) {
        AlertDialog(
            onDismissRequest = { showReciterDialog = false },
            title = { Text("Select Reciter") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    uiState.availableReciters.forEach { reciter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateReciter(reciter.identifier)
                                    showReciterDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.settings.reciterEdition == reciter.identifier,
                                onClick = {
                                    viewModel.updateReciter(reciter.identifier)
                                    showReciterDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = reciter.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReciterDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Color Selection Dialog
    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("Select Primary Color") },
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
                    Text("Cancel")
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
