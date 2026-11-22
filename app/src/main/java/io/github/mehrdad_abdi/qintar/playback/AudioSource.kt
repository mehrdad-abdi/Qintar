package io.github.mehrdad_abdi.qintar.playback

import androidx.annotation.StringRes
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import javax.inject.Inject
import kotlin.math.abs

/**
 * A reciter of the bundled, curated list (§7.8).
 *
 * @param bitrates the bitrates (kbps) the CDN serves for this reciter, ascending. Checked with a
 *   HEAD request per reciter and bitrate; other combinations answer 403.
 * @param ayahOneIncludesBasmala whether the file of ayah 1 of a surah already begins with the
 *   basmala. False for every reciter of the list (the files of 112:1 are shorter than 1:1), so the
 *   queue puts the basmala before those ayahs itself.
 */
data class Reciter(
    val id: String,
    @StringRes val name: Int,
    val bitrates: List<Int>,
    val ayahOneIncludesBasmala: Boolean = false,
) {
    /** The served bitrate nearest to [preferred]; the lower one when two are equally near. */
    fun bitrateFor(preferred: Int): Int = bitrates.minWith(compareBy<Int> { abs(it - preferred) }.thenBy { it })
}

object Reciters {
    val all: List<Reciter> = listOf(
        Reciter(UserPrefs.DEFAULT_RECITER, R.string.reciter_alafasy, listOf(64, 128)),
        Reciter("ar.abdulbasitmurattal", R.string.reciter_abdulbasit, listOf(64, 192)),
        Reciter("ar.husary", R.string.reciter_husary, listOf(64, 128)),
        Reciter("ar.minshawi", R.string.reciter_minshawi, listOf(128)),
        Reciter("ar.abdurrahmaansudais", R.string.reciter_sudais, listOf(64, 192)),
        Reciter("ar.mahermuaiqly", R.string.reciter_muaiqly, listOf(64, 128)),
        Reciter("ar.shaatree", R.string.reciter_shaatree, listOf(64, 128)),
        Reciter("ar.hudhaify", R.string.reciter_hudhaify, listOf(32, 64, 128)),
        Reciter("ar.parhizgar", R.string.reciter_parhizgar, listOf(48)),
    )

    /** The reciter with [id]; the default one for an id that is not (or no longer) in the list. */
    fun of(id: String): Reciter = all.firstOrNull { it.id == id } ?: all.first()
}

/** Where the audio of an ayah is (§7.8). Behind an interface so that the source can change. */
interface AudioSource {
    /**
     * @param reciterId an id of [Reciters]; an unknown one falls back to the default reciter.
     * @param quality the preferred bitrate; the nearest one the reciter has is used.
     */
    fun url(reciterId: String, ayah: AyahId, quality: Int): String
}

/**
 * The Islamic Network CDN: one MP3 per ayah, addressed by the global ayah number. The request
 * carries nothing but the reciter, the bitrate and the ayah.
 */
class IslamicNetworkAudioSource @Inject constructor() : AudioSource {
    override fun url(reciterId: String, ayah: AyahId, quality: Int): String {
        val reciter = Reciters.of(reciterId)
        return "$BASE/${reciter.bitrateFor(quality)}/${reciter.id}/${ayah.value}.mp3"
    }

    companion object {
        const val BASE = "https://cdn.islamic.network/quran/audio"
    }
}
