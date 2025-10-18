package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.reading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.quranbookmarks.R
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.BookmarkHeaderCard
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.RtlIcons
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.PlaybackSpeedButton
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.VerseCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkReadingScreen(
    bookmarkId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarkReadingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(bookmarkId) {
        viewModel.loadBookmark(bookmarkId)
    }

    // Auto-scroll to currently playing verse
    LaunchedEffect(uiState.currentPlayingIndex) {
        val playingIndex = uiState.currentPlayingIndex
        if (playingIndex != null && uiState.listItems.isNotEmpty()) {
            // Find the item index in the list (accounting for headers)
            val itemIndex = uiState.listItems.indexOfFirst { item ->
                item is ReadingListItem.VerseItem && item.globalIndex == playingIndex
            }
            if (itemIndex >= 0) {
                listState.animateScrollToItem(itemIndex)
            }
        }
    }

    // Show error snackbar
    val error = uiState.error
    if (error != null) {
        LaunchedEffect(error) {
            // In a real app, you might want to show a snackbar here
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.bookmark?.getDisplayText() ?: "Reading",
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
                },
                actions = {
                    if (uiState.listItems.isNotEmpty()) {
                        // Play/Pause All button
                        IconButton(
                            onClick = { viewModel.togglePlayback() }
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlayingAudio) "Pause" else "Play All"
                            )
                        }

                        // Reciter selection button (hide for temporary bookmarks)
                        if (uiState.bookmark?.id != -1L) {
                            IconButton(
                                onClick = { viewModel.toggleReciterSelection() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = stringResource(R.string.select_reciter)
                                )
                            }
                        }

                        // Playback speed button
                        PlaybackSpeedButton(
                            currentSpeed = uiState.playbackSpeed,
                            onSpeedChange = { viewModel.setPlaybackSpeed(it) }
                        )
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
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                val errorMessage = uiState.error ?: "Unknown error"
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadBookmark(bookmarkId) }
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            uiState.bookmark != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary

                    if (uiState.listItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.no_verses_display))
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = uiState.listItems,
                                key = { it.id }
                            ) { item ->
                                when (item) {
                                    is ReadingListItem.BookmarkHeader -> {
                                        BookmarkHeaderCard(
                                            bookmark = item.bookmark,
                                            displayText = item.displayText,
                                            metadata = item.metadata,
                                            primaryColor = primaryColor,
                                            isEditable = false
                                        )
                                    }
                                    is ReadingListItem.VerseItem -> {
                                        val bookmark = uiState.bookmark ?: return@items
                                        val ayahId = "${bookmark.id}:${item.verse.surahNumber}:${item.verse.ayahInSurah}"
                                        val isReadToday = ayahId in uiState.readAyahIds

                                        VerseCard(
                                            verse = item.verse,
                                            displayNumber = item.verse.ayahInSurah.toString(),
                                            isPlaying = uiState.currentPlayingIndex == item.globalIndex,
                                            primaryColor = primaryColor,
                                            onPlayClick = { viewModel.playAudioAtIndex(item.globalIndex) },
                                            isReadToday = isReadToday,
                                            onToggleReadStatus = { viewModel.toggleAyahReadStatus(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Reciter selection dialog
                    if (uiState.showReciterSelection) {
                        ReciterSelectionDialog(
                            reciters = uiState.reciters,
                            selectedReciter = uiState.selectedReciter,
                            onReciterSelected = { viewModel.selectReciter(it) },
                            onDismiss = { viewModel.toggleReciterSelection() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReciterSelectionDialog(
    reciters: List<io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData>,
    selectedReciter: io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData?,
    onReciterSelected: (io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_reciter)) },
        text = {
            LazyColumn {
                items(reciters) { reciter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = reciter.identifier == selectedReciter?.identifier,
                            onClick = { onReciterSelected(reciter) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = reciter.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}