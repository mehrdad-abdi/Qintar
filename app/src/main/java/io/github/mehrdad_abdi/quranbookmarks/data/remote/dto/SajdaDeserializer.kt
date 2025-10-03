package io.github.mehrdad_abdi.quranbookmarks.data.remote.dto

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * Custom deserializer for the polymorphic sajda field.
 * The API returns:
 * - `false` (boolean) for ayahs without sajda
 * - An object with {id, recommended, obligatory} for ayahs with sajda
 */
class SajdaDeserializer : JsonDeserializer<SajdaData?> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SajdaData? {
        if (json == null || json.isJsonNull) {
            return null
        }

        // If it's a boolean false, return null (no sajda)
        if (json.isJsonPrimitive && json.asJsonPrimitive.isBoolean) {
            return if (json.asBoolean) {
                // Shouldn't happen based on API, but handle it safely
                null
            } else {
                null
            }
        }

        // If it's an object, parse it as SajdaData
        if (json.isJsonObject) {
            val jsonObject = json.asJsonObject
            return SajdaData(
                id = jsonObject.get("id")?.asInt ?: 0,
                recommended = jsonObject.get("recommended")?.asBoolean ?: false,
                obligatory = jsonObject.get("obligatory")?.asBoolean ?: false
            )
        }

        return null
    }
}
