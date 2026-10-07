package com.revix.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object GarageReceiptImagePathCodec {
    private const val MULTI_PATH_PREFIX = "multi_receipts:"

    fun decode(rawValue: String?): List<String> {
        val normalizedRaw = rawValue?.trim().orEmpty()
        if (normalizedRaw.isEmpty()) {
            return emptyList()
        }

        if (!normalizedRaw.startsWith(MULTI_PATH_PREFIX)) {
            return listOf(normalizedRaw)
        }

        val jsonPayload = normalizedRaw.removePrefix(MULTI_PATH_PREFIX)
        if (jsonPayload.isBlank()) {
            return emptyList()
        }

        val type = object : TypeToken<List<String?>>() {}.type
        val parsedList = runCatching {
            Gson().fromJson<List<String?>>(jsonPayload, type)
        }.getOrNull().orEmpty().filterNotNull()

        return normalize(parsedList)
    }

    fun encode(paths: List<String>): String? {
        val normalizedPaths = normalize(paths)
        return when (normalizedPaths.size) {
            0 -> null
            1 -> normalizedPaths.first()
            else -> MULTI_PATH_PREFIX + Gson().toJson(normalizedPaths)
        }
    }

    fun normalize(paths: List<String>): List<String> {
        val orderedUnique = LinkedHashSet<String>()
        paths.forEach { path ->
            val normalizedPath = path.trim()
            if (normalizedPath.isNotEmpty()) {
                orderedUnique.add(normalizedPath)
            }
        }
        return orderedUnique.toList()
    }
}
