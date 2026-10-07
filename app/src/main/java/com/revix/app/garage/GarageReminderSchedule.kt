package com.revix.app.garage

import java.util.Calendar
import java.util.concurrent.TimeUnit

object GarageReminderSchedule {
    const val KM_APPROACHING = 500L
    const val KM_SOON = 100L

    enum class DateStage(val key: String, val daysBefore: Int) {
        TWO_WEEKS("d14", 14),
        ONE_WEEK("d7", 7),
        THREE_DAYS("d3", 3),
        TWO_DAYS("d2", 2),
        ONE_DAY("d1", 1),
        DUE_DAY("d0", 0)
    }

    enum class KmStage(val key: String, val remainingKm: Long) {
        APPROACHING("k500", KM_APPROACHING),
        SOON("k100", KM_SOON),
        DUE("k0", 0L)
    }

    enum class HomePhase {
        UPCOMING,
        APPROACHING,
        DUE_TODAY,
        OVERDUE
    }

    fun parseFired(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun encodeFired(keys: Set<String>): String? {
        if (keys.isEmpty()) return null
        return keys.sorted().joinToString(",")
    }

    fun startOfDay(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun daysUntil(targetMillis: Long, nowMillis: Long): Int {
        val targetDay = startOfDay(targetMillis)
        val nowDay = startOfDay(nowMillis)
        return TimeUnit.MILLISECONDS.toDays(targetDay - nowDay).toInt()
    }

    fun dateStageToday(daysUntil: Int): DateStage? {
        return DateStage.values().firstOrNull { it.daysBefore == daysUntil }
    }

    fun missedDateStages(daysUntil: Int): List<DateStage> {
        if (daysUntil < 0) {
            return DateStage.values().toList()
        }
        return DateStage.values().filter { it.daysBefore > daysUntil }
    }

    fun nextDateAlarmMillis(targetMillis: Long, nowMillis: Long, fired: Set<String>): Long? {
        val daysUntil = daysUntil(targetMillis, nowMillis)
        val next = DateStage.values().firstOrNull { stage ->
            stage.key !in fired && daysUntil > stage.daysBefore
        } ?: return null
        return startOfDay(targetMillis) - TimeUnit.DAYS.toMillis(next.daysBefore.toLong())
    }

    fun kmStageForRemaining(remainingKm: Long): KmStage? {
        return when {
            remainingKm > KM_APPROACHING -> null
            remainingKm > KM_SOON -> KmStage.APPROACHING
            remainingKm > 0L -> KmStage.SOON
            else -> KmStage.DUE
        }
    }

    fun skippedKmStagesBefore(stage: KmStage): List<KmStage> {
        return KmStage.values().filter { it.remainingKm > stage.remainingKm }
    }

    fun homePhase(
        daysUntil: Int?,
        remainingKm: Long?
    ): HomePhase {
        val dateOverdue = daysUntil != null && daysUntil < 0
        val kmOverdue = remainingKm != null && remainingKm < 0L
        if (dateOverdue || kmOverdue) return HomePhase.OVERDUE

        val dateToday = daysUntil == 0
        val kmDue = remainingKm != null && remainingKm <= 0L
        if (dateToday || kmDue) return HomePhase.DUE_TODAY

        val dateApproaching = daysUntil != null && daysUntil in 1..14
        val kmApproaching = remainingKm != null && remainingKm in 1L..KM_APPROACHING
        if (dateApproaching || kmApproaching) return HomePhase.APPROACHING

        return HomePhase.UPCOMING
    }

    fun sortScore(phase: HomePhase, daysUntil: Int?, remainingKm: Long?): Int {
        val phaseScore = when (phase) {
            HomePhase.OVERDUE -> 0
            HomePhase.DUE_TODAY -> 1
            HomePhase.APPROACHING -> 2
            HomePhase.UPCOMING -> 3
        }
        val dayScore = daysUntil ?: 999
        val kmScore = remainingKm?.coerceAtLeast(0L)?.toInt() ?: 999_999
        return phaseScore * 1_000_000 + dayScore * 1_000 + (kmScore / 10)
    }
}
