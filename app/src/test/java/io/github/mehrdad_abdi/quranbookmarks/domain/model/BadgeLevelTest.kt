package io.github.mehrdad_abdi.quranbookmarks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeLevelTest {

    @Test
    fun `fromAyahCount returns NONE for zero ayahs`() {
        val result = BadgeLevel.fromAyahCount(0)
        assertEquals(BadgeLevel.NONE, result)
    }

    @Test
    fun `fromAyahCount returns NONE for counts below threshold`() {
        val result = BadgeLevel.fromAyahCount(9)
        assertEquals(BadgeLevel.NONE, result)
    }

    @Test
    fun `fromAyahCount returns GHAIR_GHAFIL for 10 ayahs`() {
        val result = BadgeLevel.fromAyahCount(10)
        assertEquals(BadgeLevel.GHAIR_GHAFIL, result)
    }

    @Test
    fun `fromAyahCount returns GHAIR_GHAFIL for counts between 10 and 49`() {
        val result = BadgeLevel.fromAyahCount(25)
        assertEquals(BadgeLevel.GHAIR_GHAFIL, result)
    }

    @Test
    fun `fromAyahCount returns DHAKIR for 50 ayahs`() {
        val result = BadgeLevel.fromAyahCount(50)
        assertEquals(BadgeLevel.DHAKIR, result)
    }

    @Test
    fun `fromAyahCount returns QANIT for 100 ayahs`() {
        val result = BadgeLevel.fromAyahCount(100)
        assertEquals(BadgeLevel.QANIT, result)
    }

    @Test
    fun `fromAyahCount returns KHASHIE for 200 ayahs`() {
        val result = BadgeLevel.fromAyahCount(200)
        assertEquals(BadgeLevel.KHASHIE, result)
    }

    @Test
    fun `fromAyahCount returns FAEZ for 300 ayahs`() {
        val result = BadgeLevel.fromAyahCount(300)
        assertEquals(BadgeLevel.FAEZ, result)
    }

    @Test
    fun `fromAyahCount returns MUJTAHID for 500 ayahs`() {
        val result = BadgeLevel.fromAyahCount(500)
        assertEquals(BadgeLevel.MUJTAHID, result)
    }

    @Test
    fun `fromAyahCount returns SAHIB_QANTAR for 1000 ayahs`() {
        val result = BadgeLevel.fromAyahCount(1000)
        assertEquals(BadgeLevel.SAHIB_QANTAR, result)
    }

    @Test
    fun `fromAyahCount returns highest badge for very large counts`() {
        val result = BadgeLevel.fromAyahCount(5000)
        assertEquals(BadgeLevel.SAHIB_QANTAR, result)
    }

    @Test
    fun `getAllLevels returns all badges except NONE`() {
        val levels = BadgeLevel.getAllLevels()
        assertEquals(7, levels.size)
        assertEquals(false, levels.contains(BadgeLevel.NONE))
    }

    @Test
    fun `getNextLevel returns correct next level`() {
        assertEquals(BadgeLevel.DHAKIR, BadgeLevel.getNextLevel(BadgeLevel.GHAIR_GHAFIL))
        assertEquals(BadgeLevel.QANIT, BadgeLevel.getNextLevel(BadgeLevel.DHAKIR))
        assertEquals(BadgeLevel.KHASHIE, BadgeLevel.getNextLevel(BadgeLevel.QANIT))
        assertEquals(BadgeLevel.FAEZ, BadgeLevel.getNextLevel(BadgeLevel.KHASHIE))
        assertEquals(BadgeLevel.MUJTAHID, BadgeLevel.getNextLevel(BadgeLevel.FAEZ))
        assertEquals(BadgeLevel.SAHIB_QANTAR, BadgeLevel.getNextLevel(BadgeLevel.MUJTAHID))
    }

    @Test
    fun `getNextLevel returns null for highest level`() {
        val result = BadgeLevel.getNextLevel(BadgeLevel.SAHIB_QANTAR)
        assertNull(result)
    }

    @Test
    fun `getAyahsToNextLevel calculates correctly`() {
        val level = BadgeLevel.GHAIR_GHAFIL // threshold: 10, next: 50
        val ayahsNeeded = level.getAyahsToNextLevel(15)
        assertEquals(35, ayahsNeeded) // 50 - 15 = 35
    }

    @Test
    fun `getAyahsToNextLevel returns null for highest level`() {
        val level = BadgeLevel.SAHIB_QANTAR
        val result = level.getAyahsToNextLevel(1000)
        assertNull(result)
    }

    @Test
    fun `getDisplayString returns formatted string for badges with emoji`() {
        assertEquals("🔹 غیر غافل", BadgeLevel.GHAIR_GHAFIL.getDisplayString())
        assertEquals("🔺 ذاکر", BadgeLevel.DHAKIR.getDisplayString())
        assertEquals("🥉 قانت", BadgeLevel.QANIT.getDisplayString())
        assertEquals("🥈 خاشع", BadgeLevel.KHASHIE.getDisplayString())
        assertEquals("🥇 فائز", BadgeLevel.FAEZ.getDisplayString())
        assertEquals("🎖️ مجتهد", BadgeLevel.MUJTAHID.getDisplayString())
        assertEquals("👑 صاحب القنطار", BadgeLevel.SAHIB_QANTAR.getDisplayString())
    }

    @Test
    fun `getDisplayString returns displayName for NONE`() {
        assertEquals("No Badge", BadgeLevel.NONE.getDisplayString())
    }

    @Test
    fun `badge thresholds are correctly defined`() {
        assertEquals(0, BadgeLevel.NONE.threshold)
        assertEquals(10, BadgeLevel.GHAIR_GHAFIL.threshold)
        assertEquals(50, BadgeLevel.DHAKIR.threshold)
        assertEquals(100, BadgeLevel.QANIT.threshold)
        assertEquals(200, BadgeLevel.KHASHIE.threshold)
        assertEquals(300, BadgeLevel.FAEZ.threshold)
        assertEquals(500, BadgeLevel.MUJTAHID.threshold)
        assertEquals(1000, BadgeLevel.SAHIB_QANTAR.threshold)
    }
}
