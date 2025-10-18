package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmark

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.quranbookmarks.R
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.RtlIcons
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.SurahDropdown
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookmarkScreen(
    groupId: Long,
    onNavigateBack: () -> Unit,
    onBookmarkCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddBookmarkViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Show error snackbar
    val error = uiState.error
    if (error != null) {
        LaunchedEffect(error) {
            // In a real app, you'd show a SnackBar here
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_add_bookmark_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = RtlIcons.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bookmark Type Selection
            BookmarkTypeSelector(
                selectedType = uiState.bookmarkType,
                onTypeSelected = viewModel::updateBookmarkType
            )

            // Surah Selection (not needed for PAGE type)
            if (uiState.bookmarkType != BookmarkType.PAGE) {
                SurahDropdown(
                    selectedSurah = uiState.selectedSurah,
                    surahs = uiState.surahs,
                    onSurahSelected = viewModel::updateSelectedSurah,
                    label = stringResource(R.string.select_surah),
                    isError = uiState.surahError != null,
                    supportingText = uiState.surahError,
                    isLoading = uiState.isLoadingSurahs,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Ayah inputs based on bookmark type
            when (uiState.bookmarkType) {
                BookmarkType.AYAH -> {
                    OutlinedTextField(
                        value = uiState.ayahNumber,
                        onValueChange = viewModel::updateAyahNumber,
                        label = { Text(stringResource(R.string.ayah_number)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.ayahError != null,
                        supportingText = uiState.ayahError?.let { { Text(it) } }
                    )
                }

                BookmarkType.RANGE -> {
                    OutlinedTextField(
                        value = uiState.ayahNumber,
                        onValueChange = viewModel::updateAyahNumber,
                        label = { Text(stringResource(R.string.start_ayah)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.ayahError != null,
                        supportingText = uiState.ayahError?.let { { Text(it) } }
                    )

                    OutlinedTextField(
                        value = uiState.endAyahNumber,
                        onValueChange = viewModel::updateEndAyahNumber,
                        label = { Text(stringResource(R.string.end_ayah)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.endAyahError != null,
                        supportingText = uiState.endAyahError?.let { { Text(it) } }
                    )
                }

                BookmarkType.SURAH -> {
                    // For full surah, we don't need ayah numbers
                    Text(
                        text = stringResource(R.string.complete_surah_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                BookmarkType.PAGE -> {
                    OutlinedTextField(
                        value = uiState.ayahNumber,
                        onValueChange = viewModel::updateAyahNumber,
                        label = { Text(stringResource(R.string.page_number)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.ayahError != null,
                        supportingText = uiState.ayahError?.let { { Text(it) } }
                    )
                }
            }

            // Description Input
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text(stringResource(R.string.notes_optional)) },
                placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.weight(1f))

            // Create Button
            Button(
                onClick = { viewModel.createBookmark(onBookmarkCreated) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.button_create_bookmark))
            }
        }
    }
}

@Composable
private fun BookmarkTypeSelector(
    selectedType: BookmarkType,
    onTypeSelected: (BookmarkType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.bookmark_type),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BookmarkType.values().forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedType == type,
                            onClick = { onTypeSelected(type) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedType == type,
                        onClick = null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = getBookmarkTypeDisplayName(type),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = getBookmarkTypeDescription(type),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getBookmarkTypeDisplayName(type: BookmarkType): String {
    return when (type) {
        BookmarkType.AYAH -> stringResource(R.string.bookmark_type_ayah)
        BookmarkType.RANGE -> stringResource(R.string.bookmark_type_range)
        BookmarkType.SURAH -> stringResource(R.string.bookmark_type_surah)
        BookmarkType.PAGE -> stringResource(R.string.bookmark_type_page)
    }
}

@Composable
private fun getBookmarkTypeDescription(type: BookmarkType): String {
    return when (type) {
        BookmarkType.AYAH -> stringResource(R.string.bookmark_type_ayah_desc)
        BookmarkType.RANGE -> stringResource(R.string.bookmark_type_range_desc)
        BookmarkType.SURAH -> stringResource(R.string.bookmark_type_surah_desc)
        BookmarkType.PAGE -> stringResource(R.string.bookmark_type_page_desc)
    }
}