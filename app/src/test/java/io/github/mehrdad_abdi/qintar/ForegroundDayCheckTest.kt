package io.github.mehrdad_abdi.qintar

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.mehrdad_abdi.qintar.domain.testing.FakeDayClock
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Notes item 41: coming back to the app re-checks the reading day, which may have changed while the phone slept. */
class ForegroundDayCheckTest {

    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @Test
    fun `coming back to the app moves every screen to the new day`() {
        val clock = FakeDayClock()
        clock.asleep = true
        clock.advanceDays(1)
        assertEquals(LocalDate.of(2026, 11, 15), clock.todayFlow.value)

        ForegroundDayCheck(clock).onStart(owner)

        assertEquals(LocalDate.of(2026, 11, 16), clock.todayFlow.value)
    }
}
