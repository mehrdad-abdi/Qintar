package io.github.mehrdad_abdi.qintar.feature.settings

import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.FakeLegacyMigration
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.migrationNoticeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The card on Today about carrying v1 data over (§5.10). */
class MigrationNoticeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun fixture(state: LegacyMigrationState) = DomainFixture(initialPrefs = UserPrefs(legacyMigrationState = state))

    @Test
    fun `nothing is shown unless data was carried over or the carrying failed`() {
        for (state in listOf(LegacyMigrationState.PENDING, LegacyMigrationState.NOT_NEEDED, LegacyMigrationState.ACKNOWLEDGED, LegacyMigrationState.IN_PROGRESS)) {
            assertEquals(state.name, MigrationNotice.Hidden, fixture(state).migrationNoticeViewModel().notice.value)
        }
    }

    @Test
    fun `a migrated user sees the card once, with the numbers while the report is known`() {
        val app = fixture(LegacyMigrationState.MIGRATED)
        val reports = MigrationReports()
        val migration = FakeLegacyMigration(app.prefs)
        val vm = app.migrationNoticeViewModel(migration, reports)

        assertEquals(MigrationNotice.Carried(null), vm.notice.value)

        reports.record(io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigrationOutcome.Migrated(migration.report, io.github.mehrdad_abdi.qintar.domain.backup.ImportResult(9, 0, 1, 0, 4, 2, 1)))
        assertEquals(MigrationNotice.Carried(migration.report), vm.notice.value)

        vm.dismiss()
        assertEquals(LegacyMigrationState.ACKNOWLEDGED, app.prefs.current.legacyMigrationState)
        assertEquals(MigrationNotice.Hidden, vm.notice.value)

        // It does not come back for a new view model (a later launch).
        assertEquals(MigrationNotice.Hidden, app.migrationNoticeViewModel().notice.value)
    }

    @Test
    fun `dismissing never touches a failed migration`() {
        val app = fixture(LegacyMigrationState.FAILED)
        val vm = app.migrationNoticeViewModel()
        vm.dismiss()
        assertEquals(LegacyMigrationState.FAILED, app.prefs.current.legacyMigrationState)
    }

    @Test
    fun `a failed migration offers a retry that succeeds and turns into the carried over card`() {
        val app = fixture(LegacyMigrationState.FAILED)
        val migration = FakeLegacyMigration(app.prefs)
        val vm = app.migrationNoticeViewModel(migration)
        assertEquals(MigrationNotice.Failed(retrying = false), vm.notice.value)

        vm.retry("Khatm")

        assertEquals(1, migration.retryCalls)
        assertEquals(LegacyMigrationState.MIGRATED, app.prefs.current.legacyMigrationState)
        val notice = vm.notice.value
        assertTrue(notice is MigrationNotice.Carried)
        assertEquals(migration.report, (notice as MigrationNotice.Carried).report)
    }

    @Test
    fun `a retry that fails again keeps the failure card and the old data is not touched`() {
        val app = fixture(LegacyMigrationState.FAILED)
        val migration = FakeLegacyMigration(app.prefs).apply { succeedOnRetry = false }
        val vm = app.migrationNoticeViewModel(migration)

        vm.retry("Khatm")
        assertEquals(MigrationNotice.Failed(retrying = false), vm.notice.value)
        assertEquals(LegacyMigrationState.FAILED, app.prefs.current.legacyMigrationState)
        assertEquals(0, runBlocking { app.log.allEvents().size })

        migration.succeedOnRetry = true
        vm.retry("Khatm")
        assertTrue(vm.notice.value is MigrationNotice.Carried)
    }
}
