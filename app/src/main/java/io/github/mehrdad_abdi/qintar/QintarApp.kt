package io.github.mehrdad_abdi.qintar

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class QintarApp : Application() {
    @Inject
    lateinit var startup: AppStartup

    override fun onCreate() {
        super.onCreate()
        startup.registerClockChanges(this)
        startup.start(migratedKhatmName = getString(R.string.khatm_migrated_name))
    }
}
