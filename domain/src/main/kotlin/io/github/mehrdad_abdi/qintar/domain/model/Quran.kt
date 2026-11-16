package io.github.mehrdad_abdi.qintar.domain.model

/** Fixed dimensions of the Madinah mushaf in the Hafs count (§7.4). */
object Quran {
    const val AYAH_COUNT = 6236
    const val SURAH_COUNT = 114
    const val PAGE_COUNT = 604
    const val JUZ_COUNT = 30
    const val HIZB_QUARTER_COUNT = 240
}

/** Global ayah number, 1…6236 in mushaf order. The only ayah key used in storage (§4.1). */
@JvmInline
value class AyahId(val value: Int) : Comparable<AyahId> {
    init {
        require(value in 1..Quran.AYAH_COUNT) { "AyahId out of range: $value" }
    }

    override fun compareTo(other: AyahId): Int = value.compareTo(other.value)

    /** The following ayah, or null after the last ayah of the Quran. */
    fun next(): AyahId? = if (value < Quran.AYAH_COUNT) AyahId(value + 1) else null

    /** The preceding ayah, or null before the first. */
    fun previous(): AyahId? = if (value > 1) AyahId(value - 1) else null

    companion object {
        val FIRST = AyahId(1)
        val LAST = AyahId(Quran.AYAH_COUNT)

        fun orNull(value: Int): AyahId? = if (value in 1..Quran.AYAH_COUNT) AyahId(value) else null
    }
}

/** `(surah, ayah)` for display and input only; whether the ayah exists is answered by QuranIndex. */
data class AyahRef(val surah: Int, val ayah: Int) {
    init {
        require(surah in 1..Quran.SURAH_COUNT) { "Surah out of range: $surah" }
        require(ayah >= 1) { "Ayah number must be positive: $ayah" }
    }
}

/** Inclusive, contiguous run of ayahs. A surah, page, juz or bookmark is an AyahRange. */
data class AyahRange(val start: AyahId, val end: AyahId) : Iterable<AyahId> {
    init {
        require(start <= end) { "Range end ${end.value} precedes start ${start.value}" }
    }

    val size: Int get() = end.value - start.value + 1

    /** The raw ids, for loops that should not box. */
    val ids: IntRange get() = start.value..end.value

    operator fun contains(ayah: AyahId): Boolean = ayah.value in ids

    fun intersect(other: AyahRange): AyahRange? {
        val from = maxOf(start.value, other.start.value)
        val to = minOf(end.value, other.end.value)
        return if (from <= to) AyahRange(AyahId(from), AyahId(to)) else null
    }

    override fun iterator(): Iterator<AyahId> = ids.asSequence().map(::AyahId).iterator()

    companion object {
        val ALL = AyahRange(AyahId.FIRST, AyahId.LAST)

        fun of(start: Int, end: Int) = AyahRange(AyahId(start), AyahId(end))

        fun single(ayah: AyahId) = AyahRange(ayah, ayah)
    }
}

enum class Revelation { MECCAN, MEDINAN }

/** Stored as 0, 1, 2 in the Quran database (§7.4). */
enum class SajdaType { NONE, RECOMMENDED, OBLIGATORY }

data class SurahInfo(
    val number: Int,
    val nameAr: String,
    val nameLatin: String,
    val nameEn: String,
    val revelation: Revelation,
    val ayahCount: Int,
    val firstAyahId: AyahId,
    /** False for surah 1 (the basmala is its ayah 1) and surah 9 (none). The basmala row is never an ayah. */
    val hasBasmala: Boolean,
) {
    val range: AyahRange get() = AyahRange(firstAyahId, AyahId(firstAyahId.value + ayahCount - 1))
}
