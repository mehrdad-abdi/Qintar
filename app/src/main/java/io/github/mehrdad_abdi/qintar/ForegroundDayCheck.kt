package io.github.mehrdad_abdi.qintar

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-checks the reading day whenever the app comes to the foreground. While the phone sleeps the
 * clock's boundary wait (a coroutine delay, which runs on uptime) does not advance, and the
 * DATE_CHANGED broadcast does not reach a frozen app, so Today could show yesterday in the morning
 * (notes item 41). Observes the process lifecycle (`ProcessLifecycleOwner`).
 */
@Singleton
class ForegroundDayCheck @Inject constructor(private val clock: DayClock) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = clock.refresh()
}
