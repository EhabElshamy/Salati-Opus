package com.salati.app.domain

import java.time.Instant
import java.time.LocalDate

/**
 * An unrecorded prayer is auto-labelled "missed" once the NEXT obligatory prayer time has
 * started (Isha -> next day's Fajr). Only prayers whose time began after tracking started are
 * considered. This is an app bookkeeping rule, not a religious ruling about prayer deadlines.
 */
object AutoMissPolicy {
    fun deadline(p: Prayer, day: DayTimes, nextDay: DayTimes): Instant = when (p) {
        Prayer.FAJR -> day.dhuhr.toInstant()
        Prayer.DHUHR -> day.asr.toInstant()
        Prayer.ASR -> day.maghrib.toInstant()
        Prayer.MAGHRIB -> day.isha.toInstant()
        Prayer.ISHA -> nextDay.fajr.toInstant()
    }

    fun candidates(
        now: Instant,
        trackingStart: Instant,
        from: LocalDate,
        to: LocalDate,
        timesFor: (LocalDate) -> DayTimes,
        hasRecord: (LocalDate, Prayer) -> Boolean,
    ): List<Pair<LocalDate, Prayer>> {
        val out = mutableListOf<Pair<LocalDate, Prayer>>()
        var d = from
        while (!d.isAfter(to)) {
            val day = timesFor(d)
            val next = timesFor(d.plusDays(1))
            for (p in Prayer.entries) {
                if (day.of(p).toInstant().isBefore(trackingStart)) continue
                if (now.isBefore(deadline(p, day, next))) continue
                if (!hasRecord(d, p)) out += d to p
            }
            d = d.plusDays(1)
        }
        return out
    }
}
