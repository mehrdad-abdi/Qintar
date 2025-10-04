package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmark

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
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
                        text = "Add Bookmark",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
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
                    label = "Select Surah",
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
                        label = { Text("Ayah Number") },
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
                        label = { Text("Start Ayah") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.ayahError != null,
                        supportingText = uiState.ayahError?.let { { Text(it) } }
                    )

                    OutlinedTextField(
                        value = uiState.endAyahNumber,
                        onValueChange = viewModel::updateEndAyahNumber,
                        label = { Text("End Ayah") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.endAyahError != null,
                        supportingText = uiState.endAyahError?.let { { Text(it) } }
                    )
                }

                BookmarkType.SURAH -> {
                    // For full surah, we don't need ayah numbers
                    Text(
                        text = "Complete Surah will be bookmarked",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                BookmarkType.PAGE -> {
                    OutlinedTextField(
                        value = uiState.ayahNumber,
                        onValueChange = viewModel::updateAyahNumber,
                        label = { Text("Page Number") },
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
                label = { Text("Notes (Optional)") },
                placeholder = { Text("Add personal notes about this bookmark...") },
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
                Text("Create Bookmark")
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
            text = "Bookmark Type",
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

private fun getBookmarkTypeDisplayName(type: BookmarkType): String {
    return when (type) {
        BookmarkType.AYAH -> "Single Ayah"
        BookmarkType.RANGE -> "Ayah Range"
        BookmarkType.SURAH -> "Complete Surah"
        BookmarkType.PAGE -> "Page Reference"
    }
}

private fun getBookmarkTypeDescription(type: BookmarkType): String {
    return when (type) {
        BookmarkType.AYAH -> "Bookmark a specific verse"
        BookmarkType.RANGE -> "Bookmark multiple consecutive verses"
        BookmarkType.SURAH -> "Bookmark an entire chapter"
        BookmarkType.PAGE -> "Bookmark by page number"
    }
}