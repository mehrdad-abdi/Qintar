package io.github.mehrdad_abdi.qintar.playback

import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Download for offline" (R-AUD-3): what is fetched, and when nothing is. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioDownloaderTest {
    private val app = DomainFixture()
    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    private class Fetcher : AudioFetcher {
        val fetched = ArrayList<String>()
        var failAt: String? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun fetch(url: String) {
            gate?.await()
            if (url == failAt) throw IOException("no route")
            fetched += url
        }
    }

    private class Network(var online: Boolean = true, var unmetered: Boolean = true) : NetworkStatus {
        override fun isOnline() = online
        override fun isUnmetered() = unmetered
    }

    private val fetcher = Fetcher()
    private val network = Network()
    private val source = IslamicNetworkAudioSource()
    private val downloader = AudioDownloader(fetcher, network, app.prefs, source, scope)
    private val ikhlas = app.index.surahRange(112)
    private fun url(ayah: Int, reciter: String = "ar.alafasy", bitrate: Int = 64) = "${IslamicNetworkAudioSource.BASE}/$bitrate/$reciter/$ayah.mp3"

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `a surah is fetched ayah by ayah, with 1-1 for the basmala, and ends as finished`() {
        downloader.download(listOf(ikhlas))

        assertEquals(listOf(url(1)) + ikhlas.ids.map { url(it) }, fetcher.fetched)
        assertEquals(DownloadState.Finished(5), downloader.state.value)

        downloader.acknowledge()
        assertEquals(DownloadState.Idle, downloader.state.value)
    }

    @Test
    fun `the files are those of the chosen reciter and quality`() = runBlocking {
        app.prefs.update { it.copy(reciterId = "ar.husary", audioQuality = 128) }
        downloader.download(listOf(AyahRange.of(262, 262)))
        assertEquals(listOf(url(1, "ar.husary", 128), url(262, "ar.husary", 128)), fetcher.fetched)
    }

    @Test
    fun `an ayah that is in two passages is fetched once`() {
        downloader.download(listOf(AyahRange.of(1, 3), AyahRange.of(2, 4)))
        assertEquals((1..4).map { url(it) }, fetcher.fetched)
        assertEquals(DownloadState.Finished(4), downloader.state.value)
    }

    @Test
    fun `with Wi-Fi only on, nothing is fetched over a metered connection`() = runBlocking {
        network.unmetered = false
        downloader.download(listOf(ikhlas))
        assertEquals(DownloadState.Blocked(DownloadState.Blocked.Reason.WIFI_ONLY), downloader.state.value)
        assertTrue(fetcher.fetched.isEmpty())

        // The setting off: mobile data is allowed.
        app.prefs.update { it.copy(wifiOnlyDownloads = false) }
        downloader.download(listOf(ikhlas))
        assertEquals(DownloadState.Finished(5), downloader.state.value)
    }

    @Test
    fun `offline nothing is fetched and the reason is told`() {
        network.online = false
        downloader.download(listOf(ikhlas))
        assertEquals(DownloadState.Blocked(DownloadState.Blocked.Reason.OFFLINE), downloader.state.value)
        assertTrue(fetcher.fetched.isEmpty())
    }

    @Test
    fun `a file that cannot be loaded stops the download and says how far it got`() {
        fetcher.failAt = url(ikhlas.start.value + 1)
        downloader.download(listOf(ikhlas))
        assertEquals(DownloadState.Failed(done = 2, total = 5), downloader.state.value)
        assertEquals(2, fetcher.fetched.size)
    }

    @Test
    fun `progress is shown while it runs, and cancel stops it`() {
        val gate = CompletableDeferred<Unit>()
        fetcher.gate = gate
        downloader.download(listOf(ikhlas))
        assertEquals(DownloadState.Running(0, 5), downloader.state.value)

        // Running is not an outcome: acknowledging it changes nothing.
        downloader.acknowledge()
        assertEquals(DownloadState.Running(0, 5), downloader.state.value)

        downloader.cancel()
        gate.complete(Unit)
        assertEquals(DownloadState.Idle, downloader.state.value)
        assertTrue(fetcher.fetched.isEmpty())
    }
}
