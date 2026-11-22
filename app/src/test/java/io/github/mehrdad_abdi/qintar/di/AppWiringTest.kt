package io.github.mehrdad_abdi.qintar.di

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.mehrdad_abdi.qintar.Appearance
import io.github.mehrdad_abdi.qintar.ClockChangeReceiver
import io.github.mehrdad_abdi.qintar.appearanceOf
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.time.SystemDayClock
import io.github.mehrdad_abdi.qintar.ui.common.RankReachedDetector
import io.github.mehrdad_abdi.qintar.ui.designsystem.NumeralStyle
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppWiringTest {
    @Test
    fun `the appearance settings map onto the design system's terms`() {
        assertEquals(Appearance(dark = null, pureBlack = false, numerals = NumeralStyle.FollowLanguage, languageTag = null), appearanceOf(UserPrefs()))
        assertEquals(
            Appearance(dark = true, pureBlack = true, numerals = NumeralStyle.Latin, languageTag = "fa"),
            appearanceOf(UserPrefs(theme = ThemeMode.DARK, pureBlack = true, latinNumerals = true, language = AppLanguage.PERSIAN)),
        )
        assertEquals(false, appearanceOf(UserPrefs(theme = ThemeMode.LIGHT)).dark)
        assertEquals("ar", appearanceOf(UserPrefs(language = AppLanguage.ARABIC)).languageTag)
        assertEquals("en", appearanceOf(UserPrefs(language = AppLanguage.ENGLISH)).languageTag)
    }

    @Test
    fun `a time, date or zone change makes the clock look again`() {
        var now = Instant.parse("2026-11-15T10:00:00Z")
        val wallClock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?): Clock = this
            override fun instant(): Instant = now
        }
        val scope = CoroutineScope(Job())
        try {
            // The boundary watcher never fires here: only refresh() can move the day.
            val clock = SystemDayClock(wallClock, { ZoneOffset.UTC }, emptyFlow(), scope, waitUntil = { awaitCancellation() })
            val receiver = ClockChangeReceiver(clock)
            assertEquals(LocalDate.of(2026, 11, 15), clock.todayFlow.value)

            for ((i, action) in listOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED).withIndex()) {
                now = Instant.parse("2026-11-${16 + i}T10:00:00Z")
                receiver.onReceive(ApplicationProvider.getApplicationContext(), Intent(action))
                assertEquals(action, LocalDate.of(2026, 11, 16 + i), clock.todayFlow.value)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the rank effect is for a threshold crossed today, once per rank`() {
        val detector = RankReachedDetector()
        val day = LocalDate.of(2026, 11, 15)

        assertNull("where the day stands when the screen opens", detector.onTotal(day, 12))
        assertNull(detector.onTotal(day, 49))
        assertEquals(Rank.DHAKIR, detector.onTotal(day, 50))
        assertNull("lowered", detector.onTotal(day, 40))
        assertNull("and raised again: not twice", detector.onTotal(day, 50))
        assertEquals("several thresholds at once: the highest", Rank.KHASHI, detector.onTotal(day, 250))
        assertNull("a new day starts over without an effect", detector.onTotal(day.plusDays(1), 0))
        assertEquals(Rank.NOT_HEEDLESS, detector.onTotal(day.plusDays(1), 10))
    }
}
