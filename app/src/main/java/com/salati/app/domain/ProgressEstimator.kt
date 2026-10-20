package com.salati.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class InsufficientReason { NO_TARGET_FOR_OUTSTANDING_PRAYER, TOO_FEW_ACTIVE_DAYS, NO_ACTIVITY_FOR_A_PRAYER }

sealed interface CompletionEstimate {
    data object AlreadyComplete : CompletionEstimate
    data class Estimated(
        val days: Long,
        val date: LocalDate,
        val dailyRates: Map<Prayer, Double>,
        val windowDays: Int,
        val activeDays: Int,
    ) : CompletionEstimate
    data class Insufficient(val reason: InsufficientReason, val activeDays: Int = 0) : CompletionEstimate
}

object ProgressEstimator {
    const val PACE_WINDOW_DAYS = 30
    const val MIN_ACTIVE_DAYS = 5

    /** Assumes the daily target is met every day starting tomorrow. */
    fun byTarget(balances: PrayerCounts, perDay: PrayerCounts, today: LocalDate): CompletionEstimate {
        if (balances.values.all { it <= 0 }) return CompletionEstimate.AlreadyComplete
        var days = 0L
        for (p in Prayer.entries) {
            val b = balances.of(p)
            if (b <= 0) continue
            val t = perDay.of(p)
            if (t <= 0) return CompletionEstimate.Insufficient(InsufficientReason.NO_TARGET_FOR_OUTSTANDING_PRAYER)
            days = maxOf(days, ceil(b.toDouble() / t).toLong())
        }
        return CompletionEstimate.Estimated(days, today.plusDays(days), perDay.mapValues { it.value.toDouble() }, 0, 0)
    }

    /**
     * Average of the last 30 days, or since [since] (the qadaa start date) / the first make-up if
     * that is shorter; needs >= 5 active days.
     */
    fun byPace(
        balances: PrayerCounts,
        madeUpByDate: Map<LocalDate, PrayerCounts>,
        today: LocalDate,
        since: LocalDate? = null,
    ): CompletionEstimate {
        if (balances.values.all { it <= 0 }) return CompletionEstimate.AlreadyComplete
        val firstActivity = madeUpByDate.filter { it.value.total() > 0 }.keys.minOrNull()
            ?: return CompletionEstimate.Insufficient(InsufficientReason.TOO_FEW_ACTIVE_DAYS, 0)
        val first = if (since != null && !since.isAfter(today)) since else firstActivity
        val window = minOf(PACE_WINDOW_DAYS.toLong(), ChronoUnit.DAYS.between(first, today) + 1).coerceAtLeast(1L)
        val start = today.minusDays(window - 1)
        val inWindow = madeUpByDate.filter { !it.key.isBefore(start) && !it.key.isAfter(today) && it.value.total() > 0 }
        val active = inWindow.size
        if (active < MIN_ACTIVE_DAYS) return CompletionEstimate.Insufficient(InsufficientReason.TOO_FEW_ACTIVE_DAYS, active)
        val rates = Prayer.entries.associateWith { p -> inWindow.values.sumOf { it.of(p) }.toDouble() / window }
        var days = 0L
        for (p in Prayer.entries) {
            val b = balances.of(p)
            if (b <= 0) continue
            val r = rates.getValue(p)
            if (r <= 0.0) return CompletionEstimate.Insufficient(InsufficientReason.NO_ACTIVITY_FOR_A_PRAYER, active)
            days = maxOf(days, ceil(b / r).toLong())
        }
        return CompletionEstimate.Estimated(days, today.plusDays(days), rates, window.toInt(), active)
    }
}
