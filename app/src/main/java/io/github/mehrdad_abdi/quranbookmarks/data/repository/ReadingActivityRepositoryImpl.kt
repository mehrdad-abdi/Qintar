package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.ReadingActivityDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.ReadingActivityEntity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.ReadingActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Implementation of ReadingActivityRepository.
 * Handles mapping between entity and domain models.
 */
class ReadingActivityRepositoryImpl @Inject constructor(
    private val dao: ReadingActivityDao
) : ReadingActivityRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override suspend fun saveActivity(activity: ReadingActivity) {
        dao.upsert(activity.toEntity())
    }

    override suspend fun getActivityByDate(date: LocalDate): ReadingActivity? {
        return dao.getByDate(date.format(dateFormatter))?.toDomainModel()
    }

    override fun getActivityByDateFlow(date: LocalDate): Flow<ReadingActivity?> {
        return dao.getByDateFlow(date.format(dateFormatter))
            .map { it?.toDomainModel() }
    }

    override suspend fun getActivitiesByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ReadingActivity> {
        return dao.getByDateRange(
            startDate.format(dateFormatter),
            endDate.format(dateFormatter)
        ).map { it.toDomainModel() }
    }

    override suspend fun getAllActivities(): List<ReadingActivity> {
        return dao.getAll().map { it.toDomainModel() }
    }

    override suspend fun getTotalAyahsAllTime(): Int {
        return dao.getTotalAyahsAllTime() ?: 0
    }

    override suspend fun getMaxAyahsInSingleDay(): Int {
        return dao.getMaxAyahsInSingleDay() ?: 0
    }

    override suspend fun getBestDay(): ReadingActivity? {
        return dao.getBestDay()?.toDomainModel()
    }

    override suspend fun getDaysWithMinimumAyahs(minimumAyahs: Int): Int {
        return dao.getDaysWithMinimumAyahs(minimumAyahs)
    }

    override suspend fun deleteActivityByDate(date: LocalDate) {
        dao.deleteByDate(date.format(dateFormatter))
    }

    override suspend fun deleteAllActivities() {
        dao.deleteAll()
    }

    // Mapping functions

    private fun ReadingActivity.toEntity(): ReadingActivityEntity {
        return ReadingActivityEntity(
            date = date.format(dateFormatter),
            totalAyahsRead = totalAyahsRead,
            trackedAyahIds = trackedAyahIds.toJsonArray(),
            badgeLevel = badgeLevel.name
        )
    }

    private fun ReadingActivityEntity.toDomainModel(): ReadingActivity {
        return ReadingActivity(
            date = LocalDate.parse(date, dateFormatter),
            totalAyahsRead = totalAyahsRead,
            trackedAyahIds = trackedAyahIds.parseJsonArray(),
            badgeLevel = try {
                BadgeLevel.valueOf(badgeLevel)
            } catch (e: IllegalArgumentException) {
                BadgeLevel.NONE
            }
        )
    }

    private fun Set<String>.toJsonArray(): String {
        val jsonArray = JSONArray()
        forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    private fun String.parseJsonArray(): Set<String> {
        return try {
            val jsonArray = JSONArray(this)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                set.add(jsonArray.getString(i))
            }
            set
        } catch (e: Exception) {
            emptySet()
        }
    }
}
