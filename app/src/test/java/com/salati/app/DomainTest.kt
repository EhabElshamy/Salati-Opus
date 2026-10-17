package com.salati.app

import com.salati.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class PrayerTimeTest {
    private val cairo = ZoneId.of("Africa/Cairo")
    private val calc = PrayerTimeCalculator()
    private val params = CalcParams(30.0444, 31.2357, CalcMethod.EGYPTIAN, AsrMethod.SHAFII)

    private fun near(expectedHHmm: String, actual: ZonedDateTime, tol: Int = 3) {
        val (h, m) = expectedHHmm.split(":").map { it.toInt() }
        val diff = abs((actual.hour * 60 + actual.minute) - (h * 60 + m))
        assertTrue("expected ~$expectedHHmm got ${actual.toLocalTime()}", diff <= tol)
    }

    @Test fun cairoWinterEgyptianMethod() {
        val t = calc.compute(LocalDate.of(2024, 1, 1), cairo, params)
        near("05:18", t.fajr); near("06:49", t.sunrise); near("11:59", t.dhuhr)
        near("14:47", t.asr); near("17:08", t.maghrib); near("18:30", t.isha)
    }

    @Test fun summerUsesDaylightSavingFromZoneRules() {
        val t = calc.compute(LocalDate.of(2024, 7, 1), cairo, params)
        near("12:58", t.dhuhr)   // Egypt observed DST (UTC+3) in summer 2024
        assertEquals(3 * 3600, t.dhuhr.offset.totalSeconds)
    }

    @Test fun orderIsAlwaysCorrectForSharkia() {
        val p = CalcParams(DEFAULT_LAT, DEFAULT_LNG)
        var d = LocalDate.of(2026, 1, 1)
        repeat(365) {
            val t = calc.compute(d, cairo, p)
            assertTrue(t.fajr < t.sunrise && t.sunrise < t.dhuhr && t.dhuhr < t.asr && t.asr < t.maghrib && t.maghrib < t.isha)
            d = d.plusDays(1)
        }
    }

    @Test fun hanafiAsrIsLater() {
        val d = LocalDate.of(2026, 3, 1)
        val s = calc.compute(d, cairo, params)
        val h = calc.compute(d, cairo, params.copy(asr = AsrMethod.HANAFI))
        assertTrue(h.asr.isAfter(s.asr))
    }

    @Test fun minuteAdjustmentApplies() {
        val d = LocalDate.of(2026, 3, 1)
        val a = calc.compute(d, cairo, params)
        val b = calc.compute(d, cairo, params.copy(adjustmentsMinutes = mapOf(Prayer.DHUHR to 2)))
        assertEquals(a.dhuhr.plusMinutes(2), b.dhuhr)
    }
}

class AutoMissPolicyTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private fun times(d: LocalDate) = DayTimes(
        d, d.atTime(5, 0).atZone(zone), d.atTime(6, 30).atZone(zone), d.atTime(12, 0).atZone(zone),
        d.atTime(15, 0).atZone(zone), d.atTime(17, 30).atZone(zone), d.atTime(19, 0).atZone(zone),
    )

    @Test fun labelsOnlyAfterNextPrayerStartsAndAfterTrackingStart() {
        val d = LocalDate.of(2026, 10, 15)
        val start = d.atTime(4, 0).atZone(zone).toInstant()
        val now = d.atTime(15, 1).atZone(zone).toInstant()
        val c = AutoMissPolicy.candidates(now, start, d, d, { times(it) }) { _, _ -> false }
        assertEquals(listOf(d to Prayer.FAJR, d to Prayer.DHUHR), c)
        val late = AutoMissPolicy.candidates(d.atTime(13, 0).atZone(zone).toInstant(), d.atTime(12, 30).atZone(zone).toInstant(), d, d, { times(it) }) { _, _ -> false }
        assertTrue(late.isEmpty())
    }

    @Test fun ishaWaitsForNextFajrAndRecordedArePreserved() {
        val d = LocalDate.of(2026, 10, 15)
        val start = Instant.EPOCH
        val beforeFajr = d.plusDays(1).atTime(4, 59).atZone(zone).toInstant()
        assertTrue(AutoMissPolicy.candidates(beforeFajr, start, d, d, { times(it) }) { _, _ -> false }.none { it.second == Prayer.ISHA })
        val afterFajr = d.plusDays(1).atTime(5, 0).atZone(zone).toInstant()
        assertTrue(AutoMissPolicy.candidates(afterFajr, start, d, d, { times(it) }) { _, _ -> false }.contains(d to Prayer.ISHA))
        assertTrue(AutoMissPolicy.candidates(afterFajr, start, d, d, { times(it) }) { _, _ -> true }.isEmpty())
    }
}

class PeriodsTest {
    private val d = LocalDate.of(2026, 10, 15)   // Thursday

    @Test fun weeksStartSaturday() {
        val w = Periods.containing(PeriodType.WEEK, d)
        assertEquals(LocalDate.of(2026, 10, 10), w.start)
        assertEquals(LocalDate.of(2026, 10, 16), w.endInclusive)
    }

    @Test fun quarterHalfYear() {
        assertEquals(Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)), Periods.containing(PeriodType.QUARTER, d))
        assertEquals(Period(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31)), Periods.containing(PeriodType.HALF_YEAR, d))
        assertEquals(12, Periods.subPeriods(PeriodType.YEAR, Periods.containing(PeriodType.YEAR, d)).size)
        assertEquals(LocalDate.of(2027, 1, 1), Periods.shift(PeriodType.QUARTER, Periods.containing(PeriodType.QUARTER, d), 1).start)
    }

    @Test fun oldFashionSheetIs147DaysFromSaturday() {
        assertEquals(LocalDate.of(2026, 10, 10), Periods.sheetStart(d, d))
        assertEquals(LocalDate.of(2026, 10, 10), Periods.sheetStart(LocalDate.of(2027, 3, 5), d))
        assertEquals(LocalDate.of(2027, 3, 6), Periods.sheetStart(LocalDate.of(2027, 3, 6), d))
        val blocks = Periods.sheetBlocks(LocalDate.of(2026, 10, 10))
        assertEquals(3, blocks.size)
        blocks.forEach { assertEquals(49, it.size); assertEquals(java.time.DayOfWeek.SATURDAY, it.first().dayOfWeek) }
    }

    @Test fun sumsMadeUpPerPeriod() {
        val byDate = mapOf(d to mapOf(Prayer.FAJR to 2), d.minusDays(30) to mapOf(Prayer.ASR to 1))
        assertEquals(2, Periods.sum(byDate, Periods.containing(PeriodType.WEEK, d)).total())
        assertEquals(3, Periods.sum(byDate, Periods.containing(PeriodType.YEAR, d)).total())
    }
}

class ProgressEstimatorTest {
    private val today = LocalDate.of(2026, 10, 15)

    @Test fun targetBasedEstimate() {
        val b = emptyCounts() + (Prayer.FAJR to 10) + (Prayer.ISHA to 4)
        val e = ProgressEstimator.byTarget(b, Prayer.entries.associateWith { 1 }, today) as CompletionEstimate.Estimated
        assertEquals(10, e.days)
        assertEquals(today.plusDays(10), e.date)
    }

    @Test fun zeroTargetForOutstandingPrayerIsInsufficient() {
        val b = emptyCounts() + (Prayer.FAJR to 10)
        val e = ProgressEstimator.byTarget(b, emptyCounts(), today)
        assertEquals(CompletionEstimate.Insufficient(InsufficientReason.NO_TARGET_FOR_OUTSTANDING_PRAYER), e)
    }

    @Test fun paceNeedsEnoughActivity() {
        val b = Prayer.entries.associateWith { 100 }
        val few = mapOf(today to Prayer.entries.associateWith { 1 }, today.minusDays(1) to Prayer.entries.associateWith { 1 })
        assertTrue(ProgressEstimator.byPace(b, few, today) is CompletionEstimate.Insufficient)
        assertTrue(ProgressEstimator.byPace(b, emptyMap(), today) is CompletionEstimate.Insufficient)
    }

    @Test fun paceEstimateFromRecordedAverage() {
        val b = Prayer.entries.associateWith { 100 }
        val hist = (0 until 10).associate { today.minusDays(it.toLong()) to Prayer.entries.associateWith { 1 } }
        val e = ProgressEstimator.byPace(b, hist, today) as CompletionEstimate.Estimated
        assertEquals(10, e.windowDays)
        assertEquals(100, e.days)
    }

    @Test fun completeWhenNothingOutstanding() {
        assertEquals(CompletionEstimate.AlreadyComplete, ProgressEstimator.byPace(emptyCounts(), emptyMap(), today))
    }
}

class BackupValidationTest {
    private fun entry(id: Long, type: EntryType, delta: Int, prayer: String = "FAJR", undoneBy: Long? = null) =
        EntryDto(id, prayer, type.name, delta, "2026-10-01", 1L, undoneByEntryId = undoneBy)

    private fun valid() = BackupFile(
        exportedAt = "2026-10-15T10:00:00Z",
        settings = AppSettings(setupComplete = true),
        records = listOf(RecordDto("2026-10-01", "FAJR", "MISSED", debtEntryId = 2), RecordDto("2026-10-01", "DHUHR", "ON_TIME", congregation = true)),
        entries = listOf(entry(1, EntryType.INITIAL_ESTIMATE, 365), entry(2, EntryType.MISSED_CONFIRMED, 1), entry(3, EntryType.MADE_UP, -1)),
    )

    @Test fun roundTripIsValid() {
        val text = BackupCodec.encode(valid())
        val r = BackupCodec.decodeAndValidate(text)
        assertTrue(r is BackupParseResult.Valid)
        r as BackupParseResult.Valid
        assertEquals(365, r.summary.balances.of(Prayer.FAJR))
        assertEquals(2, r.summary.records)
    }

    @Test fun rejectsGarbage() {
        val r = BackupCodec.decodeAndValidate("{not json")
        assertEquals(BackupErrorCode.INVALID_JSON, (r as BackupParseResult.Invalid).issues.first().code)
    }

    private fun codes(f: BackupFile) = BackupCodec.validate(f).map { it.code }.toSet()

    @Test fun rejectsWrongAppAndVersion() {
        assertTrue(BackupErrorCode.WRONG_APP in codes(valid().copy(app = "other")))
        assertTrue(BackupErrorCode.UNSUPPORTED_VERSION in codes(valid().copy(formatVersion = 99)))
    }

    @Test fun rejectsDuplicatesAndDoubleCounting() {
        val f = valid()
        assertTrue(BackupErrorCode.DUPLICATE_RECORD in codes(f.copy(records = f.records + f.records.first().copy(prayer = "FAJR"))))
        val second = RecordDto("2026-10-02", "FAJR", "MISSED", debtEntryId = 2)
        assertTrue(BackupErrorCode.DOUBLE_COUNTED in codes(f.copy(records = f.records + second)))
        assertTrue(BackupErrorCode.DUPLICATE_ENTRY_ID in codes(f.copy(entries = f.entries + entry(3, EntryType.MADE_UP, -1))))
    }

    @Test fun rejectsNegativeBalanceAndBrokenReferences() {
        val f = valid()
        assertTrue(BackupErrorCode.NEGATIVE_BALANCE in codes(f.copy(entries = f.entries + entry(9, EntryType.MADE_UP, -1000))))
        assertTrue(BackupErrorCode.BROKEN_REFERENCE in codes(f.copy(records = listOf(RecordDto("2026-10-01", "FAJR", "MISSED", debtEntryId = 77)))))
        assertTrue(BackupErrorCode.BROKEN_REFERENCE in codes(f.copy(entries = f.entries + entry(10, EntryType.MADE_UP, -1, undoneBy = 555))))
        assertTrue(BackupErrorCode.BROKEN_REFERENCE in codes(f.copy(records = listOf(RecordDto("2026-10-01", "FAJR", "ON_TIME", debtEntryId = 2)))))
    }

    @Test fun rejectsInvalidValues() {
        val f = valid()
        assertTrue(BackupErrorCode.INVALID_RECORD in codes(f.copy(records = listOf(RecordDto("2026-13-01", "FAJR", "ON_TIME")))))
        assertTrue(BackupErrorCode.INVALID_RECORD in codes(f.copy(records = listOf(RecordDto("2026-10-01", "WITR", "ON_TIME")))))
        assertTrue(BackupErrorCode.INVALID_ENTRY in codes(f.copy(entries = f.entries + entry(11, EntryType.MADE_UP, -1, prayer = "WITR"))))
        assertTrue(BackupErrorCode.INVALID_SETTINGS in codes(f.copy(settings = AppSettings(latitude = 120.0))))
        assertTrue(BackupErrorCode.INVALID_SETTINGS in codes(f.copy(settings = AppSettings(qadaaReminderTimes = listOf("25:99")))))
    }
}

class StatsTest {
    @Test fun unrecordedIsSeparateFromMissed() {
        val zone = ZoneId.of("Africa/Cairo")
        val d = LocalDate.of(2026, 10, 15)
        val times = { x: LocalDate -> PrayerTimeCalculator().compute(x, zone, CalcParams(DEFAULT_LAT, DEFAULT_LNG)) }
        val recs = listOf(
            DayRecord(d, Prayer.FAJR, PrayerStatus.ON_TIME, congregation = true),
            DayRecord(d, Prayer.DHUHR, PrayerStatus.MISSED, debtEntryId = 1),
            DayRecord(d, Prayer.ASR, PrayerStatus.MISSED, autoMarked = true),
        )
        val st = StatsCalculator.compute(recs, d, d, Instant.EPOCH, d.plusDays(1).atStartOfDay(zone).toInstant(), times)
        assertEquals(1, st.total.onTime)
        assertEquals(1, st.total.congregation)
        assertEquals(1, st.total.missedConfirmed)
        assertEquals(1, st.total.missedAuto)
        assertEquals(2, st.total.unrecorded)
    }
}
