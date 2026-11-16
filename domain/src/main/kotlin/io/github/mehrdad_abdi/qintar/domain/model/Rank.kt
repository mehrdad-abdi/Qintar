package io.github.mehrdad_abdi.qintar.domain.model

/**
 * The hadith ladder (§4.4). Names are stable but stored nowhere: a rank is always derived from a day
 * total. [NONE] is the absence of a rank, not a rank with a name.
 */
enum class Rank(val threshold: Int) {
    NONE(0),
    NOT_HEEDLESS(10),
    DHAKIR(50),
    QANIT(100),
    KHASHI(200),
    FAIZ(300),
    MUJTAHID(500),
    QINTAR(1000);

    /** The rank above this one, or null at the top of the ladder. */
    val next: Rank? get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** The seven real ranks, lowest first. */
        val ladder: List<Rank> = entries.drop(1)
    }
}
