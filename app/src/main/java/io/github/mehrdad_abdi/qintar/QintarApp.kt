package io.github.mehrdad_abdi.qintar

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class QintarApp : Application() {
    @Inject
    lateinit var startup: AppStartup

    @Inject
    lateinit var foregroundDayCheck: ForegroundDayCheck

    @Inject
    lateinit var diagnostics: io.github.mehrdad_abdi.qintar.diagnostics.Diagnostics

    override fun onCreate() {
        super.onCreate()
        // First, so that a crash during start-up is recorded when the canary is on.
        diagnostics.installCrashHandler()
        diagnostics.log("app", "process started")
        startup.registerClockChanges(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundDayCheck)
        startup.start(migratedKhatmName = getString(R.string.khatm_migrated_name))
    }
}
