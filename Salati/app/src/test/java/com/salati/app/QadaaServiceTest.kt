package com.salati.app

import com.salati.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QadaaServiceTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val store = InMemoryStore()
    private val svc = QadaaService(store, clock = { 1L }, today = { today })
    private fun bal(p: Prayer = Prayer.FAJR) = runBlocking { QadaaMath.balances(store.allEntries()).of(p) }
    private fun set(d: LocalDate, s: PrayerStatus, decision: MadeUpDecision? = null, p: Prayer = Prayer.FAJR) =
        runBlocking { svc.setStatus(d, p, s, autoCountsAsQadaa = false, decision = decision) }

    @Test fun missedIsAddedExactlyOnce() {
        set(today, PrayerStatus.MISSED)
        set(today, PrayerStatus.MISSED)
        set(today, PrayerStatus.MISSED)
        assertEquals(1, bal())
        assertEquals(1, store.entries.count { it.type == EntryType.MISSED_CONFIRMED })
    }

    @Test fun changingMissedToPrayedRemovesUnpaidDebt() {
        set(today, PrayerStatus.MISSED)
        val r = set(today, PrayerStatus.ON_TIME)
        assertEquals(StatusOutcome.Applied(LedgerEffect.REMOVED_FROM_QADAA), r)
        assertEquals(0, bal())
        assertNull(store.records[today to Prayer.FAJR]!!.debtEntryId)
    }

    @Test fun toggleBackAndForthNeverDoubleCounts() {
        repeat(3) {
            set(today, PrayerStatus.MISSED)
            set(today, PrayerStatus.LATE)
        }
        set(today, PrayerStatus.MISSED)
        assertEquals(1, bal())
    }

    @Test fun pastDateEditUsesThatDate() {
        val d = today.minusDays(10)
        set(d, PrayerStatus.MISSED)
        assertEquals(1, bal())
        assertEquals(d, store.entries.single().effectiveDate)
    }

    @Test fun makeUpsPayOldestDebtFirst() = runBlocking {
        svc.applyInitial(mapOf(Prayer.FAJR to 5))
        set(today.minusDays(1), PrayerStatus.MISSED)       // 6
        svc.makeUp(Prayer.FAJR, 1)                          // 5, pays initial estimate first
        val r = set(today.minusDays(1), PrayerStatus.ON_TIME)
        assertEquals(StatusOutcome.Applied(LedgerEffect.REMOVED_FROM_QADAA), r)
        assertEquals(4, bal())
    }

    @Test fun alreadyMadeUpNeedsExplicitDecision() = runBlocking {
        val d1 = today.minusDays(2)
        val d2 = today.minusDays(1)
        set(d1, PrayerStatus.MISSED)
        set(d2, PrayerStatus.MISSED)
        svc.makeUp(Prayer.FAJR, 1)                           // balance 1; d1 counted as made up
        val r = set(d1, PrayerStatus.ON_TIME)
        assertTrue(r is StatusOutcome.NeedsDecision && r.canReduceBalance)
        assertEquals(1, bal())                               // nothing changed silently
        assertEquals(PrayerStatus.MISSED, store.records[d1 to Prayer.FAJR]!!.status)
        set(d1, PrayerStatus.ON_TIME, MadeUpDecision.REDUCE_BALANCE)
        assertEquals(0, bal())
        assertEquals(PrayerStatus.ON_TIME, store.records[d1 to Prayer.FAJR]!!.status)
    }

    @Test fun keepBalanceWhenNothingLeftToReduce() = runBlocking {
        set(today, PrayerStatus.MISSED)
        svc.makeUp(Prayer.FAJR, 1)
        val r = set(today, PrayerStatus.ON_TIME)
        assertTrue(r is StatusOutcome.NeedsDecision && !r.canReduceBalance)
        // Reduce is refused when balance is 0
        assertTrue(set(today, PrayerStatus.ON_TIME, MadeUpDecision.REDUCE_BALANCE) is StatusOutcome.NeedsDecision)
        assertEquals(StatusOutcome.Applied(LedgerEffect.KEPT_BALANCE), set(today, PrayerStatus.ON_TIME, MadeUpDecision.KEEP_BALANCE))
        assertEquals(0, bal())
        assertTrue(store.entries.any { it.type == EntryType.MISSED_REVERSED && it.delta == 0 && it.note == SysNote.KEPT_BALANCE })
    }

    @Test fun balancesNeverGoNegative() = runBlocking {
        assertEquals(OpResult.Failure(RejectReason.NOTHING_OUTSTANDING), svc.makeUp(Prayer.FAJR, 1))
        svc.applyInitial(mapOf(Prayer.FAJR to 2))
        assertEquals(OpResult.Failure(RejectReason.WOULD_BE_NEGATIVE), svc.makeUp(Prayer.FAJR, 3))
        assertEquals(OpResult.Failure(RejectReason.INVALID_QUANTITY), svc.makeUp(Prayer.FAJR, 0))
        assertEquals(OpResult.Failure(RejectReason.WOULD_BE_NEGATIVE), svc.correct(Prayer.FAJR, -1, ""))
        assertEquals(2, bal())
    }

    @Test fun undoRestoresAndCannotRepeat() = runBlocking {
        svc.applyInitial(mapOf(Prayer.ASR to 3))
        val r = svc.makeUp(Prayer.ASR, 2) as OpResult.Success
        assertEquals(1, bal(Prayer.ASR))
        assertTrue(svc.undo(r.entryIds.single()) is OpResult.Success)
        assertEquals(3, bal(Prayer.ASR))
        assertEquals(OpResult.Failure(RejectReason.ALREADY_UNDONE), svc.undo(r.entryIds.single()))
        assertEquals(3, bal(Prayer.ASR))
        // audit trail kept: original + undo entry both present
        assertTrue(store.entries.any { it.type == EntryType.MADE_UP && it.isUndone })
        assertTrue(store.entries.any { it.type == EntryType.UNDO })
    }

    @Test fun undoOfCorrectionRefusedIfItWouldGoNegative() = runBlocking {
        svc.applyInitial(mapOf(Prayer.FAJR to 5))
        val c = svc.correct(Prayer.FAJR, 10, "found more") as OpResult.Success
        svc.makeUp(Prayer.FAJR, 8)
        assertEquals(OpResult.Failure(RejectReason.WOULD_BE_NEGATIVE), svc.undo(c.entryIds.single()))
        assertEquals(2, bal())
    }

    @Test fun missedEntriesAreNotUndoableFromHistory() = runBlocking {
        set(today, PrayerStatus.MISSED)
        val id = store.entries.single().id
        assertEquals(OpResult.Failure(RejectReason.NOT_UNDOABLE), svc.undo(id))
    }

    @Test fun fullDaySkipsZeroBalancesAndUndoesAsBatch() = runBlocking {
        svc.applyInitial(mapOf(Prayer.FAJR to 2, Prayer.DHUHR to 1))
        val r = svc.makeUpFullDay() as OpResult.Success
        assertEquals(2, r.entryIds.size)
        assertEquals(1, bal(Prayer.FAJR))
        assertEquals(0, bal(Prayer.DHUHR))
        assertEquals(0, bal(Prayer.ASR))
        assertTrue(svc.undoBatch(r.batchId!!) is OpResult.Success)
        assertEquals(2, bal(Prayer.FAJR))
        assertEquals(1, bal(Prayer.DHUHR))
        assertEquals(OpResult.Failure(RejectReason.NOTHING_OUTSTANDING), QadaaService(InMemoryStore()).makeUpFullDay())
    }

    @Test fun autoMissedWaitsForConfirmationAndCountsOnce() = runBlocking {
        val c = listOf(today to Prayer.ISHA)
        assertEquals(1, svc.applyAutoMiss(c, autoCountsAsQadaa = false))
        assertEquals(0, svc.applyAutoMiss(c, autoCountsAsQadaa = false))   // never overwrites
        assertEquals(0, bal(Prayer.ISHA))
        assertEquals(1, svc.confirmAutoMissed())
        assertEquals(0, svc.confirmAutoMissed())
        assertEquals(1, bal(Prayer.ISHA))
        // explicitly choosing Missed afterwards does not double count
        set(today, PrayerStatus.MISSED, p = Prayer.ISHA)
        assertEquals(1, bal(Prayer.ISHA))
    }

    @Test fun autoMissedNeverOverwritesUserRecord() = runBlocking {
        set(today, PrayerStatus.ON_TIME, p = Prayer.MAGHRIB)
        assertEquals(0, svc.applyAutoMiss(listOf(today to Prayer.MAGHRIB), autoCountsAsQadaa = true))
        assertEquals(PrayerStatus.ON_TIME, store.records[today to Prayer.MAGHRIB]!!.status)
    }

    @Test fun congregationOnlyForPrayed() = runBlocking {
        set(today, PrayerStatus.MISSED)
        assertFalse(svc.setCongregation(today, Prayer.FAJR, true))
        set(today, PrayerStatus.ON_TIME)
        assertTrue(svc.setCongregation(today, Prayer.FAJR, true))
        set(today, PrayerStatus.MISSED)
        assertFalse(store.records[today to Prayer.FAJR]!!.congregation)
    }

    @Test fun changingEstimateAddsAdjustmentsAndClamps() = runBlocking {
        svc.applyInitial(Prayer.entries.associateWith { 365 })
        svc.makeUp(Prayer.FAJR, 300)
        val before = store.entries.size
        val lines = svc.applyInitial(Prayer.entries.associateWith { 100 })
        val fajr = lines.first { it.prayer == Prayer.FAJR }
        assertTrue(fajr.clamped)
        assertEquals(0, bal(Prayer.FAJR))
        assertEquals(100, bal(Prayer.DHUHR))
        assertTrue(store.entries.size > before)                // nothing deleted
        assertTrue(store.entries.filter { it.type == EntryType.INITIAL_ADJUSTMENT }.isNotEmpty())
    }
}
