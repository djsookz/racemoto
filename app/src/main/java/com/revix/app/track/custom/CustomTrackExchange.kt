package com.revix.app.tracking

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

/**
 * Stable on-disk / share format for custom tracks.
 * Kept in tracking package + @SerializedName so release R8 cannot break cross-device import.
 */
@Keep
data class CustomTrackExchangePoint(
    @SerializedName("lat") val lat: Double = 0.0,
    @SerializedName("lon") val lon: Double = 0.0
)

@Keep
data class CustomTrackExchange(
    @SerializedName("version") val version: Int = 2,
    @SerializedName("name") val name: String = "",
    @SerializedName("mode") val mode: String = "",
    @SerializedName("checkpointPoints") val checkpointPoints: List<CustomTrackExchangePoint> = emptyList(),
    @SerializedName("circuitGatePoints") val circuitGatePoints: List<CustomTrackExchangePoint> = emptyList(),
    @SerializedName("startGatePoints") val startGatePoints: List<CustomTrackExchangePoint> = emptyList(),
    @SerializedName("finishGatePoints") val finishGatePoints: List<CustomTrackExchangePoint> = emptyList(),
    @SerializedName("referencePath") val referencePath: List<CustomTrackExchangePoint>? = null,
    @SerializedName("measuredDistanceMeters") val measuredDistanceMeters: Float? = null
) {
    fun looksUsable(): Boolean {
        return mode.isNotBlank() ||
            checkpointPoints.isNotEmpty() ||
            circuitGatePoints.isNotEmpty() ||
            startGatePoints.isNotEmpty() ||
            finishGatePoints.isNotEmpty() ||
            !referencePath.isNullOrEmpty()
    }

    fun sanitized(): CustomTrackExchange {
        return copy(
            name = (name as String?) ?: "",
            mode = (mode as String?) ?: "",
            checkpointPoints = (checkpointPoints as List<CustomTrackExchangePoint?>?)?.filterNotNull().orEmpty(),
            circuitGatePoints = (circuitGatePoints as List<CustomTrackExchangePoint?>?)?.filterNotNull().orEmpty(),
            startGatePoints = (startGatePoints as List<CustomTrackExchangePoint?>?)?.filterNotNull().orEmpty(),
            finishGatePoints = (finishGatePoints as List<CustomTrackExchangePoint?>?)?.filterNotNull().orEmpty(),
            referencePath = (referencePath as List<CustomTrackExchangePoint?>?)?.filterNotNull()
        )
    }

    companion object {
        fun parse(content: String, gson: Gson = Gson()): CustomTrackExchange {
            val direct = runCatching {
                gson.fromJson(content, CustomTrackExchange::class.java)?.sanitized()
            }.getOrNull()
            if (direct != null && direct.looksUsable()) return direct

            val recovered = parseLegacyOrObfuscated(content)?.sanitized()
            if (recovered != null && recovered.looksUsable()) return recovered

            return direct ?: throw IllegalArgumentException("Unrecognized track JSON")
        }

        /**
         * Recovers files written by older release builds where R8 renamed private nested
         * exchange fields (often a/b/c… in declaration order), and also accepts lat/lon points.
         */
        private fun parseLegacyOrObfuscated(content: String): CustomTrackExchange? {
            val root = runCatching {
                JsonParser.parseString(content).asJsonObject
            }.getOrNull() ?: return null

            if (root.has("mode") || root.has("checkpointPoints") || root.has("name")) {
                // Readable keys present but somehow incomplete — nothing more to recover.
                return null
            }

            // Old private nested class field order:
            // version, name, mode, checkpointPoints, circuitGatePoints,
            // startGatePoints, finishGatePoints, referencePath, measuredDistanceMeters
            val version = root.getAsInt(listOf("version", "a")) ?: 2
            val name = root.getAsString(listOf("name", "b")).orEmpty()
            val mode = root.getAsString(listOf("mode", "c"))
                ?: root.entrySet()
                    .mapNotNull { (_, value) ->
                        value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    }
                    .firstOrNull { it.equals("CIRCUIT", true) || it.equals("POINT_TO_POINT", true) }
                    .orEmpty()

            val checkpointPoints = root.getAsPointList(listOf("checkpointPoints", "d"))
            val circuitGatePoints = root.getAsPointList(listOf("circuitGatePoints", "e"))
            val startGatePoints = root.getAsPointList(listOf("startGatePoints", "f"))
            val finishGatePoints = root.getAsPointList(listOf("finishGatePoints", "g"))
            val referencePath = root.getAsPointList(listOf("referencePath", "h")).ifEmpty { null }
            val measuredDistanceMeters = root.getAsFloat(listOf("measuredDistanceMeters", "i"))

            return CustomTrackExchange(
                version = version,
                name = name,
                mode = mode,
                checkpointPoints = checkpointPoints,
                circuitGatePoints = circuitGatePoints,
                startGatePoints = startGatePoints,
                finishGatePoints = finishGatePoints,
                referencePath = referencePath,
                measuredDistanceMeters = measuredDistanceMeters
            )
        }

        private fun JsonObject.getAsInt(keys: List<String>): Int? {
            for (key in keys) {
                val el = get(key) ?: continue
                if (el.isJsonPrimitive && el.asJsonPrimitive.isNumber) return el.asInt
            }
            return null
        }

        private fun JsonObject.getAsFloat(keys: List<String>): Float? {
            for (key in keys) {
                val el = get(key) ?: continue
                if (el.isJsonPrimitive && el.asJsonPrimitive.isNumber) return el.asFloat
            }
            return null
        }

        private fun JsonObject.getAsString(keys: List<String>): String? {
            for (key in keys) {
                val el = get(key) ?: continue
                if (el.isJsonPrimitive && el.asJsonPrimitive.isString) return el.asString
            }
            return null
        }

        private fun JsonObject.getAsPointList(keys: List<String>): List<CustomTrackExchangePoint> {
            for (key in keys) {
                val el = get(key) ?: continue
                if (!el.isJsonArray) continue
                return el.asJsonArray.toPointList()
            }
            return emptyList()
        }

        private fun JsonArray.toPointList(): List<CustomTrackExchangePoint> {
            return mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                element.asJsonObject.toPoint()
            }
        }

        private fun JsonObject.toPoint(): CustomTrackExchangePoint? {
            val lat = when {
                has("lat") && get("lat").isJsonPrimitive -> get("lat").asDouble
                has("a") && get("a").isJsonPrimitive && get("a").asJsonPrimitive.isNumber -> get("a").asDouble
                else -> null
            }
            val lon = when {
                has("lon") && get("lon").isJsonPrimitive -> get("lon").asDouble
                has("b") && get("b").isJsonPrimitive && get("b").asJsonPrimitive.isNumber -> get("b").asDouble
                else -> null
            }
            if (lat != null && lon != null) {
                return CustomTrackExchangePoint(lat = lat, lon = lon)
            }

            // Last resort: first two numeric values in key iteration order.
            val numbers = entrySet().mapNotNull { (_, value) ->
                value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
            }
            if (numbers.size < 2) return null
            return CustomTrackExchangePoint(lat = numbers[0], lon = numbers[1])
        }
    }
}
