package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmark

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun EditBookmarkScreen(
    bookmarkId: Long,
    onNavigateBack: () -> Unit,
    onBookmarkUpdated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditBookmarkViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Load bookmark data when screen is first displayed
    LaunchedEffect(bookmarkId) {
        viewModel.loadBookmark(bookmarkId)
    }

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
                        text = "Edit Bookmark",
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
        if (uiState.isLoadingBookmark) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bookmark Type Selection
                EditBookmarkTypeSelector(
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

                // Preview Card
                EditBookmarkPreviewCard(
                    bookmarkType = uiState.bookmarkType,
                    selectedSurah = uiState.selectedSurah,
                    ayahNumber = uiState.ayahNumber,
                    endAyahNumber = uiState.endAyahNumber,
                    description = uiState.description
                )

                Spacer(modifier = Modifier.weight(1f))

                // Update Button
                Button(
                    onClick = { viewModel.updateBookmark(onBookmarkUpdated) },
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
                    Text("Update Bookmark")
                }
            }
        }
    }
}

@Composable
private fun EditBookmarkTypeSelector(
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
                            text = getEditBookmarkTypeDisplayName(type),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = getEditBookmarkTypeDescription(type),
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
private fun EditBookmarkPreviewCard(
    bookmarkType: BookmarkType,
    selectedSurah: io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah?,
    ayahNumber: String,
    endAyahNumber: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = generateEditPreviewText(bookmarkType, selectedSurah, ayahNumber, endAyahNumber),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getEditBookmarkTypeDisplayName(type: BookmarkType): String {
    return when (type) {
        BookmarkType.AYAH -> "Single Ayah"
        BookmarkType.RANGE -> "Ayah Range"
        BookmarkType.SURAH -> "Complete Surah"
        BookmarkType.PAGE -> "Page Reference"
    }
}

private fun getEditBookmarkTypeDescription(type: BookmarkType): String {
    return when (type) {
        BookmarkType.AYAH -> "Bookmark a specific verse"
        BookmarkType.RANGE -> "Bookmark multiple consecutive verses"
        BookmarkType.SURAH -> "Bookmark an entire chapter"
        BookmarkType.PAGE -> "Bookmark by page number"
    }
}

private fun generateEditPreviewText(
    type: BookmarkType,
    selectedSurah: io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah?,
    ayahNumber: String,
    endAyahNumber: String
): String {
    return when (type) {
        BookmarkType.PAGE -> {
            val pageNumber = ayahNumber.ifBlank { "?" }
            "Page $pageNumber"
        }
        else -> {
            val surahDisplay = selectedSurah?.let { "${it.number} ${it.englishName}" } ?: "Select Surah"
            val ayah = ayahNumber.ifBlank { "?" }
            val endAyah = endAyahNumber.ifBlank { "?" }

            when (type) {
                BookmarkType.AYAH -> "Surah $surahDisplay:$ayah"
                BookmarkType.RANGE -> "Surah $surahDisplay:$ayah-$endAyah"
                BookmarkType.SURAH -> "Surah $surahDisplay"
                BookmarkType.PAGE -> "Page $ayah" // Won't reach here due to outer when
            }
        }
    }
}