package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure.InvalidInput.Field
import io.github.mehrdad_abdi.qintar.domain.model.Khatm
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.TransactionRunner
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import javax.inject.Inject

/** Creating and editing khatms (§4.6, §5.5). Reading in a khatm goes through `ReadingTracker`. */
class KhatmManager @Inject constructor(
    private val khatms: KhatmStore,
    private val clock: DayClock,
    private val tx: TransactionRunner,
) {
    /** The ordinal for the default name of the next khatm ("Khatm 2"); the UI localizes the word. */
    suspend fun nextOrdinal(): Int = khatms.khatms().size + 1

    /**
     * Starts a khatm today.
     *
     * @param startFrom the first ayah still to be read; everything before it counts as covered without
     *   events (R-KHT-4). Null = from the beginning.
     */
    suspend fun start(name: String, startFrom: AyahId? = null, dailyPages: Float? = null): Khatm {
        val cleanName = name.trim().ifEmpty { throw DomainFailure.InvalidInput(Field.NAME) }
        if (dailyPages != null && !(dailyPages > 0f)) throw DomainFailure.InvalidInput(Field.DAILY_PAGES)
        val khatm = Khatm(
            id = 0,
            name = cleanName,
            startedDay = clock.today(),
            dailyPages = dailyPages,
            assumedCoveredUpTo = (startFrom?.value ?: 1) - 1,
            completedAt = null,
        )
        return khatm.copy(id = khatms.insert(khatm, clock.now()))
    }

    suspend fun rename(id: Long, name: String) {
        val cleanName = name.trim().ifEmpty { throw DomainFailure.InvalidInput(Field.NAME) }
        edit(id) { it.copy(name = cleanName) }
    }

    /** Null removes the goal. */
    suspend fun setDailyPages(id: Long, dailyPages: Float?) {
        if (dailyPages != null && !(dailyPages > 0f)) throw DomainFailure.InvalidInput(Field.DAILY_PAGES)
        edit(id) { it.copy(dailyPages = dailyPages) }
    }

    /** Removes the khatm only. Its readings stay in the log and in every day total (§4.2). */
    suspend fun delete(id: Long) {
        khatms.delete(id)
    }

    private suspend fun edit(id: Long, change: (Khatm) -> Khatm) {
        tx.inTransaction {
            val khatm = khatms.khatm(id) ?: throw DomainFailure.KhatmNotFound(id)
            khatms.update(change(khatm))
        }
    }
}
