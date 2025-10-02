package io.github.mehrdad_abdi.quranbookmarks.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReadingActivityTest {

    private val testDate = LocalDate.of(2025, 9, 30)

    @Test
    fun `create generates activity with correct badge level`() {
        val activity = ReadingActivity.create(
            date = testDate,
            totalAyahsRead = 100,
            trackedAyahIds = setOf("1:2:255", "1:2:256")
        )

        assertEquals(testDate, activity.date)
        assertEquals(100, activity.totalAyahsRead)
        assertEquals(2, activity.trackedAyahIds.size)
        assertEquals(BadgeLevel.QANIT, activity.badgeLevel)
    }

    @Test
    fun `empty creates activity with zero ayahs and NONE badge`() {
        val activity = ReadingActivity.empty(testDate)

        assertEquals(testDate, activity.date)
        assertEquals(0, activity.totalAyahsRead)
        assertTrue(activity.trackedAyahIds.isEmpty())
        assertEquals(BadgeLevel.NONE, activity.badgeLevel)
    }

    @Test
    fun `toggleAyahTracking adds ayah when not present`() {
        val activity = ReadingActivity.empty(testDate)
        val updated = activity.toggleAyahTracking("1:2:255")

        assertEquals(1, updated.totalAyahsRead)
        assertTrue(updated.trackedAyahIds.contains("1:2:255"))
        assertEquals(BadgeLevel.NONE, updated.badgeLevel) // < 10 ayahs
    }

    @Test
    fun `toggleAyahTracking removes ayah when present`() {
        val activity = ReadingActivity.create(
            date = testDate,
            totalAyahsRead = 2,
            trackedAyahIds = setOf("1:2:255", "1:2:256")
        )
        val updated = activity.toggleAyahTracking("1:2:255")

        assertEquals(1, updated.totalAyahsRead)
        assertFalse(updated.trackedAyahIds.contains("1:2:255"))
        assertTrue(updated.trackedAyahIds.contains("1:2:256"))
    }

    @Test
    fun `toggleAyahTracking updates badge level when threshold crossed`() {
        // Start with 9 ayahs (NONE badge)
        val ids = (1..9).map { "1:2:$it" }.toSet()
        val activity = ReadingActivity.create(testDate, 9, ids)
        assertEquals(BadgeLevel.NONE, activity.badgeLevel)

        // Add 10th ayah -> should become GHAIR_GHAFIL
        val updated = activity.toggleAyahTracking("1:2:10")
        assertEquals(10, updated.totalAyahsRead)
        assertEquals(BadgeLevel.GHAIR_GHAFIL, updated.badgeLevel)
    }

    @Test
    fun `addAyah adds new ayah and updates count`() {
        val activity = ReadingActivity.empty(testDate)
        val updated = activity.addAyah("1:2:255")

        assertEquals(1, updated.totalAyahsRead)
        assertTrue(updated.isAyahTracked("1:2:255"))
    }

    @Test
    fun `addAyah does not duplicate existing ayah`() {
        val activity = ReadingActivity.create(
            testDate,
            1,
            setOf("1:2:255")
        )
        val updated = activity.addAyah("1:2:255")

        assertEquals(1, updated.totalAyahsRead)
        assertEquals(activity, updated) // No change
    }

    @Test
    fun `removeAyah removes existing ayah and updates count`() {
        val activity = ReadingActivity.create(
            testDate,
            2,
            setOf("1:2:255", "1:2:256")
        )
        val updated = activity.removeAyah("1:2:255")

        assertEquals(1, updated.totalAyahsRead)
        assertFalse(updated.isAyahTracked("1:2:255"))
        assertTrue(updated.isAyahTracked("1:2:256"))
    }

    @Test
    fun `removeAyah does nothing if ayah not present`() {
        val activity = ReadingActivity.create(
            testDate,
            1,
            setOf("1:2:255")
        )
        val updated = activity.removeAyah("1:2:256")

        assertEquals(activity, updated) // No change
    }

    @Test
    fun `isAyahTracked returns true for tracked ayah`() {
        val activity = ReadingActivity.create(
            testDate,
            1,
            setOf("1:2:255")
        )

        assertTrue(activity.isAyahTracked("1:2:255"))
        assertFalse(activity.isAyahTracked("1:2:256"))
    }

    @Test
    fun `getAyahsToNextLevel returns correct count`() {
        val activity = ReadingActivity.create(
            testDate,
            15,
            setOf() // Badge: GHAIR_GHAFIL (10-49)
        )

        val ayahsNeeded = activity.getAyahsToNextLevel()
        assertEquals(35, ayahsNeeded) // 50 - 15 = 35 to reach DHAKIR
    }

    @Test
    fun `getAyahsToNextLevel returns null at max level`() {
        val activity = ReadingActivity.create(
            testDate,
            1000,
            setOf() // Badge: SAHIB_QANTAR
        )

        assertNull(activity.getAyahsToNextLevel())
    }

    @Test
    fun `getNextBadgeLevel returns correct next level`() {
        val activity = ReadingActivity.create(
            testDate,
            25,
            setOf() // Badge: GHAIR_GHAFIL
        )

        assertEquals(BadgeLevel.DHAKIR, activity.getNextBadgeLevel())
    }

    @Test
    fun `getNextBadgeLevel returns null at max level`() {
        val activity = ReadingActivity.create(
            testDate,
            1000,
            setOf() // Badge: SAHIB_QANTAR
        )

        assertNull(activity.getNextBadgeLevel())
    }

    @Test
    fun `badge level updates correctly across all thresholds`() {
        var activity = ReadingActivity.empty(testDate)
        assertEquals(BadgeLevel.NONE, activity.badgeLevel)

        // Add ayahs one by one and check badge updates
        val testCases = listOf(
            9 to BadgeLevel.NONE,
            10 to BadgeLevel.GHAIR_GHAFIL,
            49 to BadgeLevel.GHAIR_GHAFIL,
            50 to BadgeLevel.DHAKIR,
            99 to BadgeLevel.DHAKIR,
            100 to BadgeLevel.QANIT,
            199 to BadgeLevel.QANIT,
            200 to BadgeLevel.KHASHIE,
            299 to BadgeLevel.KHASHIE,
            300 to BadgeLevel.FAEZ,
            499 to BadgeLevel.FAEZ,
            500 to BadgeLevel.MUJTAHID,
            999 to BadgeLevel.MUJTAHID,
            1000 to BadgeLevel.SAHIB_QANTAR
        )

        testCases.forEach { (count, expectedBadge) ->
            val ids = (1..count).map { "1:2:$it" }.toSet()
            activity = ReadingActivity.create(testDate, count, ids)
            assertEquals("Failed at count $count", expectedBadge, activity.badgeLevel)
        }
    }
}
