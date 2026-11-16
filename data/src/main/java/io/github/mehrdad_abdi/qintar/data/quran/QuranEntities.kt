package io.github.mehrdad_abdi.qintar.data.quran

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One surah of the bundled, read-only Quran database (DESIGN.md §7.4).
 * Rows are produced by `tools/quran-data/build_quran_db.py`; never written by the app.
 */
@Entity(tableName = "surah")
data class SurahEntity(
    @PrimaryKey @ColumnInfo(name = "number") val number: Int,
    /** Arabic name without the "سورة" prefix, e.g. الفاتحة. */
    @ColumnInfo(name = "name_ar") val nameAr: String,
    /** Latin transliteration as given by Tanzil metadata, e.g. Al-Faatiha. */
    @ColumnInfo(name = "name_latin") val nameLatin: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    /** "MECCAN" or "MEDINAN". */
    @ColumnInfo(name = "revelation") val revelation: String,
    @ColumnInfo(name = "ayah_count") val ayahCount: Int,
    @ColumnInfo(name = "first_ayah_id") val firstAyahId: Int,
    /** False for surah 1 (where the basmala is ayah 1) and surah 9 (no basmala). */
    @ColumnInfo(name = "has_basmala") val hasBasmala: Boolean,
)

/** One ayah. [id] runs 1..6236 in Mushaf order. */
@Entity(
    tableName = "ayah",
    indices = [
        Index(value = ["page"], name = "ayah_page"),
        Index(value = ["surah", "number"], name = "ayah_ref", unique = true),
    ],
)
data class AyahEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "surah") val surah: Int,
    /** Number within the surah. */
    @ColumnInfo(name = "number") val number: Int,
    /** Tanzil Uthmani text, verbatim, never prefixed with the basmala. */
    @ColumnInfo(name = "text") val text: String,
    /** Page of the Madinah mushaf, 1..604. */
    @ColumnInfo(name = "page") val page: Int,
    @ColumnInfo(name = "juz") val juz: Int,
    @ColumnInfo(name = "hizb_quarter") val hizbQuarter: Int,
    /** [SAJDA_NONE], [SAJDA_RECOMMENDED] or [SAJDA_OBLIGATORY]. */
    @ColumnInfo(name = "sajda") val sajda: Int,
) {
    companion object {
        const val SAJDA_NONE = 0
        const val SAJDA_RECOMMENDED = 1
        const val SAJDA_OBLIGATORY = 2
    }
}

/** Every column of an ayah except its text: what an in-memory structural index needs. */
data class AyahStructure(
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "surah") val surah: Int,
    @ColumnInfo(name = "number") val number: Int,
    @ColumnInfo(name = "page") val page: Int,
    @ColumnInfo(name = "juz") val juz: Int,
    @ColumnInfo(name = "hizb_quarter") val hizbQuarter: Int,
    @ColumnInfo(name = "sajda") val sajda: Int,
)

/** The text of one ayah, keyed by its global id. */
data class AyahText(
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "text") val text: String,
)
