package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.playlist

import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.GetProfileByIdUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetProfilePlaylistUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.PlaylistVerse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class ContinuousPlaybackViewModelTest {

    @Mock
    private lateinit var getProfileByIdUseCase: GetProfileByIdUseCase

    @Mock
    private lateinit var getProfilePlaylistUseCase: GetProfilePlaylistUseCase

    @Mock
    private lateinit var quranRepository: QuranRepository

    @Mock
    private lateinit var audioService: AudioService

    private lateinit var viewModel: ContinuousPlaybackViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleProfile = BookmarkGroup(
        id = 1L,
        name = "Daily Quran",
        description = "My daily reading",
        color = 0xFF0000,
        reciterEdition = "ar.alafasy"
    )

    private val sampleBookmark1 = Bookmark(
        id = 1L,
        groupId = 1L,
        type = BookmarkType.AYAH,
        startSurah = 1,
        startAyah = 1,
        endSurah = 1,
        endAyah = 1,
        description = "Al-Fatiha verse 1"
    )

    private val sampleBookmark2 = Bookmark(
        id = 2L,
        groupId = 1L,
        type = BookmarkType.AYAH,
        startSurah = 1,
        startAyah = 2,
        endSurah = 1,
        endAyah = 2,
        description = "Al-Fatiha verse 2"
    )

    private val sampleVerse1 = VerseMetadata(
        text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
        surahName = "الفاتحة",
        surahNameEn = "Al-Fatiha",
        revelationType = "Meccan",
        numberOfAyahs = 7,
        hizbQuarter = 1,
        rukuNumber = 1,
        page = 1,
        manzil = 1,
        sajda = false,
        globalAyahNumber = 1,
        ayahInSurah = 1
    )

    private val sampleVerse2 = VerseMetadata(
        text = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
        surahName = "الفاتحة",
        surahNameEn = "Al-Fatiha",
        revelationType = "Meccan",
        numberOfAyahs = 7,
        hizbQuarter = 1,
        rukuNumber = 1,
        page = 1,
        manzil = 1,
        sajda = false,
        globalAyahNumber = 2,
        ayahInSurah = 2
    )

    private val samplePlaylist = listOf(
        PlaylistVerse(sampleVerse1, sampleBookmark1, 0),
        PlaylistVerse(sampleVerse2, sampleBookmark2, 0)
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

    private val audioStateFlow = MutableStateFlow(
        io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioPlaybackState()
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Setup audio service mock
        whenever(audioService.playbackState).thenReturn(audioStateFlow)

        viewModel = ContinuousPlaybackViewModel(
            getProfileByIdUseCase,
            getProfilePlaylistUseCase,
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
        assertNull(state.profile)
        assertTrue(state.playlist.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.isPlayingAudio)
        assertNull(state.currentPlayingIndex)
        assertEquals(PlaybackSpeed.SPEED_1, state.playbackSpeed)
    }

    @Test
    fun `loadPlaylist should load profile and playlist successfully`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(sampleProfile)
        whenever(getProfilePlaylistUseCase(1L)).thenReturn(Result.success(samplePlaylist))
        whenever(quranRepository.getAvailableReciters()).thenReturn(
            Result.success(listOf(sampleReciter))
        )

        // When
        viewModel.loadPlaylist(1L)

        // Then
        val state = viewModel.uiState.value
        assertEquals(sampleProfile, state.profile)
        assertEquals(samplePlaylist, state.playlist)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(sampleReciter, state.selectedReciter)
    }

    @Test
    fun `loadPlaylist should set error when profile not found`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(null)

        // When
        viewModel.loadPlaylist(1L)

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Profile not found", state.error)
        assertNull(state.profile)
        assertTrue(state.playlist.isEmpty())
    }

    @Test
    fun `loadPlaylist should set error when playlist loading fails`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(sampleProfile)
        whenever(getProfilePlaylistUseCase(1L)).thenReturn(
            Result.failure(Exception("Network error"))
        )

        // When
        viewModel.loadPlaylist(1L)

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Network error", state.error)
    }

    @Test
    fun `toggleReciterSelection should toggle showReciterSelection`() {
        // Initially false
        assertFalse(viewModel.uiState.value.showReciterSelection)

        // Toggle to true
        viewModel.toggleReciterSelection()
        assertTrue(viewModel.uiState.value.showReciterSelection)

        // Toggle back to false
        viewModel.toggleReciterSelection()
        assertFalse(viewModel.uiState.value.showReciterSelection)
    }

    @Test
    fun `selectReciter should update selected reciter and hide selection`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(sampleProfile)
        whenever(getProfilePlaylistUseCase(1L)).thenReturn(Result.success(samplePlaylist))
        whenever(quranRepository.getAvailableReciters()).thenReturn(
            Result.success(listOf(sampleReciter))
        )
        viewModel.loadPlaylist(1L)
        viewModel.toggleReciterSelection()

        val newReciter = sampleReciter.copy(identifier = "ar.husary")

        // When
        viewModel.selectReciter(newReciter)

        // Then
        val state = viewModel.uiState.value
        assertEquals(newReciter, state.selectedReciter)
        assertFalse(state.showReciterSelection)
    }

    @Test
    fun `getAudioUrl should return cached audio path when available`() = runTest {
        // Given
        val verseWithCache = sampleVerse1.copy(cachedAudioPath = "/path/to/cached/audio.mp3")
        val playlistWithCache = listOf(
            PlaylistVerse(verseWithCache, sampleBookmark1, 0)
        )

        whenever(getProfileByIdUseCase(1L)).thenReturn(sampleProfile)
        whenever(getProfilePlaylistUseCase(1L)).thenReturn(Result.success(playlistWithCache))
        whenever(quranRepository.getAvailableReciters()).thenReturn(
            Result.success(listOf(sampleReciter))
        )
        viewModel.loadPlaylist(1L)

        // When
        val audioUrl = viewModel.getAudioUrl(0)

        // Then
        assertEquals("/path/to/cached/audio.mp3", audioUrl)
    }

    @Test
    fun `getAudioUrl should return streaming URL when cache not available`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(sampleProfile)
        whenever(getProfilePlaylistUseCase(1L)).thenReturn(Result.success(samplePlaylist))
        whenever(quranRepository.getAvailableReciters()).thenReturn(
            Result.success(listOf(sampleReciter))
        )
        whenever(quranRepository.getAudioUrl("ar.alafasy", 1, "128"))
            .thenReturn("https://cdn.alquran.cloud/audio/1.mp3")
        viewModel.loadPlaylist(1L)

        // When
        val audioUrl = viewModel.getAudioUrl(0)

        // Then
        assertEquals("https://cdn.alquran.cloud/audio/1.mp3", audioUrl)
        verify(quranRepository).getAudioUrl("ar.alafasy", 1, "128")
    }

    @Test
    fun `getAudioUrl should return null for invalid index`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(sampleProfile)
        whenever(getProfilePlaylistUseCase(1L)).thenReturn(Result.success(samplePlaylist))
        whenever(quranRepository.getAvailableReciters()).thenReturn(
            Result.success(listOf(sampleReciter))
        )
        viewModel.loadPlaylist(1L)

        // When
        val audioUrl = viewModel.getAudioUrl(999)

        // Then
        assertNull(audioUrl)
    }

    @Test
    fun `playAudioAtIndex should update current playing index and call audioService`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(sampleProfile)
        whenever(getProfilePlaylistUseCase(1L)).thenReturn(Result.success(samplePlaylist))
        whenever(quranRepository.getAvailableReciters()).thenReturn(
            Result.success(listOf(sampleReciter))
        )
        whenever(quranRepository.getAudioUrl(any(), any(), any()))
            .thenReturn("https://audio.url")
        viewModel.loadPlaylist(1L)

        // When
        viewModel.playAudioAtIndex(0)

        // Then
        assertEquals(0, viewModel.uiState.value.currentPlayingIndex)
        verify(audioService).playAudio("https://audio.url")
    }

    @Test
    fun `playAudioAtIndex should not play for invalid index`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(sampleProfile)
        whenever(getProfilePlaylistUseCase(1L)).thenReturn(Result.success(samplePlaylist))
        whenever(quranRepository.getAvailableReciters()).thenReturn(
            Result.success(listOf(sampleReciter))
        )
        viewModel.loadPlaylist(1L)

        // When
        viewModel.playAudioAtIndex(999)

        // Then
        verify(audioService, never()).playAudio(any())
    }

    @Test
    fun `togglePlayback should pause when audio is playing`() = runTest {
        // Given
        audioStateFlow.value = audioStateFlow.value.copy(isPlaying = true)

        // When
        viewModel.togglePlayback()

        // Then
        verify(audioService).pause()
    }

    @Test
    fun `togglePlayback should play when audio is not playing`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(sampleProfile)
        whenever(getProfilePlaylistUseCase(1L)).thenReturn(Result.success(samplePlaylist))
        whenever(quranRepository.getAvailableReciters()).thenReturn(
            Result.success(listOf(sampleReciter))
        )
        whenever(quranRepository.getAudioUrl(any(), any(), any()))
            .thenReturn("https://audio.url")
        viewModel.loadPlaylist(1L)
        audioStateFlow.value = audioStateFlow.value.copy(isPlaying = false)

        // When
        viewModel.togglePlayback()

        // Then
        verify(audioService).playAudio("https://audio.url")
    }

    @Test
    fun `setPlaybackSpeed should update audioService`() {
        // When
        viewModel.setPlaybackSpeed(PlaybackSpeed.SPEED_1_5)

        // Then
        verify(audioService).setPlaybackSpeed(PlaybackSpeed.SPEED_1_5)
    }

    @Test
    fun `getCurrentPlaybackSpeed should return speed from audioService`() {
        // Given
        whenever(audioService.getCurrentPlaybackSpeed()).thenReturn(PlaybackSpeed.SPEED_2)

        // When
        val speed = viewModel.getCurrentPlaybackSpeed()

        // Then
        assertEquals(PlaybackSpeed.SPEED_2, speed)
    }

    @Test
    fun `clearError should clear error state`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(null)
        viewModel.loadPlaylist(1L)
        assertNotNull(viewModel.uiState.value.error)

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.uiState.value.error)
        verify(audioService).clearError()
    }

    @Test
    fun `loadPlaylist with empty playlist should succeed`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(sampleProfile)
        whenever(getProfilePlaylistUseCase(1L)).thenReturn(Result.success(emptyList()))
        whenever(quranRepository.getAvailableReciters()).thenReturn(
            Result.success(listOf(sampleReciter))
        )

        // When
        viewModel.loadPlaylist(1L)

        // Then
        val state = viewModel.uiState.value
        assertEquals(sampleProfile, state.profile)
        assertTrue(state.playlist.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }
}