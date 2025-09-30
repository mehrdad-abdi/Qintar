package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(
    bookmarkId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReadingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(bookmarkId) {
        viewModel.loadBookmarkContent(bookmarkId)
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
                        text = uiState.bookmark?.let { getBookmarkTitle(it) } ?: "Reading",
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
                },
                actions = {
                    // Audio controls
                    if (uiState.verses.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.toggleAudioPlayback() }
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlayingAudio) "Pause" else "Play"
                            )
                        }

                        // Speed control button
                        IconButton(
                            onClick = {
                                val currentSpeed = uiState.playbackSpeed
                                val nextSpeed = getNextSpeed(currentSpeed)
                                viewModel.setPlaybackSpeed(nextSpeed)
                            }
                        ) {
                            Text(
                                text = uiState.playbackSpeed.displayText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleReciterSelection() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Select Reciter"
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading verses...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = uiState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.loadBookmarkContent(bookmarkId) }
                    ) {
                        Text("Retry")
                    }
                }
            }

            uiState.verses.isNotEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Reciter selection dropdown
                    AnimatedVisibility(
                        visible = uiState.showReciterSelection,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        ReciterSelectionCard(
                            reciters = uiState.reciters,
                            selectedReciter = uiState.selectedReciter,
                            onReciterSelected = { reciter ->
                                viewModel.selectReciter(reciter)
                            },
                            onDismiss = { viewModel.toggleReciterSelection() }
                        )
                    }

                    // Bookmark info header
                    uiState.bookmark?.let { bookmark ->
                        BookmarkInfoCard(
                            bookmark = bookmark,
                            verseCount = uiState.verses.size
                        )
                    }

                    // Verses list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.verses,
                            key = { index, _ -> index }
                        ) { index, verse ->
                            VerseCard(
                                verse = verse,
                                verseIndex = index,
                                isPlaying = uiState.currentPlayingVerse == index,
                                onPlayClick = { viewModel.toggleAudioPlayback(index) }
                            )
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No content available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReciterSelectionCard(
    reciters: List<ReciterData>,
    selectedReciter: ReciterData?,
    onReciterSelected: (ReciterData) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Reciter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                reciters.forEach { reciter ->
                    ReciterItem(
                        reciter = reciter,
                        isSelected = selectedReciter?.identifier == reciter.identifier,
                        onClick = { onReciterSelected(reciter) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReciterItem(
    reciter: ReciterData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = reciter.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (reciter.englishName != reciter.name) {
                    Text(
                        text = reciter.englishName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkInfoCard(
    bookmark: io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark,
    verseCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = bookmark.getDisplayText(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (bookmark.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bookmark.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$verseCount verse${if (verseCount != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun VerseCard(
    verse: VerseMetadata,
    verseIndex: Int,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Verse header with play button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = verse.surahNameEn.ifBlank { verse.surahName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Page ${verse.page}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Juz ${kotlin.math.min(((verse.hizbQuarter - 1) / 8) + 1, 30)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (verse.sajda) {
                            Text(
                                text = "Sajda",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onPlayClick
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Verse text
            Text(
                text = verse.text,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 32.sp
                ),
                textAlign = TextAlign.End, // Arabic text alignment
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun getBookmarkTitle(bookmark: io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark): String {
    return when (bookmark.type) {
        BookmarkType.AYAH -> "Verse ${bookmark.startSurah}:${bookmark.startAyah}"
        BookmarkType.RANGE -> "Verses ${bookmark.startSurah}:${bookmark.startAyah}-${bookmark.endAyah}"
        BookmarkType.SURAH -> "Surah ${bookmark.startSurah}"
        BookmarkType.PAGE -> "Page ${bookmark.startAyah}"
    }
}

private fun getNextSpeed(currentSpeed: PlaybackSpeed): PlaybackSpeed {
    val speeds = PlaybackSpeed.values()
    val currentIndex = speeds.indexOf(currentSpeed)
    val nextIndex = (currentIndex + 1) % speeds.size
    return speeds[nextIndex]
}