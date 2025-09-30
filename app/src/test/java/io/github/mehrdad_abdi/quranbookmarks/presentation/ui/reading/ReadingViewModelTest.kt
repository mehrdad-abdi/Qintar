package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.reading

import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarkByIdUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetBookmarkContentUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class ReadingViewModelTest {

    @Mock
    private lateinit var getBookmarkByIdUseCase: GetBookmarkByIdUseCase

    @Mock
    private lateinit var getBookmarkContentUseCase: GetBookmarkContentUseCase

    @Mock
    private lateinit var quranRepository: QuranRepository

    @Mock
    private lateinit var audioService: AudioService

    private lateinit var viewModel: ReadingViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleBookmark = Bookmark(
        id = 1L,
        groupId = 1L,
        type = BookmarkType.AYAH,
        startSurah = 2,
        startAyah = 255,
        endSurah = 2,
        endAyah = 255,
        description = "Ayat al-Kursi"
    )

    private val sampleVerse = VerseMetadata(
        text = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
        surahName = "البقرة",
        surahNameEn = "Al-Baqarah",
        revelationType = "Medinan",
        numberOfAyahs = 286,
        hizbQuarter = 10,
        rukuNumber = 3,
        page = 42,
        manzil = 1,
        sajda = false,
        globalAyahNumber = 255,
        ayahInSurah = 255
    )

    private val sampleReciter = ReciterData(
        identifier = "ar.alafasy",
        language = "ar",
        name = "مشاري العفاسي",
        englishName = "Mishary Alafasy",
        format = "audio",
        type = "versebyverse",
        direction = null
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        viewModel = ReadingViewModel(
            getBookmarkByIdUseCase,
            getBookmarkContentUseCase,
            quranRepository,
            audioService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should have default values`() {
        val state = viewModel.uiState.value
        assertNull(state.bookmark)
        assertTrue(state.verses.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.isPlayingAudio)
        assertNull(state.currentPlayingVerse)
        assertTrue(state.reciters.isEmpty())
        assertNull(state.selectedReciter)
        assertFalse(state.showReciterSelection)
    }

    @Test
    fun `loadBookmarkContent should load bookmark and verses successfully`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(getBookmarkContentUseCase(sampleBookmark)).thenReturn(Result.success(listOf(sampleVerse)))
        whenever(quranRepository.getAvailableReciters()).thenReturn(Result.success(listOf(sampleReciter)))

        // When
        viewModel.loadBookmarkContent(1L)

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(sampleBookmark, state.bookmark)
        assertEquals(1, state.verses.size)
        assertEquals(sampleVerse, state.verses[0])
        assertEquals(1, state.reciters.size)
        assertEquals(sampleReciter, state.selectedReciter)
        assertNull(state.error)
    }

    @Test
    fun `loadBookmarkContent should handle bookmark loading failure`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.failure(Exception("Bookmark not found")))

        // When
        viewModel.loadBookmarkContent(1L)

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.bookmark)
        assertTrue(state.verses.isEmpty())
        assertEquals("Bookmark not found", state.error)
    }

    @Test
    fun `loadBookmarkContent should handle content loading failure`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(getBookmarkContentUseCase(sampleBookmark)).thenReturn(Result.failure(Exception("Content not found")))

        // When
        viewModel.loadBookmarkContent(1L)

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(sampleBookmark, state.bookmark)
        assertTrue(state.verses.isEmpty())
        assertEquals("Content not found", state.error)
    }

    @Test
    fun `loadBookmarkContent should handle reciters loading failure gracefully`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(getBookmarkContentUseCase(sampleBookmark)).thenReturn(Result.success(listOf(sampleVerse)))
        whenever(quranRepository.getAvailableReciters()).thenReturn(Result.failure(Exception("Reciters not available")))

        // When
        viewModel.loadBookmarkContent(1L)

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(sampleBookmark, state.bookmark)
        assertEquals(1, state.verses.size)
        assertTrue(state.reciters.isEmpty()) // Reciters should be empty but not cause error
        assertNull(state.selectedReciter)
        assertNull(state.error) // Main error should still be null
    }

    @Test
    fun `toggleReciterSelection should toggle reciter selection state`() {
        // When - first toggle
        viewModel.toggleReciterSelection()

        // Then
        assertTrue(viewModel.uiState.value.showReciterSelection)

        // When - second toggle
        viewModel.toggleReciterSelection()

        // Then
        assertFalse(viewModel.uiState.value.showReciterSelection)
    }

    @Test
    fun `selectReciter should update selected reciter and hide selection`() {
        // Given
        viewModel.toggleReciterSelection() // Show selection first

        // When
        viewModel.selectReciter(sampleReciter)

        // Then
        val state = viewModel.uiState.value
        assertEquals(sampleReciter, state.selectedReciter)
        assertFalse(state.showReciterSelection)
    }

    @Test
    fun `toggleAudioPlayback should start playback when not playing`() {
        // When
        viewModel.toggleAudioPlayback(0)

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.isPlayingAudio)
        assertEquals(0, state.currentPlayingVerse)
    }

    @Test
    fun `toggleAudioPlayback should stop playback when playing`() {
        // Given - start playback first
        viewModel.toggleAudioPlayback(0)
        assertTrue(viewModel.uiState.value.isPlayingAudio)

        // When - stop playback
        viewModel.toggleAudioPlayback()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isPlayingAudio)
        assertNull(state.currentPlayingVerse)
    }

    @Test
    fun `toggleAudioPlayback without index should default to first verse`() {
        // When
        viewModel.toggleAudioPlayback()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.isPlayingAudio)
        assertEquals(0, state.currentPlayingVerse)
    }

    @Test
    fun `getAudioUrl should return URL when reciter and verse exist`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(getBookmarkContentUseCase(sampleBookmark)).thenReturn(Result.success(listOf(sampleVerse)))
        whenever(quranRepository.getAvailableReciters()).thenReturn(Result.success(listOf(sampleReciter)))
        whenever(quranRepository.getAudioUrl(any(), any(), any())).thenReturn("https://audio.url")

        viewModel.loadBookmarkContent(1L)

        // When
        val audioUrl = viewModel.getAudioUrl(0)

        // Then
        assertNotNull(audioUrl)
        assertEquals("https://audio.url", audioUrl)
        verify(quranRepository).getAudioUrl(eq("ar.alafasy"), any())
    }

    @Test
    fun `getAudioUrl should return null when no reciter selected`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(getBookmarkContentUseCase(sampleBookmark)).thenReturn(Result.success(listOf(sampleVerse)))
        whenever(quranRepository.getAvailableReciters()).thenReturn(Result.success(emptyList()))

        viewModel.loadBookmarkContent(1L)

        // When
        val audioUrl = viewModel.getAudioUrl(0)

        // Then
        assertNull(audioUrl)
    }

    @Test
    fun `getAudioUrl should return null when verse index out of bounds`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(getBookmarkContentUseCase(sampleBookmark)).thenReturn(Result.success(listOf(sampleVerse)))
        whenever(quranRepository.getAvailableReciters()).thenReturn(Result.success(listOf(sampleReciter)))

        viewModel.loadBookmarkContent(1L)

        // When
        val audioUrl = viewModel.getAudioUrl(999) // Out of bounds

        // Then
        assertNull(audioUrl)
    }

    @Test
    fun `getImageUrl should return URL when verse exists`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(getBookmarkContentUseCase(sampleBookmark)).thenReturn(Result.success(listOf(sampleVerse)))
        whenever(quranRepository.getImageUrl(any(), any(), any())).thenReturn("https://image.url")

        viewModel.loadBookmarkContent(1L)

        // When
        val imageUrl = viewModel.getImageUrl(0)

        // Then
        assertNotNull(imageUrl)
        assertEquals("https://image.url", imageUrl)
        verify(quranRepository).getImageUrl(any(), any(), eq(false))
    }

    @Test
    fun `getImageUrl should return null when verse index out of bounds`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(getBookmarkContentUseCase(sampleBookmark)).thenReturn(Result.success(listOf(sampleVerse)))

        viewModel.loadBookmarkContent(1L)

        // When
        val imageUrl = viewModel.getImageUrl(999) // Out of bounds

        // Then
        assertNull(imageUrl)
    }

    @Test
    fun `clearError should clear error state`() = runTest {
        // Given - set error state
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.failure(Exception("Test error")))
        viewModel.loadBookmarkContent(1L)
        assertNotNull(viewModel.uiState.value.error)

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.uiState.value.error)
    }
}