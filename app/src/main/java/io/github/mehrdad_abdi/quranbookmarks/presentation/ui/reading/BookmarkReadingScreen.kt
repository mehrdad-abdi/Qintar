package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed

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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
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

                        // Reciter selection button
                        IconButton(
                            onClick = { viewModel.toggleReciterSelection() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Select Reciter"
                            )
                        }

                        // Playback speed button
                        Box {
                            IconButton(
                                onClick = {
                                    val nextSpeed = when (uiState.playbackSpeed) {
                                        PlaybackSpeed.SPEED_0_5 -> PlaybackSpeed.SPEED_0_75
                                        PlaybackSpeed.SPEED_0_75 -> PlaybackSpeed.SPEED_1
                                        PlaybackSpeed.SPEED_1 -> PlaybackSpeed.SPEED_1_25
                                        PlaybackSpeed.SPEED_1_25 -> PlaybackSpeed.SPEED_1_5
                                        PlaybackSpeed.SPEED_1_5 -> PlaybackSpeed.SPEED_2
                                        PlaybackSpeed.SPEED_2 -> PlaybackSpeed.SPEED_0_5
                                    }
                                    viewModel.setPlaybackSpeed(nextSpeed)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed: ${uiState.playbackSpeed.displayText}"
                                )
                            }
                            // Speed badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = (-4).dp, y = (-4).dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = uiState.playbackSpeed.displayText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = Color.White
                                )
                            }
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
                        Text("Retry")
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
                            Text("No verses to display")
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
                                            primaryColor = primaryColor
                                        )
                                    }
                                    is ReadingListItem.VerseItem -> {
                                        val ayahId = "${item.bookmark.id}:${item.verse.surahNumber}:${item.verse.ayahInSurah}"
                                        val isReadToday = ayahId in uiState.readAyahIds

                                        VerseCard(
                                            verseItem = item,
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
private fun BookmarkHeaderCard(
    bookmark: Bookmark,
    displayText: String,
    metadata: String,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = primaryColor.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VerseCard(
    verseItem: ReadingListItem.VerseItem,
    isPlaying: Boolean,
    primaryColor: Color,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    isReadToday: Boolean = false,
    onToggleReadStatus: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                primaryColor.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Play button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = primaryColor
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Verse text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = verseItem.verse.text,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Verse number badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(primaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = verseItem.displayNumber,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Read status indicator at bottom left
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleReadStatus,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = if (isReadToday) "Mark as unread" else "Mark as read",
                        tint = if (isReadToday) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
                        modifier = Modifier.size(20.dp)
                    )
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
        title = { Text("Select Reciter") },
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
                Text("Close")
            }
        }
    )
}

@Composable
private fun EmptyBookmarksCard(
    onAddBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No bookmarks yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Start adding bookmarks to organize your favorite verses",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAddBookmarkClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Your First Bookmark")
            }
        }
    }
}