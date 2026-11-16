package io.github.mehrdad_abdi.qintar.feature.onboarding

import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.startDestinationOf
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.onboardingViewModel
import io.github.mehrdad_abdi.qintar.ui.navigation.OnboardingRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.TodayRoute
import java.time.LocalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** First-launch tour (§5.10). */
class OnboardingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `a new install starts on onboarding, and after it is finished never again`() = runTest {
        val app = DomainFixture()
        assertEquals(OnboardingRoute, startDestinationOf(app.prefs))

        val vm = app.onboardingViewModel()
        vm.finish()

        assertTrue(vm.state.value.finished)
        assertTrue(app.prefs.current.onboardingDone)
        assertEquals(TodayRoute, startDestinationOf(app.prefs))
    }

    @Test
    fun `skipping on the first page counts as done and keeps nothing else`() = runTest {
        val app = DomainFixture()
        val vm = app.onboardingViewModel()

        vm.finish()

        assertEquals(UserPrefs().copy(onboardingDone = true), app.prefs.current)
        assertEquals(TodayRoute, startDestinationOf(app.prefs))
    }

    @Test
    fun `the full tour has three pages that can be walked both ways`() {
        val vm = DomainFixture().onboardingViewModel()
        assertEquals(OnboardingMode.FULL, vm.state.value.mode)
        assertEquals(listOf(OnboardingPage.RANKS, OnboardingPage.LANGUAGE, OnboardingPage.GOAL_AND_REMINDER), vm.state.value.pages)
        assertEquals(OnboardingPage.RANKS, vm.state.value.page)
        assertFalse(vm.state.value.isLast)

        vm.next()
        assertEquals(OnboardingPage.LANGUAGE, vm.state.value.page)
        vm.next()
        assertTrue(vm.state.value.isLast)
        vm.next()
        assertEquals("cannot go past the end", OnboardingPage.GOAL_AND_REMINDER, vm.state.value.page)
        vm.back()
        assertEquals(OnboardingPage.LANGUAGE, vm.state.value.page)
        vm.back()
        vm.back()
        assertEquals(0, vm.state.value.pageIndex)
    }

    @Test
    fun `language, goal and reminder are saved as they are chosen`() {
        val app = DomainFixture()
        val vm = app.onboardingViewModel()

        vm.setLanguage(AppLanguage.ARABIC)
        vm.setGoal(Rank.QANIT)
        vm.setReminderEnabled(true)
        vm.setReminderTime(LocalTime.of(5, 30))

        val p = app.prefs.current
        assertEquals(AppLanguage.ARABIC, p.language)
        assertEquals(Rank.QANIT, p.goalRank)
        assertTrue(p.reminderEnabled)
        assertEquals(LocalTime.of(5, 30), p.reminderTime)
        assertFalse("choosing is not finishing", p.onboardingDone)
        assertEquals(Rank.QANIT, vm.state.value.goal)

        vm.setGoal(null)
        assertEquals(null, app.prefs.current.goalRank)
    }

    @Test
    fun `someone whose v1 data was carried over sees one what's new page`() {
        for (state in listOf(LegacyMigrationState.MIGRATED, LegacyMigrationState.ACKNOWLEDGED)) {
            val vm = DomainFixture(initialPrefs = UserPrefs(legacyMigrationState = state)).onboardingViewModel()
            assertEquals(OnboardingMode.WHATS_NEW, vm.state.value.mode)
            assertEquals(listOf(OnboardingPage.WHATS_NEW), vm.state.value.pages)
            assertTrue(vm.state.value.isLast)
        }
        val fresh = DomainFixture(initialPrefs = UserPrefs(legacyMigrationState = LegacyMigrationState.NOT_NEEDED)).onboardingViewModel()
        assertEquals(OnboardingMode.FULL, fresh.state.value.mode)
    }

    @Test
    fun `the start waits for the migration to settle, and an already finished tour is not delayed`() = runTest {
        val app = DomainFixture(initialPrefs = UserPrefs(legacyMigrationState = LegacyMigrationState.PENDING))
        val decided = async { startDestinationOf(app.prefs) }
        app.prefs.update { it.copy(legacyMigrationState = LegacyMigrationState.MIGRATED) }
        assertEquals(OnboardingRoute, decided.await())

        val done = DomainFixture(initialPrefs = UserPrefs(onboardingDone = true, legacyMigrationState = LegacyMigrationState.PENDING))
        assertEquals(TodayRoute, startDestinationOf(done.prefs))
    }
}
