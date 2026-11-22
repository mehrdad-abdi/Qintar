package io.github.mehrdad_abdi.qintar.data.quran

import androidx.room.Dao
import androidx.room.Query

/** Read-only queries over the bundled Quran database. Every list is in ayah/surah order. */
@Dao
interface QuranDao {

    @Query("SELECT * FROM surah ORDER BY number")
    suspend fun surahs(): List<SurahEntity>

    /** All 6,236 ayahs without their text, in id order, for building an in-memory index. */
    @Query("SELECT id, surah, number, page, juz, hizb_quarter, sajda FROM ayah ORDER BY id")
    suspend fun ayahStructures(): List<AyahStructure>

    /** Texts of the ayahs on one page of the Madinah mushaf; empty for a page outside 1..604. */
    @Query("SELECT id, text FROM ayah WHERE page = :page ORDER BY id")
    suspend fun ayahTextsOnPage(page: Int): List<AyahText>

    /** Texts of ayahs [fromId]..[toId], both inclusive. */
    @Query("SELECT id, text FROM ayah WHERE id BETWEEN :fromId AND :toId ORDER BY id")
    suspend fun ayahTexts(fromId: Int, toId: Int): List<AyahText>

    /** One ayah with all its columns, or null if [id] is outside 1..6236. */
    @Query("SELECT * FROM ayah WHERE id = :id")
    suspend fun ayah(id: Int): AyahEntity?
}
