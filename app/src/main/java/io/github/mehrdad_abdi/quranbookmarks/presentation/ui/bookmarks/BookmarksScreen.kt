package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddBookmark: () -> Unit,
    onNavigateToEditBookmark: (Long) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Play All button
                    if (uiState.bookmarksWithAyahs.isNotEmpty() &&
                        uiState.bookmarksWithAyahs.any { it.ayahs.isNotEmpty() }) {
                        IconButton(onClick = { viewModel.togglePlayAll() }) {
                            Icon(
                                imageVector = if (uiState.isPlayingAll) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlayingAll) "Stop Playing All" else "Play All"
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
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddBookmark) {
                Icon(Icons.Default.Add, "Add Bookmark")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tag filter chips
            if (uiState.allTags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All" chip
                    item {
                        FilterChip(
                            selected = uiState.selectedTag == null,
                            onClick = { viewModel.selectTag(null) },
                            label = { Text("All") }
                        )
                    }

                    items(uiState.allTags) { tag ->
                        FilterChip(
                            selected = uiState.selectedTag == tag,
                            onClick = { viewModel.selectTag(tag) },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            // Bookmarks list
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.bookmarksWithAyahs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No bookmarks yet. Tap + to add one!")
                    }
                }
                else -> {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

                    // Auto-scroll to currently playing ayah
                    LaunchedEffect(uiState.currentPlayingAyah) {
                        val playingAyah = uiState.currentPlayingAyah
                        if (playingAyah != null) {
                            // Find the index of the playing ayah in the flat list
                            var itemIndex = 0
                            var found = false

                            for (bookmarkWithAyahs in uiState.bookmarksWithAyahs) {
                                // Skip bookmark header
                                itemIndex++

                                // Check ayahs
                                for (ayah in bookmarkWithAyahs.ayahs) {
                                    if (ayah.globalAyahNumber == playingAyah) {
                                        found = true
                                        break
                                    }
                                    itemIndex++
                                }

                                if (found) break
                            }

                            if (found && itemIndex < listState.layoutInfo.totalItemsCount) {
                                listState.animateScrollToItem(itemIndex)
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.bookmarksWithAyahs.forEach { bookmarkWithAyahs ->
                            // Bookmark card header
                            item(key = "bookmark_${bookmarkWithAyahs.bookmark.id}") {
                                BookmarkCard(
                                    bookmark = bookmarkWithAyahs.bookmark,
                                    firstAyahText = bookmarkWithAyahs.ayahs.firstOrNull()?.text,
                                    onEdit = { onNavigateToEditBookmark(bookmarkWithAyahs.bookmark.id) },
                                    onDelete = { viewModel.deleteBookmark(bookmarkWithAyahs.bookmark.id) },
                                    primaryColor = primaryColor
                                )
                            }

                            // Ayahs for this bookmark
                            if (bookmarkWithAyahs.isLoadingAyahs) {
                                item(key = "loading_${bookmarkWithAyahs.bookmark.id}") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else {
                                items(
                                    items = bookmarkWithAyahs.ayahs,
                                    key = { ayah -> "ayah_${bookmarkWithAyahs.bookmark.id}_${ayah.surahNumber}_${ayah.ayahInSurah}" }
                                ) { ayah ->
                                    val ayahId = "${bookmarkWithAyahs.bookmark.id}:${ayah.surahNumber}:${ayah.ayahInSurah}"
                                    val isReadToday = ayahId in uiState.readAyahIds

                                    VerseCard(
                                        verse = ayah,
                                        bookmark = bookmarkWithAyahs.bookmark,
                                        displayNumber = "${ayah.ayahInSurah}",
                                        isPlaying = viewModel.isAyahPlaying(ayah.globalAyahNumber),
                                        isSelected = viewModel.isAyahSelected(ayah.globalAyahNumber),
                                        primaryColor = primaryColor,
                                        onPlayClick = {
                                            if (viewModel.isAyahSelected(ayah.globalAyahNumber)) {
                                                // Same ayah - toggle play/pause
                                                if (viewModel.isAyahPlaying(ayah.globalAyahNumber)) {
                                                    viewModel.pauseAyah()
                                                } else {
                                                    viewModel.resumeAyah()
                                                }
                                            } else {
                                                // Different ayah - play it
                                                viewModel.playAyah(ayah)
                                            }
                                        },
                                        isReadToday = isReadToday,
                                        onToggleReadStatus = { viewModel.toggleAyahReadStatus(bookmarkWithAyahs.bookmark, ayah) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error
            viewModel.clearError()
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: Bookmark,
    firstAyahText: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    primaryColor: Color
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = primaryColor.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bookmark info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bookmark.getDisplayText(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Metadata line (description or tags summary)
                    val metadata = if (bookmark.description.isNotBlank()) {
                        bookmark.description
                    } else if (bookmark.tags.isNotEmpty()) {
                        bookmark.tags.joinToString(", ")
                    } else {
                        ""
                    }

                    if (metadata.isNotBlank()) {
                        Text(
                            text = metadata,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Actions
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            // Arabic text preview
            if (firstAyahText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = firstAyahText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Bookmark") },
            text = { Text("Are you sure you want to delete this bookmark?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VerseCard(
    verse: VerseMetadata,
    bookmark: Bookmark,
    displayNumber: String,
    isPlaying: Boolean,
    isSelected: Boolean = false,
    primaryColor: Color,
    onPlayClick: () -> Unit,
    isReadToday: Boolean = false,
    onToggleReadStatus: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (verse.sajda) {
                            Text(
                                text = "۩",
                                style = MaterialTheme.typography.headlineSmall,
                                color = primaryColor,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Text(
                            text = verse.text,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                        text = displayNumber,
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
