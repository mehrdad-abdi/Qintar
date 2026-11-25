package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo

/**
 * Finds surahs by what someone typed of their name, for "Go to" (owner, 2026-11-25): the Arabic name
 * in Arabic or Persian letters, the transliteration, or the English meaning. Vowel marks, the
 * article and letter variants do not matter, and in English letters the consonants alone are enough
 * ("ysn" is Yaseen), since people mostly type the consonants.
 */
object SurahNames {
    /** The matching surahs, closest first: whole name, then start of a name, then consonants, then any part. */
    fun search(query: String, surahs: List<SurahInfo>): List<SurahInfo> {
        val arabic = query.any { it in '؀'..'ۿ' }
        val q = if (arabic) arabicKey(query) else latinKey(query)
        if (q.isEmpty()) return emptyList()
        val qShort = if (arabic) q else withoutArticle(q)
        val qSkeleton = if (arabic) "" else skeleton(qShort)
        return surahs.mapNotNull { surah ->
            val keys = if (arabic) {
                arabicKey(surah.nameAr).let { listOf(it, it.removePrefix("ال")) }
            } else {
                listOf(latinKey(surah.nameLatin), latinKey(surah.nameEn)).flatMap { listOf(it, withoutArticle(it)) }
            }
            score(q, qShort, qSkeleton, keys)?.let { surah to it }
        }.sortedWith(compareBy({ it.second }, { it.first.number })).map { it.first }
    }

    private fun score(q: String, qShort: String, qSkeleton: String, keys: List<String>): Int? {
        val queries = listOf(q, qShort).distinct()
        if (keys.any { it in queries }) return 0
        // One letter is a whole name or nothing (ص and ق are surahs; s, p and j are Go to prefixes).
        if (q.length < 2) return null
        if (keys.any { k -> queries.any { k.startsWith(it) } }) return 1
        if (qSkeleton.length >= 2) {
            val skeletons = keys.map(::skeleton)
            if (qSkeleton in skeletons) return 2
            if (skeletons.any { it.startsWith(qSkeleton) }) return 3
        }
        if (q.length >= 3 && keys.any { k -> queries.any { k.contains(it) } }) return 4
        return null
    }

    /** Arabic or Persian letters without vowel marks, Quranic signs, tatweel or spaces; one form per letter. */
    private fun arabicKey(text: String): String = buildString {
        for (c in text) {
            when (c) {
                in 'ً'..'ٟ', 'ٰ', in 'ۖ'..'ۭ', 'ـ', '‌' -> Unit
                'ي', 'ى', 'ئ' -> append('ی')
                'ك' -> append('ک')
                'ة' -> append('ه')
                'أ', 'إ', 'آ', 'ٱ' -> append('ا')
                'ؤ' -> append('و')
                else -> if (c in '؀'..'ۿ') append(c)
            }
        }
    }

    /** Lower-case Latin letters only: "Al-Kahf" → "alkahf". */
    private fun latinKey(text: String): String = text.lowercase().filter { it in 'a'..'z' }

    /** Without a leading article: "alkahf" → "kahf", "annaas" → "naas", "thecow" → "cow". */
    private fun withoutArticle(key: String): String {
        for (article in ARTICLES) {
            if (key.length > article.length + 1 && key.startsWith(article)) return key.removePrefix(article)
        }
        return key
    }

    private val ARTICLES = listOf("the", "ash", "adh", "al", "an", "ar", "as", "at", "ad", "az")

    /** The consonants, each run of one letter counted once: "muzzammil" → "mzml", "yaseen" → "ysn". */
    private fun skeleton(key: String): String = buildString {
        for (c in key) {
            if (c in "aeiou") continue
            if (isEmpty() || last() != c) append(c)
        }
    }
}
