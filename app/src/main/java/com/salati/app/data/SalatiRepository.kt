package com.salati.app.data

import androidx.room.withTransaction
import com.salati.app.domain.AppSettings
import com.salati.app.domain.AutoMissPolicy
import com.salati.app.domain.BackupCodec
import com.salati.app.domain.BackupFile
import com.salati.app.domain.DayRecord
import com.salati.app.domain.DayTimes
import com.salati.app.domain.LedgerEntry
import com.salati.app.domain.LedgerStore
import com.salati.app.domain.NewEntry
import com.salati.app.domain.Prayer
import com.salati.app.domain.PrayerCounts
import com.salati.app.domain.PrayerTimeCalculator
import com.salati.app.domain.QadaaService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SalatiRepository(private val db: SalatiDatabase) : LedgerStore {
    private val dao = db.dao()
    private val calculator = PrayerTimeCalculator()
    val service = QadaaService(this)

    val settings: Flow<AppSettings> = dao.observeSettings().map { decode(it?.json) }
    val entries: Flow<List<LedgerEntry>> = dao.observeEntries().map { l -> l.mapNotNull { it.toDomain() } }
    val records: Flow<List<DayRecord>> = dao.observeRecords().map { l -> l.mapNotNull { it.toDomain() } }

    private fun decode(json: String?): AppSettings =
        json?.let { runCatching { BackupCodec.json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()

    suspend fun getSettings(): AppSettings = decode(dao.getSettings()?.json)

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings = transaction {
        val next = transform(getSettings())
        dao.upsertSettings(SettingsEntity(0, BackupCodec.json.encodeToString(AppSettings.serializer(), next)))
        next
    }

    fun times(s: AppSettings, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayTimes =
        calculator.compute(date, zone, s.calcParams())

    /** Labels unrecorded prayers as missed once the next prayer time has begun (bounded to 400 days). */
    suspend fun runAutoMiss(now: Instant = Instant.now()): Int {
        val s = getSettings()
        if (!s.setupComplete || s.trackingStartEpochMs <= 0L) return 0
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(s.trackingStartEpochMs)
        val today = now.atZone(zone).toLocalDate()
        val from = maxOf(start.atZone(zone).toLocalDate(), today.minusDays(400))
        val existing = dao.getAllRecords().map { it.date + "|" + it.prayer }.toHashSet()
        val cache = HashMap<LocalDate, DayTimes>()
        val candidates = AutoMissPolicy.candidates(
            now, start, from, today,
            timesFor = { d -> cache.getOrPut(d) { times(s, d, zone) } },
            hasRecord = { d, p -> "$d|${p.name}" in existing },
        )
        return if (candidates.isEmpty()) 0 else service.applyAutoMiss(candidates, s.autoMissedCountsAsQadaa)
    }

    suspend fun completeSetup(language: String, lat: Double, lng: Double, years: Double, values: PrayerCounts) = transaction {
        service.applyInitial(values)
        updateSettings {
            it.copy(
                language = language, latitude = lat, longitude = lng, locationConfirmed = true,
                estimateYears = years, setupComplete = true,
                trackingStartEpochMs = if (it.trackingStartEpochMs > 0) it.trackingStartEpochMs else System.currentTimeMillis(),
            )
        }
    }

    suspend fun exportBackup(): String {
        val file = BackupFile(
            exportedAt = Instant.now().toString(),
            settings = getSettings(),
            records = dao.getAllRecords().map { it.toDto() },
            entries = dao.getAllEntries().map { it.toDto() },
        )
        return BackupCodec.encode(file)
    }

    /** Replaces ALL local data with the (already validated) backup, atomically. */
    suspend fun restore(file: BackupFile) = transaction {
        dao.clearRecords()
        dao.clearEntries()
        dao.insertEntries(file.entries.map { it.toEntity() })
        dao.insertRecords(file.records.map { it.toEntity() })
        dao.upsertSettings(SettingsEntity(0, BackupCodec.json.encodeToString(AppSettings.serializer(), file.settings)))
    }

    // ---- LedgerStore ----
    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }
    override suspend fun allEntries(): List<LedgerEntry> = dao.getAllEntries().mapNotNull { it.toDomain() }
    override suspend fun insertEntry(e: NewEntry, createdAt: Long): Long = dao.insertEntry(
        QadaaEntryEntity(
            prayer = e.prayer.name, type = e.type.name, delta = e.delta, effectiveDate = e.effectiveDate.toString(),
            createdAt = createdAt, note = e.note, batchId = e.batchId, undoneByEntryId = null, reversesEntryId = e.reversesEntryId,
        )
    )
    override suspend fun markUndone(id: Long, byId: Long) = dao.markUndone(id, byId)
    override suspend fun record(date: LocalDate, prayer: Prayer): DayRecord? = dao.getRecord(date.toString(), prayer.name)?.toDomain()
    override suspend fun allRecords(): List<DayRecord> = dao.getAllRecords().mapNotNull { it.toDomain() }
    override suspend fun upsertRecord(r: DayRecord, updatedAt: Long) = dao.upsertRecord(r.toEntity(updatedAt))
}
