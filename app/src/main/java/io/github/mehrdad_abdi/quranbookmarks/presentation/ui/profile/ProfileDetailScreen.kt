package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    profileId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToAddBookmark: (Long) -> Unit,
    onNavigateToEditBookmark: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }

    // Auto-scroll to currently playing verse
    LaunchedEffect(uiState.currentPlayingIndex) {
        val playingIndex = uiState.currentPlayingIndex
        if (playingIndex != null && uiState.listItems.isNotEmpty()) {
            // Find the item index in the list (accounting for headers)
            val itemIndex = uiState.listItems.indexOfFirst { item ->
                item is ProfileListItem.VerseItem && item.globalIndex == playingIndex
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
                        text = uiState.profile?.name ?: "Profile Details",
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
                    val profile = uiState.profile
                    if (profile != null && uiState.listItems.isNotEmpty()) {
                        val profileColor = Color(profile.color)

                        // Play/Pause All button
                        IconButton(
                            onClick = { viewModel.togglePlayback() }
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlayingAudio) "Pause" else "Play All",
                                tint = profileColor
                            )
                        }

                        // Reciter selection button
                        IconButton(
                            onClick = { viewModel.toggleReciterSelection() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Select Reciter",
                                tint = profileColor
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
                                    contentDescription = "Playback Speed: ${uiState.playbackSpeed.displayText}",
                                    tint = profileColor
                                )
                            }
                            // Speed badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = (-4).dp, y = (-4).dp)
                                    .background(
                                        color = profileColor,
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
        floatingActionButton = {
            val profile = uiState.profile
            if (profile != null) {
                FloatingActionButton(
                    onClick = { onNavigateToAddBookmark(profile.id) },
                    containerColor = Color(profile.color)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Bookmark",
                        tint = Color.White
                    )
                }
            }
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
                        onClick = { viewModel.loadProfile(profileId) }
                    ) {
                        Text("Retry")
                    }
                }
            }

            uiState.profile != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (uiState.listItems.isEmpty()) {
                        EmptyBookmarksCard(
                            onAddBookmarkClick = { onNavigateToAddBookmark(profileId) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 80.dp // Extra padding for FAB
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = uiState.listItems,
                                key = { it.id }
                            ) { item ->
                                val profile = uiState.profile
                                if (profile != null) {
                                    val profileColor = Color(profile.color)
                                    when (item) {
                                        is ProfileListItem.BookmarkHeader -> {
                                            BookmarkHeaderCard(
                                                bookmark = item.bookmark,
                                                displayText = item.displayText,
                                                metadata = item.metadata,
                                                profileColor = profileColor,
                                                onEditClick = { onNavigateToEditBookmark(item.bookmark.id) },
                                                onDeleteClick = { viewModel.deleteBookmark(item.bookmark.id) }
                                            )
                                        }
                                        is ProfileListItem.VerseItem -> {
                                            VerseCard(
                                                verseItem = item,
                                                isPlaying = uiState.currentPlayingIndex == item.globalIndex,
                                                profileColor = profileColor,
                                                onPlayClick = { viewModel.playAudioAtIndex(item.globalIndex) }
                                            )
                                        }
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
    profileColor: Color,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = profileColor.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = profileColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(
                    onClick = onEditClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Bookmark",
                        tint = profileColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showDeleteDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete Bookmark",
                        tint = profileColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Bookmark") },
            text = { Text("Are you sure you want to delete this bookmark? All verses in this bookmark will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VerseCard(
    verseItem: ProfileListItem.VerseItem,
    isPlaying: Boolean,
    profileColor: Color,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                profileColor.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                    tint = profileColor
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
                    .background(profileColor),
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