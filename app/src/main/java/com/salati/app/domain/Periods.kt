package com.salati.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class PeriodType { DAY, WEEK, MONTH, QUARTER, HALF_YEAR, YEAR }

data class Period(val start: LocalDate, val endInclusive: LocalDate) {
    val days: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1
    operator fun contains(d: LocalDate): Boolean = !d.isBefore(start) && !d.isAfter(endInclusive)
}

object Periods {
    /** Weeks start on Saturday, like the paper tracker (SA..F). */
    val WEEK_START: DayOfWeek = DayOfWeek.SATURDAY
    const val BLOCK_DAYS = 49
    const val SHEET_DAYS = 147

    fun containing(type: PeriodType, date: LocalDate): Period = when (type) {
        PeriodType.DAY -> Period(date, date)
        PeriodType.WEEK -> {
            val s = date.with(TemporalAdjusters.previousOrSame(WEEK_START))
            Period(s, s.plusDays(6))
        }
        PeriodType.MONTH -> {
            val s = date.withDayOfMonth(1)
            Period(s, s.plusMonths(1).minusDays(1))
        }
        PeriodType.QUARTER -> {
            val s = LocalDate.of(date.year, (date.monthValue - 1) / 3 * 3 + 1, 1)
            Period(s, s.plusMonths(3).minusDays(1))
        }
        PeriodType.HALF_YEAR -> {
            val s = LocalDate.of(date.year, if (date.monthValue <= 6) 1 else 7, 1)
            Period(s, s.plusMonths(6).minusDays(1))
        }
        PeriodType.YEAR -> Period(LocalDate.of(date.year, 1, 1), LocalDate.of(date.year, 12, 31))
    }

    fun shift(type: PeriodType, period: Period, steps: Long): Period {
        val anchor = when (type) {
            PeriodType.DAY -> period.start.plusDays(steps)
            PeriodType.WEEK -> period.start.plusWeeks(steps)
            PeriodType.MONTH -> period.start.plusMonths(steps)
            PeriodType.QUARTER -> period.start.plusMonths(3 * steps)
            PeriodType.HALF_YEAR -> period.start.plusMonths(6 * steps)
            PeriodType.YEAR -> period.start.plusYears(steps)
        }
        return containing(type, anchor)
    }

    /** Rows: days for day/week/month views; months for quarter/half-year/year views. */
    fun subPeriods(type: PeriodType, period: Period): List<Period> = when (type) {
        PeriodType.DAY, PeriodType.WEEK, PeriodType.MONTH ->
            generateSequence(period.start) { it.plusDays(1) }
                .takeWhile { !it.isAfter(period.endInclusive) }.map { Period(it, it) }.toList()
        else -> generateSequence(period.start) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(period.endInclusive) }.map { containing(PeriodType.MONTH, it) }.toList()
    }

    fun sum(byDate: Map<LocalDate, PrayerCounts>, period: Period): PrayerCounts {
        val m = emptyCounts().toMutableMap()
        for ((d, c) in byDate) if (d in period) for (p in Prayer.entries) m[p] = m.getValue(p) + c.of(p)
        return m
    }

    /** Start of the 147-day "old fashion" sheet containing [date]; aligned to the Saturday on/before [origin]. */
    fun sheetStart(date: LocalDate, origin: LocalDate): LocalDate {
        val o = origin.with(TemporalAdjusters.previousOrSame(WEEK_START))
        val diff = ChronoUnit.DAYS.between(o, date)
        return o.plusDays(Math.floorDiv(diff, SHEET_DAYS.toLong()) * SHEET_DAYS)
    }

    fun sheetBlocks(sheetStart: LocalDate): List<List<LocalDate>> =
        (0 until 3).map { b -> (0 until BLOCK_DAYS).map { sheetStart.plusDays((b * BLOCK_DAYS + it).toLong()) } }
}
