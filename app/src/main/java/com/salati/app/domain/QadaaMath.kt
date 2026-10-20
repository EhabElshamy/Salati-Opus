package com.salati.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object QadaaMath {
    /** Explicit approximation: one year = 365 days. */
    const val DAYS_PER_YEAR = 365
    const val MAX_YEARS = 100.0

    /** Accepts "2.5", "2,5", Arabic-Indic "٢٫٥" and Persian digits. */
    fun parseYears(input: String): Double? {
        val normalized = buildString {
            for (c in input.trim()) {
                when (c) {
                    in '\u0660'..'\u0669' -> append('0' + (c - '\u0660'))
                    in '\u06F0'..'\u06F9' -> append('0' + (c - '\u06F0'))
                    '\u066B', ',' -> append('.')
                    else -> append(c)
                }
            }
        }
        if (normalized.isEmpty()) return null
        if (!normalized.all { it in '0'..'9' || it == '.' }) return null
        val v = normalized.toDoubleOrNull() ?: return null
        if (!v.isFinite() || v < 0.0 || v > MAX_YEARS) return null
        return v
    }

    /** days = round_half_up(years x 365). */
    fun estimateDays(years: Double): Int {
        require(years.isFinite() && years >= 0.0 && years <= MAX_YEARS) { "years out of range" }
        return BigDecimal.valueOf(years).multiply(BigDecimal(DAYS_PER_YEAR))
            .setScale(0, RoundingMode.HALF_UP).toInt()
    }

    fun estimateBalances(years: Double): PrayerCounts {
        val d = estimateDays(years)
        return Prayer.entries.associateWith { d }
    }

    fun balances(entries: List<LedgerEntry>): PrayerCounts {
        val m = emptyCounts().toMutableMap()
        for (e in entries) m[e.prayer] = m.getValue(e.prayer) + e.delta
        return m
    }

    fun initialTotals(entries: List<LedgerEntry>): PrayerCounts {
        val m = emptyCounts().toMutableMap()
        for (e in entries) if (e.type.isInitial) m[e.prayer] = m.getValue(e.prayer) + e.delta
        return m
    }

    /** Active (not undone) make-ups grouped by the date they were made up. */
    fun madeUpByDate(entries: List<LedgerEntry>): Map<LocalDate, PrayerCounts> {
        val out = HashMap<LocalDate, MutableMap<Prayer, Int>>()
        for (e in entries) {
            if (e.type == EntryType.MADE_UP && !e.isUndone) {
                val m = out.getOrPut(e.effectiveDate) { emptyCounts().toMutableMap() }
                m[e.prayer] = m.getValue(e.prayer) - e.delta
            }
        }
        return out
    }

    fun madeUpOn(entries: List<LedgerEntry>, date: LocalDate): PrayerCounts =
        madeUpByDate(entries)[date] ?: emptyCounts()

    fun totalMadeUp(entries: List<LedgerEntry>): PrayerCounts {
        val m = emptyCounts().toMutableMap()
        for (e in entries) if (e.type == EntryType.MADE_UP && !e.isUndone) m[e.prayer] = m.getValue(e.prayer) - e.delta
        return m
    }

    /**
     * Make-ups are assumed to pay the OLDEST debt first (initial estimate, then confirmed missed
     * prayers by date). A confirmed missed prayer is still outstanding only if fewer than
     * [balance] counted missed prayers are newer than it.
     */
    fun isAlreadyMadeUp(recordDate: LocalDate, otherCountedDates: Collection<LocalDate>, balance: Int): Boolean =
        otherCountedDates.count { it.isAfter(recordDate) } >= balance

    data class Breakdown(val fromEstimate: Int, val fromConfirmedMissed: Int)

    fun breakdown(balance: Int, countedMissed: Int): Breakdown {
        val c = minOf(balance, countedMissed).coerceAtLeast(0)
        return Breakdown((balance - c).coerceAtLeast(0), c)
    }
}

enum class RejectReason { INVALID_QUANTITY, NOTHING_OUTSTANDING, WOULD_BE_NEGATIVE, ALREADY_UNDONE, NOT_UNDOABLE, NO_CHANGE, NOT_FOUND }

sealed interface LedgerPlan {
    data class Apply(val entries: List<NewEntry>) : LedgerPlan
    data class Rejected(val reason: RejectReason) : LedgerPlan
}

sealed interface StatusPlan {
    data object NoLedgerChange : StatusPlan
    data object AddDebt : StatusPlan
    data object RemoveDebt : StatusPlan
    data class NeedsDecision(val canReduceBalance: Boolean) : StatusPlan
}

data class InitialChangeLine(
    val prayer: Prayer,
    val currentInitial: Int,
    val requested: Int,
    val appliedDelta: Int,
    val clamped: Boolean,
)

object QadaaPlanner {

    fun makeUp(balances: PrayerCounts, prayer: Prayer, quantity: Int, date: LocalDate, batchId: String? = null): LedgerPlan {
        if (quantity <= 0) return LedgerPlan.Rejected(RejectReason.INVALID_QUANTITY)
        val bal = balances.of(prayer)
        if (bal <= 0) return LedgerPlan.Rejected(RejectReason.NOTHING_OUTSTANDING)
        if (quantity > bal) return LedgerPlan.Rejected(RejectReason.WOULD_BE_NEGATIVE)
        return LedgerPlan.Apply(listOf(NewEntry(prayer, EntryType.MADE_UP, -quantity, date, batchId = batchId)))
    }

    /** One make-up for every prayer that still has a positive balance; zero balances are skipped. */
    fun fullDay(balances: PrayerCounts, date: LocalDate, batchId: String): LedgerPlan {
        val list = Prayer.entries.filter { balances.of(it) > 0 }
            .map { NewEntry(it, EntryType.MADE_UP, -1, date, batchId = batchId) }
        return if (list.isEmpty()) LedgerPlan.Rejected(RejectReason.NOTHING_OUTSTANDING) else LedgerPlan.Apply(list)
    }

    /** Many make-ups at once (bulk entry / import); all-or-nothing, never below zero. */
    fun bulkMakeUp(balances: PrayerCounts, counts: Map<LocalDate, PrayerCounts>, batchId: String): LedgerPlan {
        if (counts.values.any { c -> c.values.any { it < 0 } }) return LedgerPlan.Rejected(RejectReason.INVALID_QUANTITY)
        val entries = counts.entries.sortedBy { it.key.toEpochDay() }.flatMap { (d, c) ->
            Prayer.entries.filter { c.of(it) > 0 }.map { p -> NewEntry(p, EntryType.MADE_UP, -c.of(p), d, batchId = batchId) }
        }
        if (entries.isEmpty()) return LedgerPlan.Rejected(RejectReason.INVALID_QUANTITY)
        for (p in Prayer.entries) {
            val need = counts.values.sumOf { it.of(p) }
            if (need > balances.of(p)) return LedgerPlan.Rejected(RejectReason.WOULD_BE_NEGATIVE)
        }
        return LedgerPlan.Apply(entries)
    }

    fun undo(target: LedgerEntry, balances: PrayerCounts, today: LocalDate): LedgerPlan {
        if (target.isUndone) return LedgerPlan.Rejected(RejectReason.ALREADY_UNDONE)
        if (!target.type.isUndoable) return LedgerPlan.Rejected(RejectReason.NOT_UNDOABLE)
        val delta = -target.delta
        if (balances.of(target.prayer) + delta < 0) return LedgerPlan.Rejected(RejectReason.WOULD_BE_NEGATIVE)
        return LedgerPlan.Apply(
            listOf(NewEntry(target.prayer, EntryType.UNDO, delta, today, reversesEntryId = target.id, batchId = target.batchId))
        )
    }

    fun correction(balances: PrayerCounts, prayer: Prayer, newBalance: Int, date: LocalDate, note: String): LedgerPlan {
        if (newBalance < 0) return LedgerPlan.Rejected(RejectReason.WOULD_BE_NEGATIVE)
        val delta = newBalance - balances.of(prayer)
        if (delta == 0) return LedgerPlan.Rejected(RejectReason.NO_CHANGE)
        return LedgerPlan.Apply(listOf(NewEntry(prayer, EntryType.CORRECTION, delta, date, note = note.trim())))
    }

    /** Changing the estimate later adds adjustment rows; never deletes history; never goes negative. */
    fun initialChange(entries: List<LedgerEntry>, requested: PrayerCounts): List<InitialChangeLine> {
        val balances = QadaaMath.balances(entries)
        val initial = QadaaMath.initialTotals(entries)
        return Prayer.entries.map { p ->
            val want = requested.of(p).coerceAtLeast(0)
            val desired = want - initial.of(p)
            val bal = balances.of(p)
            val applied = if (bal + desired < 0) -bal else desired
            InitialChangeLine(p, initial.of(p), want, applied, applied != desired)
        }
    }

    fun initialEntries(entries: List<LedgerEntry>, lines: List<InitialChangeLine>, date: LocalDate): List<NewEntry> {
        val type = if (entries.any { it.type.isInitial }) EntryType.INITIAL_ADJUSTMENT else EntryType.INITIAL_ESTIMATE
        return lines.filter { it.appliedDelta != 0 }.map {
            NewEntry(it.prayer, type, it.appliedDelta, date, note = if (it.clamped) SysNote.CLAMPED else "")
        }
    }

    fun statusChange(
        current: DayRecord?,
        newStatus: PrayerStatus,
        isAuto: Boolean,
        autoCountsAsQadaa: Boolean,
        alreadyMadeUp: Boolean,
        balance: Int,
    ): StatusPlan {
        val currentlyCounted = current?.countedInQadaa == true
        val targetCounted = newStatus == PrayerStatus.MISSED && (!isAuto || autoCountsAsQadaa)
        return when {
            currentlyCounted && targetCounted -> StatusPlan.NoLedgerChange   // duplicate prevention
            !currentlyCounted && targetCounted -> StatusPlan.AddDebt
            currentlyCounted && !targetCounted ->
                if (!alreadyMadeUp) StatusPlan.RemoveDebt else StatusPlan.NeedsDecision(balance >= 1)
            else -> StatusPlan.NoLedgerChange
        }
    }
}
