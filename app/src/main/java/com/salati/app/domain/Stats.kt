package com.salati.app.domain

import java.time.Instant
import java.time.LocalDate

data class StatusCounts(
    val onTime: Int = 0,
    val late: Int = 0,
    val missedConfirmed: Int = 0,
    val missedAuto: Int = 0,
    val unrecorded: Int = 0,
    val congregation: Int = 0,
) {
    operator fun plus(o: StatusCounts) = StatusCounts(
        onTime + o.onTime, late + o.late, missedConfirmed + o.missedConfirmed,
        missedAuto + o.missedAuto, unrecorded + o.unrecorded, congregation + o.congregation,
    )
}

data class PrayerStats(val total: StatusCounts, val perPrayer: Map<Prayer, StatusCounts>)

/**
 * Keeps "unrecorded" (time passed, nothing recorded) separate from "missed": confirmed by the user
 * vs. auto-labelled by the app. Unrecorded prayers before tracking began are ignored.
 */
object StatsCalculator {
    fun compute(
        records: List<DayRecord>,
        from: LocalDate,
        to: LocalDate,
        trackingStart: Instant,
        now: Instant,
        timesFor: (LocalDate) -> DayTimes,
    ): PrayerStats {
        val map = records.associateBy { it.date to it.prayer }
        val per = Prayer.entries.associateWith { StatusCounts() }.toMutableMap()
        var d = from
        while (!d.isAfter(to)) {
            var times: DayTimes? = null
            for (p in Prayer.entries) {
                val r = map[d to p]
                val c = when (r?.status) {
                    PrayerStatus.ON_TIME -> StatusCounts(onTime = 1, congregation = if (r.congregation) 1 else 0)
                    PrayerStatus.LATE -> StatusCounts(late = 1, congregation = if (r.congregation) 1 else 0)
                    PrayerStatus.MISSED -> if (r.autoMarked) StatusCounts(missedAuto = 1) else StatusCounts(missedConfirmed = 1)
                    else -> {
                        val t = (times ?: timesFor(d).also { times = it }).of(p).toInstant()
                        if (!t.isAfter(now) && (r != null || !t.isBefore(trackingStart))) StatusCounts(unrecorded = 1) else StatusCounts()
                    }
                }
                per[p] = per.getValue(p) + c
            }
            d = d.plusDays(1)
        }
        return PrayerStats(per.values.fold(StatusCounts()) { a, b -> a + b }, per)
    }
}
