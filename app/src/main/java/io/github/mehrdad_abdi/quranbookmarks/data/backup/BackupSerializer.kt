package io.github.mehrdad_abdi.quranbookmarks.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupData
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles serialization and deserialization of backup data to/from JSON.
 */
@Singleton
class BackupSerializer @Inject constructor() {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateSerializer())
        .registerTypeAdapter(LocalDate::class.java, LocalDateDeserializer())
        .registerTypeAdapter(ReadingActivity::class.java, ReadingActivitySerializer())
        .registerTypeAdapter(ReadingActivity::class.java, ReadingActivityDeserializer())
        .setPrettyPrinting()
        .create()

    /**
     * Serialize backup data to JSON string.
     */
    fun serialize(backupData: BackupData): String {
        return gson.toJson(backupData)
    }

    /**
     * Deserialize JSON string to backup data.
     */
    fun deserialize(json: String): BackupData {
        return gson.fromJson(json, BackupData::class.java)
    }

    /**
     * Custom serializer for LocalDate.
     */
    private class LocalDateSerializer : JsonSerializer<LocalDate> {
        override fun serialize(
            src: LocalDate,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            return context.serialize(src.format(DateTimeFormatter.ISO_LOCAL_DATE))
        }
    }

    /**
     * Custom deserializer for LocalDate.
     */
    private class LocalDateDeserializer : JsonDeserializer<LocalDate> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): LocalDate {
            return LocalDate.parse(json.asString, DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    /**
     * Custom serializer for ReadingActivity to handle Set serialization.
     */
    private class ReadingActivitySerializer : JsonSerializer<ReadingActivity> {
        override fun serialize(
            src: ReadingActivity,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val jsonObject = JsonObject()
            jsonObject.add("date", context.serialize(src.date))
            jsonObject.addProperty("totalAyahsRead", src.totalAyahsRead)
            jsonObject.add("trackedAyahIds", context.serialize(src.trackedAyahIds.toList()))
            jsonObject.addProperty("badgeLevel", src.badgeLevel.name)
            return jsonObject
        }
    }

    /**
     * Custom deserializer for ReadingActivity to handle Set deserialization.
     */
    private class ReadingActivityDeserializer : JsonDeserializer<ReadingActivity> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): ReadingActivity {
            val jsonObject = json.asJsonObject
            val date = context.deserialize<LocalDate>(jsonObject.get("date"), LocalDate::class.java)
            val totalAyahsRead = jsonObject.get("totalAyahsRead").asInt
            val trackedAyahIdsArray = jsonObject.getAsJsonArray("trackedAyahIds")
            val trackedAyahIds = trackedAyahIdsArray.map { it.asString }.toSet()
            val badgeLevel = BadgeLevel.valueOf(jsonObject.get("badgeLevel").asString)

            return ReadingActivity(
                date = date,
                totalAyahsRead = totalAyahsRead,
                trackedAyahIds = trackedAyahIds,
                badgeLevel = badgeLevel
            )
        }
    }
}
