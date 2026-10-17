package com.salati.app.domain

import java.time.LocalDate
import java.util.UUID

/** Persistence abstraction: all ledger orchestration is unit-testable without Android. */
interface LedgerStore {
    suspend fun <T> transaction(block: suspend () -> T): T
    suspend fun allEntries(): List<LedgerEntry>
    suspend fun insertEntry(e: NewEntry, createdAt: Long): Long
    suspend fun markUndone(id: Long, byId: Long)
    suspend fun record(date: LocalDate, prayer: Prayer): DayRecord?
    suspend fun allRecords(): List<DayRecord>
    suspend fun upsertRecord(r: DayRecord, updatedAt: Long)
}

enum class LedgerEffect { NONE, ADDED_TO_QADAA, REMOVED_FROM_QADAA, REDUCED_BALANCE, KEPT_BALANCE }
enum class MadeUpDecision { REDUCE_BALANCE, KEEP_BALANCE }

sealed interface StatusOutcome {
    data class Applied(val effect: LedgerEffect) : StatusOutcome
    data class NeedsDecision(
        val date: LocalDate,
        val prayer: Prayer,
        val newStatus: PrayerStatus,
        val canReduceBalance: Boolean,
    ) : StatusOutcome
}

sealed interface OpResult {
    data class Success(val entryIds: List<Long>, val batchId: String?) : OpResult
    data class Failure(val reason: RejectReason) : OpResult
}

class QadaaService(
    private val store: LedgerStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    suspend fun setStatus(
        date: LocalDate,
        prayer: Prayer,
        status: PrayerStatus,
        autoCountsAsQadaa: Boolean,
        decision: MadeUpDecision? = null,
    ): StatusOutcome = store.transaction {
        val current = store.record(date, prayer)
        val balance = QadaaMath.balances(store.allEntries()).of(prayer)
        val others = store.allRecords()
            .filter { it.prayer == prayer && it.countedInQadaa && it.date != date }
            .map { it.date }
        val alreadyMadeUp = current?.countedInQadaa == true && QadaaMath.isAlreadyMadeUp(date, others, balance)
        val plan = QadaaPlanner.statusChange(current, status, false, autoCountsAsQadaa, alreadyMadeUp, balance)
        val now = clock()
        val congregation = if (status.isPrayed) current?.congregation ?: false else false
        val base = DayRecord(date, prayer, status, congregation, autoMarked = false, debtEntryId = current?.debtEntryId)
        when (plan) {
            StatusPlan.NoLedgerChange -> {
                store.upsertRecord(base, now)
                StatusOutcome.Applied(LedgerEffect.NONE)
            }
            StatusPlan.AddDebt -> {
                val id = store.insertEntry(NewEntry(prayer, EntryType.MISSED_CONFIRMED, 1, date), now)
                store.upsertRecord(base.copy(debtEntryId = id), now)
                StatusOutcome.Applied(LedgerEffect.ADDED_TO_QADAA)
            }
            StatusPlan.RemoveDebt -> {
                reverseDebt(current!!, -1, "", now)
                store.upsertRecord(base.copy(debtEntryId = null), now)
                StatusOutcome.Applied(LedgerEffect.REMOVED_FROM_QADAA)
            }
            is StatusPlan.NeedsDecision -> when {
                decision == MadeUpDecision.REDUCE_BALANCE && plan.canReduceBalance -> {
                    reverseDebt(current!!, -1, SysNote.REDUCED_BALANCE, now)
                    store.upsertRecord(base.copy(debtEntryId = null), now)
                    StatusOutcome.Applied(LedgerEffect.REDUCED_BALANCE)
                }
                decision == MadeUpDecision.KEEP_BALANCE -> {
                    reverseDebt(current!!, 0, SysNote.KEPT_BALANCE, now)
                    store.upsertRecord(base.copy(debtEntryId = null), now)
                    StatusOutcome.Applied(LedgerEffect.KEPT_BALANCE)
                }
                else -> StatusOutcome.NeedsDecision(date, prayer, status, plan.canReduceBalance)
            }
        }
    }

    private suspend fun reverseDebt(record: DayRecord, delta: Int, note: String, now: Long) {
        val debtId = record.debtEntryId ?: return
        val id = store.insertEntry(
            NewEntry(record.prayer, EntryType.MISSED_REVERSED, delta, record.date, note = note, reversesEntryId = debtId), now
        )
        store.markUndone(debtId, id)
    }

    suspend fun setCongregation(date: LocalDate, prayer: Prayer, value: Boolean): Boolean = store.transaction {
        val current = store.record(date, prayer)
        if (current == null || !current.status.isPrayed) {
            false
        } else {
            store.upsertRecord(current.copy(congregation = value), clock())
            true
        }
    }

    /** Auto-labelling never overwrites anything the user recorded. */
    suspend fun applyAutoMiss(candidates: List<Pair<LocalDate, Prayer>>, autoCountsAsQadaa: Boolean): Int = store.transaction {
        var n = 0
        val now = clock()
        for ((d, p) in candidates) {
            if (store.record(d, p) != null) continue
            val id = if (autoCountsAsQadaa) {
                store.insertEntry(NewEntry(p, EntryType.MISSED_CONFIRMED, 1, d, note = SysNote.AUTO), now)
            } else null
            store.upsertRecord(DayRecord(d, p, PrayerStatus.MISSED, false, autoMarked = true, debtEntryId = id), now)
            n++
        }
        n
    }

    suspend fun confirmAutoMissed(): Int = store.transaction {
        var n = 0
        val now = clock()
        for (r in store.allRecords()) {
            if (r.autoMarked && r.status == PrayerStatus.MISSED && !r.countedInQadaa) {
                val id = store.insertEntry(NewEntry(r.prayer, EntryType.MISSED_CONFIRMED, 1, r.date, note = SysNote.CONFIRMED_AUTO), now)
                store.upsertRecord(r.copy(autoMarked = false, debtEntryId = id), now)
                n++
            }
        }
        n
    }

    private suspend fun applyPlan(plan: LedgerPlan, batchId: String?): OpResult = when (plan) {
        is LedgerPlan.Rejected -> OpResult.Failure(plan.reason)
        is LedgerPlan.Apply -> {
            val now = clock()
            OpResult.Success(plan.entries.map { store.insertEntry(it, now) }, batchId)
        }
    }

    suspend fun makeUp(prayer: Prayer, quantity: Int, date: LocalDate = today()): OpResult = store.transaction {
        val batch = UUID.randomUUID().toString()
        applyPlan(QadaaPlanner.makeUp(QadaaMath.balances(store.allEntries()), prayer, quantity, date, batch), batch)
    }

    suspend fun makeUpFullDay(date: LocalDate = today()): OpResult = store.transaction {
        val batch = UUID.randomUUID().toString()
        applyPlan(QadaaPlanner.fullDay(QadaaMath.balances(store.allEntries()), date, batch), batch)
    }

    suspend fun undo(entryId: Long): OpResult = store.transaction {
        val entries = store.allEntries()
        val target = entries.firstOrNull { it.id == entryId }
        if (target == null) {
            OpResult.Failure(RejectReason.NOT_FOUND)
        } else when (val plan = QadaaPlanner.undo(target, QadaaMath.balances(entries), today())) {
            is LedgerPlan.Rejected -> OpResult.Failure(plan.reason)
            is LedgerPlan.Apply -> {
                val id = store.insertEntry(plan.entries.single(), clock())
                store.markUndone(target.id, id)
                OpResult.Success(listOf(id), target.batchId)
            }
        }
    }

    /** Undo every still-active, undoable entry of a batch (e.g. a "full day" make-up). */
    suspend fun undoBatch(batchId: String): OpResult = store.transaction {
        val ids = store.allEntries().filter { it.batchId == batchId && it.type.isUndoable && !it.isUndone }.map { it.id }
        if (ids.isEmpty()) {
            OpResult.Failure(RejectReason.ALREADY_UNDONE)
        } else {
            val done = mutableListOf<Long>()
            var failure: OpResult.Failure? = null
            for (id in ids) {
                when (val r = undo(id)) {
                    is OpResult.Success -> done += r.entryIds
                    is OpResult.Failure -> { failure = r; break }
                }
            }
            failure ?: OpResult.Success(done, batchId)
        }
    }

    suspend fun correct(prayer: Prayer, newBalance: Int, note: String): OpResult = store.transaction {
        applyPlan(QadaaPlanner.correction(QadaaMath.balances(store.allEntries()), prayer, newBalance, today(), note), null)
    }

    suspend fun previewInitial(requested: PrayerCounts): List<InitialChangeLine> =
        QadaaPlanner.initialChange(store.allEntries(), requested)

    suspend fun applyInitial(requested: PrayerCounts): List<InitialChangeLine> = store.transaction {
        val entries = store.allEntries()
        val lines = QadaaPlanner.initialChange(entries, requested)
        val now = clock()
        QadaaPlanner.initialEntries(entries, lines, today()).forEach { store.insertEntry(it, now) }
        lines
    }
}
