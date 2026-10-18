package com.salati.app

import com.salati.app.domain.DayRecord
import com.salati.app.domain.LedgerEntry
import com.salati.app.domain.LedgerStore
import com.salati.app.domain.NewEntry
import com.salati.app.domain.Prayer
import java.time.LocalDate

/** Same contract as the Room repository, kept in memory for tests. */
class InMemoryStore : LedgerStore {
    val entries = mutableListOf<LedgerEntry>()
    val records = linkedMapOf<Pair<LocalDate, Prayer>, DayRecord>()
    private var nextId = 1L

    override suspend fun <T> transaction(block: suspend () -> T): T = block()
    override suspend fun allEntries(): List<LedgerEntry> = entries.toList()
    override suspend fun insertEntry(e: NewEntry, createdAt: Long): Long {
        val id = nextId++
        entries += LedgerEntry(id, e.prayer, e.type, e.delta, e.effectiveDate, createdAt, e.note, e.batchId, null, e.reversesEntryId)
        return id
    }
    override suspend fun markUndone(id: Long, byId: Long) {
        val i = entries.indexOfFirst { it.id == id }
        entries[i] = entries[i].copy(undoneByEntryId = byId)
    }
    override suspend fun record(date: LocalDate, prayer: Prayer): DayRecord? = records[date to prayer]
    override suspend fun allRecords(): List<DayRecord> = records.values.toList()
    override suspend fun upsertRecord(r: DayRecord, updatedAt: Long) {
        records[r.date to r.prayer] = r
    }
}
