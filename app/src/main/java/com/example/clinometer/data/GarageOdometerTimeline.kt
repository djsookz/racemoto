package com.example.clinometer.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale

enum class GarageOdometerSource {
    FUEL,
    MAINTENANCE,
    DOCUMENT
}

data class GarageOdometerConflict(
    val type: Type,
    val referenceOdometerKm: Long
) {
    enum class Type {
        PREVIOUS,
        NEXT
    }
}

object GarageOdometerTimeline {
    private const val TIMELINE_DATE_PATTERN = "dd MMM yyyy, HH:mm"

    fun resolveReferenceTimestamp(dateText: String?, fallbackTimestamp: Long): Long {
        return resolveTimelineTimestamp(dateText, fallbackTimestamp)
    }

    fun resolveConflict(
        context: Context,
        profileId: Long,
        source: GarageOdometerSource,
        entryId: Long,
        odometerKm: Long?,
        dateText: String?,
        fallbackTimestamp: Long
    ): GarageOdometerConflict? {
        if (profileId == -1L || odometerKm == null || odometerKm <= 0L) {
            return null
        }

        val currentOrder = EntryOrder(
            occurredAt = resolveTimelineTimestamp(dateText, fallbackTimestamp),
            sourcePriority = source.ordinal,
            entryId = entryId
        )

        val surroundingEntries = loadTimelineEntries(context, profileId)
            .filterNot { it.source == source && it.entryId == entryId }

        val previousEntry = surroundingEntries
            .filter { it.order < currentOrder }
            .maxByOrNull { it.order }

        if (previousEntry != null && odometerKm <= previousEntry.odometerKm) {
            return GarageOdometerConflict(
                type = GarageOdometerConflict.Type.PREVIOUS,
                referenceOdometerKm = previousEntry.odometerKm
            )
        }

        val nextEntry = surroundingEntries
            .filter { it.order > currentOrder }
            .minByOrNull { it.order }

        if (nextEntry != null && odometerKm >= nextEntry.odometerKm) {
            return GarageOdometerConflict(
                type = GarageOdometerConflict.Type.NEXT,
                referenceOdometerKm = nextEntry.odometerKm
            )
        }

        return null
    }

    fun resolveLatestAddedConflict(
        context: Context,
        profileId: Long,
        source: GarageOdometerSource,
        entryId: Long,
        odometerKm: Long?
    ): GarageOdometerConflict? {
        if (profileId == -1L || odometerKm == null || odometerKm <= 0L) {
            return null
        }

        val latestAddedEntry = loadTimelineEntries(context, profileId)
            .filterNot { it.source == source && it.entryId == entryId }
            .maxByOrNull { it.addedOrder }
            ?: return null

        if (odometerKm <= latestAddedEntry.odometerKm) {
            return GarageOdometerConflict(
                type = GarageOdometerConflict.Type.PREVIOUS,
                referenceOdometerKm = latestAddedEntry.odometerKm
            )
        }

        return null
    }

    fun hasReachedTargetAfter(
        context: Context,
        profileId: Long,
        source: GarageOdometerSource,
        entryId: Long,
        targetOdometerKm: Long,
        dateText: String?,
        fallbackTimestamp: Long
    ): Boolean {
        if (profileId == -1L || targetOdometerKm <= 0L) {
            return false
        }

        val currentOrder = EntryOrder(
            occurredAt = resolveTimelineTimestamp(dateText, fallbackTimestamp),
            sourcePriority = source.ordinal,
            entryId = entryId
        )

        return loadTimelineEntries(context, profileId)
            .asSequence()
            .filterNot { it.source == source && it.entryId == entryId }
            .filter { it.order > currentOrder }
            .any { it.odometerKm >= targetOdometerKm }
    }

    fun firstReachedTargetTimestampAfter(
        context: Context,
        profileId: Long,
        source: GarageOdometerSource,
        entryId: Long,
        targetOdometerKm: Long,
        dateText: String?,
        fallbackTimestamp: Long
    ): Long? {
        if (profileId == -1L || targetOdometerKm <= 0L) {
            return null
        }

        val currentOrder = EntryOrder(
            occurredAt = resolveTimelineTimestamp(dateText, fallbackTimestamp),
            sourcePriority = source.ordinal,
            entryId = entryId
        )

        return loadTimelineEntries(context, profileId)
            .asSequence()
            .filterNot { it.source == source && it.entryId == entryId }
            .filter { it.order > currentOrder }
            .firstOrNull { it.odometerKm >= targetOdometerKm }
            ?.order
            ?.occurredAt
    }

    fun latestRecordedOdometerFrom(
        context: Context,
        profileId: Long,
        source: GarageOdometerSource,
        entryId: Long,
        dateText: String?,
        fallbackTimestamp: Long
    ): Long? {
        if (profileId == -1L) {
            return null
        }

        val currentOrder = EntryOrder(
            occurredAt = resolveTimelineTimestamp(dateText, fallbackTimestamp),
            sourcePriority = source.ordinal,
            entryId = entryId
        )

        return loadTimelineEntries(context, profileId)
            .asSequence()
            .filter { it.order >= currentOrder }
            .maxByOrNull { it.order }
            ?.odometerKm
    }

    private fun loadTimelineEntries(context: Context, profileId: Long): List<TimelineEntry> {
        val fuelEntries = GarageFuelEntryStorage.loadEntries(context, profileId)
            .asSequence()
            .filter { it.odometerKm > 0L }
            .map {
                TimelineEntry(
                    source = GarageOdometerSource.FUEL,
                    entryId = it.id,
                    odometerKm = it.odometerKm,
                    addedOrder = resolveAddedOrder(it.createdAt, it.id),
                    order = EntryOrder(
                        occurredAt = resolveTimelineTimestamp(it.date, it.createdAt),
                        sourcePriority = GarageOdometerSource.FUEL.ordinal,
                        entryId = it.id
                    )
                )
            }

        val maintenanceEntries = GarageMaintenanceEntryStorage.loadEntries(context, profileId)
            .asSequence()
            .filter { it.odometerKm > 0L }
            .map {
                TimelineEntry(
                    source = GarageOdometerSource.MAINTENANCE,
                    entryId = it.id,
                    odometerKm = it.odometerKm,
                    addedOrder = resolveAddedOrder(it.createdAt, it.id),
                    order = EntryOrder(
                        occurredAt = resolveTimelineTimestamp(it.date, it.createdAt),
                        sourcePriority = GarageOdometerSource.MAINTENANCE.ordinal,
                        entryId = it.id
                    )
                )
            }

        val documentEntries = GarageDocumentEntryStorage.loadEntries(context, profileId)
            .asSequence()
            .filter { it.odometerKm > 0L }
            .map {
                TimelineEntry(
                    source = GarageOdometerSource.DOCUMENT,
                    entryId = it.id,
                    odometerKm = it.odometerKm,
                    addedOrder = resolveAddedOrder(it.createdAt, it.id),
                    order = EntryOrder(
                        occurredAt = resolveTimelineTimestamp(it.date, it.createdAt),
                        sourcePriority = GarageOdometerSource.DOCUMENT.ordinal,
                        entryId = it.id
                    )
                )
            }

        return (fuelEntries + maintenanceEntries + documentEntries).toList()
    }

    private fun resolveTimelineTimestamp(dateText: String?, fallbackTimestamp: Long): Long {
        val normalizedDateText = dateText?.trim().orEmpty()
        if (normalizedDateText.isEmpty()) {
            return fallbackTimestamp
        }

        val candidateLocales = buildList {
            add(Locale.getDefault())
            add(Locale.ENGLISH)
            add(Locale.US)
            add(Locale.UK)
            add(Locale("bg"))
            add(Locale("bg", "BG"))
        }.distinctBy { it.toLanguageTag() }

        candidateLocales.forEach { locale ->
            val parsedTime = runCatching {
                SimpleDateFormat(TIMELINE_DATE_PATTERN, locale).apply {
                    isLenient = false
                }.parse(normalizedDateText)?.time
            }.getOrNull()

            if (parsedTime != null && parsedTime > 0L) {
                return parsedTime
            }
        }

        return fallbackTimestamp
    }

    private fun resolveAddedOrder(createdAt: Long, entryId: Long): Long {
        return maxOf(createdAt.takeIf { it > 0L } ?: 0L, entryId)
    }

    private data class TimelineEntry(
        val source: GarageOdometerSource,
        val entryId: Long,
        val odometerKm: Long,
        val addedOrder: Long,
        val order: EntryOrder
    )

    private data class EntryOrder(
        val occurredAt: Long,
        val sourcePriority: Int,
        val entryId: Long
    ) : Comparable<EntryOrder> {
        override fun compareTo(other: EntryOrder): Int {
            return compareValuesBy(this, other, EntryOrder::occurredAt, EntryOrder::sourcePriority, EntryOrder::entryId)
        }
    }
}