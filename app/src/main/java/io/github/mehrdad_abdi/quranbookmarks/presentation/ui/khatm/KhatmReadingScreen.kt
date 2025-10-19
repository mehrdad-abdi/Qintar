package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.khatm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.quranbookmarks.R
import io.github.mehrdad_abdi.quranbookmarks.domain.model.KhatmProgress
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.PlaybackSpeedButton
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.RtlIcons
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.VerseCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhatmReadingScreen(
    surahs: List<Surah>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KhatmReadingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // Pager state for 604 pages
    val pagerState = rememberPagerState(
        initialPage = 0, // Will be set by LaunchedEffect below
        pageCount = { KhatmProgress.TOTAL_PAGES }
    )

    // Sync pager with ViewModel state on initial load and programmatic changes
    LaunchedEffect(uiState.currentPage) {
        val targetPage = uiState.currentPage - 1 // Convert to 1-indexed to 0-indexed
        if (targetPage != pagerState.currentPage && targetPage in 0 until KhatmProgress.TOTAL_PAGES) {
            pagerState.scrollToPage(targetPage)
        }
    }

    // Update ViewModel when user swipes to a different page
    LaunchedEffect(pagerState.currentPage) {
        snapshotFlow { pagerState.currentPage }
            .collect { pageIndex ->
                val newPage = pageIndex + 1 // Convert from 0-indexed to 1-indexed
                if (newPage != uiState.currentPage) {
                    viewModel.loadPage(newPage)
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.page_indicator, uiState.currentPage, KhatmProgress.TOTAL_PAGES),
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
                    // Jump button
                    IconButton(onClick = { viewModel.toggleJumpDialog() }) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = stringResource(R.string.jump_to)
                        )
                    }

                    // Play/Pause All button
                    IconButton(onClick = { viewModel.togglePlayback() }) {
                        Icon(
                            imageVector = if (uiState.isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlayingAudio)
                                stringResource(R.string.pause)
                            else
                                stringResource(R.string.play_all_page)
                        )
                    }

                    // Playback speed button
                    PlaybackSpeedButton(
                        currentSpeed = uiState.playbackSpeed,
                        onSpeedChange = { viewModel.setPlaybackSpeed(it) }
                    )
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Horizontal Pager for swiping between pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // RTL: swipe left = next page (increment), swipe right = previous page (decrement)
                reverseLayout = true // This makes left swipe go to next page for Arabic
            ) { pageIndex ->
                PageContent(
                    verses = uiState.verses,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    currentPlayingIndex = uiState.currentPlayingIndex,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    onPlayClick = { verseIndex ->
                        viewModel.playAudioAtIndex(verseIndex)
                    },
                    onRetry = { viewModel.loadPage(uiState.currentPage) },
                    readAyahIds = uiState.readAyahIds,
                    onToggleReadStatus = { verse ->
                        viewModel.toggleAyahReadStatus(verse)
                    }
                )
            }

            // Jump Dialog
            if (uiState.showJumpDialog) {
                KhatmJumpDialog(
                    currentPage = uiState.currentPage,
                    surahs = surahs,
                    onJumpToPage = { page ->
                        viewModel.jumpToPage(page)
                        coroutineScope.launch {
                            pagerState.scrollToPage(page - 1)
                        }
                    },
                    onDismiss = { viewModel.toggleJumpDialog() }
                )
            }
        }
    }
}

@Composable
private fun PageContent(
    verses: List<io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata>,
    isLoading: Boolean,
    error: String?,
    currentPlayingIndex: Int?,
    primaryColor: androidx.compose.ui.graphics.Color,
    onPlayClick: (Int) -> Unit,
    onRetry: () -> Unit,
    readAyahIds: Set<String> = emptySet(),
    onToggleReadStatus: (io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to currently playing ayah
    LaunchedEffect(currentPlayingIndex) {
        if (currentPlayingIndex != null && currentPlayingIndex in verses.indices) {
            coroutineScope.launch {
                // Scroll to make the playing ayah visible
                // Use animateScrollToItem for smooth scrolling
                listState.animateScrollToItem(
                    index = currentPlayingIndex,
                    scrollOffset = -100 // Add some offset to show context above
                )
            }
        }
    }

    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        verses.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_verses_display))
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = verses,
                    key = { index, verse -> "${verse.surahNumber}:${verse.ayahInSurah}:$index" }
                ) { index, verse ->
                    val ayahId = "-1:${verse.surahNumber}:${verse.ayahInSurah}"
                    val isReadToday = ayahId in readAyahIds

                    VerseCard(
                        verse = verse,
                        displayNumber = verse.ayahInSurah.toString(),
                        isPlaying = currentPlayingIndex == index,
                        primaryColor = primaryColor,
                        onPlayClick = { onPlayClick(index) },
                        isReadToday = isReadToday,
                        onToggleReadStatus = { onToggleReadStatus(verse) }
                    )
                }
            }
        }
    }
}
