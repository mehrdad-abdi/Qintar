package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmarks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.RtlIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.R
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.BookmarkHeaderCard
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.PlaybackSpeedButton
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.VerseCard

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
                title = { Text(stringResource(R.string.screen_bookmarks_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(RtlIcons.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Expand/Collapse All button
                    if (uiState.bookmarksWithAyahs.isNotEmpty()) {
                        val allExpanded = uiState.bookmarksWithAyahs.all { it.isExpanded }
                        IconButton(onClick = { viewModel.expandOrCollapseAll() }) {
                            Icon(
                                imageVector = if (allExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = if (allExpanded) "Collapse All" else "Expand All"
                            )
                        }
                    }

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
                        PlaybackSpeedButton(
                            currentSpeed = uiState.playbackSpeed,
                            onSpeedChange = { viewModel.setPlaybackSpeed(it) }
                        )
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
                            label = { Text(stringResource(R.string.tab_all)) }
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
                        Text(stringResource(R.string.empty_bookmarks_message))
                    }
                }
                else -> {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

                    // Auto-scroll to currently playing ayah
                    LaunchedEffect(uiState.currentPlayingAyah) {
                        val playingAyah = uiState.currentPlayingAyah
                        if (playingAyah != null) {
                            // Find the bookmark containing this ayah and expand if needed
                            var targetBookmarkId: Long?
                            var itemIndex = 0
                            var found = false

                            for (bookmarkWithAyahs in uiState.bookmarksWithAyahs) {
                                // Check if this bookmark contains the playing ayah
                                // IMPORTANT: Only expand if this is the CURRENTLY PLAYING bookmark
                                val containsAyah = bookmarkWithAyahs.ayahs.any {
                                    it.surahNumber == playingAyah.surah && it.ayahInSurah == playingAyah.ayah
                                }
                                val isCurrentlyPlayingBookmark = viewModel.isBookmarkCurrentlyPlaying(bookmarkWithAyahs.bookmark.id)

                                if (containsAyah && isCurrentlyPlayingBookmark) {
                                    targetBookmarkId = bookmarkWithAyahs.bookmark.id

                                    // Expand the bookmark if it's collapsed
                                    if (!bookmarkWithAyahs.isExpanded) {
                                        viewModel.expandBookmarkIfNeeded(targetBookmarkId)
                                        // Wait a bit for the UI to update after expansion
                                        kotlinx.coroutines.delay(100)
                                    }
                                    break // Found the correct bookmark, stop searching
                                }
                            }

                            // Now calculate the index after potential expansion
                            for (bookmarkWithAyahs in uiState.bookmarksWithAyahs) {
                                // Skip bookmark header
                                itemIndex++

                                // Only count ayahs if the bookmark is expanded
                                if (bookmarkWithAyahs.isExpanded) {
                                    for (ayah in bookmarkWithAyahs.ayahs) {
                                        if (ayah.surahNumber == playingAyah.surah && ayah.ayahInSurah == playingAyah.ayah &&
                                            bookmarkWithAyahs.bookmark.id == playingAyah.bookmarkId) {
                                            found = true
                                            break
                                        }
                                        itemIndex++
                                    }
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
                                val bookmark = bookmarkWithAyahs.bookmark
                                val metadata = if (bookmark.description.isNotBlank()) {
                                    bookmark.description
                                } else if (bookmark.tags.isNotEmpty()) {
                                    bookmark.tags.joinToString(", ")
                                } else {
                                    ""
                                }

                                BookmarkHeaderCard(
                                    bookmark = bookmark,
                                    displayText = bookmarkWithAyahs.displayText,
                                    metadata = metadata,
                                    primaryColor = primaryColor,
                                    isEditable = true,
                                    isExpanded = bookmarkWithAyahs.isExpanded,
                                    onEdit = { onNavigateToEditBookmark(bookmark.id) },
                                    onDelete = { viewModel.deleteBookmark(bookmark.id) },
                                    onToggleExpansion = { viewModel.toggleBookmarkExpansion(bookmark.id) }
                                )
                            }

                            // Ayahs for this bookmark (only show if expanded)
                            if (bookmarkWithAyahs.isExpanded) {
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
                                            displayNumber = ayah.ayahInSurah.toString(),
                                            isPlaying = viewModel.isAyahPlaying(bookmarkWithAyahs.bookmark.id, ayah.surahNumber, ayah.ayahInSurah),
                                            isSelected = viewModel.isAyahSelected(bookmarkWithAyahs.bookmark.id, ayah.surahNumber, ayah.ayahInSurah),
                                            primaryColor = primaryColor,
                                            onPlayClick = { viewModel.playAyah(bookmarkWithAyahs.bookmark.id, ayah) },
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
    }

    // Error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error
            viewModel.clearError()
        }
    }
}
