package com.salati.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.salati.app.domain.DayRecord
import com.salati.app.domain.EntryDto
import com.salati.app.domain.EntryType
import com.salati.app.domain.LedgerEntry
import com.salati.app.domain.Prayer
import com.salati.app.domain.PrayerStatus
import com.salati.app.domain.RecordDto
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "settings")
data class SettingsEntity(@PrimaryKey val id: Int = 0, val json: String)

@Entity(tableName = "prayer_records", primaryKeys = ["date", "prayer"])
data class PrayerRecordEntity(
    val date: String,
    val prayer: String,
    val status: String,
    val congregation: Boolean,
    val autoMarked: Boolean,
    val debtEntryId: Long?,
    val updatedAt: Long,
)

@Entity(tableName = "qadaa_entries", indices = [Index("effectiveDate"), Index("batchId")])
data class QadaaEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prayer: String,
    val type: String,
    val delta: Int,
    val effectiveDate: String,
    val createdAt: Long,
    val note: String,
    val batchId: String?,
    val undoneByEntryId: Long?,
    val reversesEntryId: Long?,
    /** Added in DB version 2 (Old fashion grid box). */
    val slotDate: String? = null,
)

fun PrayerRecordEntity.toDomain(): DayRecord? {
    val p = Prayer.parse(prayer) ?: return null
    val s = PrayerStatus.parse(status) ?: return null
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    return DayRecord(d, p, s, congregation, autoMarked, debtEntryId)
}

fun DayRecord.toEntity(updatedAt: Long) = PrayerRecordEntity(
    date.toString(), prayer.name, status.name, congregation, autoMarked, debtEntryId, updatedAt
)

fun QadaaEntryEntity.toDomain(): LedgerEntry? {
    val p = Prayer.parse(prayer) ?: return null
    val t = EntryType.parse(type) ?: return null
    val d = runCatching { LocalDate.parse(effectiveDate) }.getOrNull() ?: return null
    return LedgerEntry(
        id, p, t, delta, d, createdAt, note, batchId, undoneByEntryId, reversesEntryId,
        slotDate = slotDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    )
}

fun QadaaEntryEntity.toDto() = EntryDto(id, prayer, type, delta, effectiveDate, createdAt, note, batchId, undoneByEntryId, reversesEntryId, slotDate)
fun EntryDto.toEntity() = QadaaEntryEntity(id, prayer, type, delta, effectiveDate, createdAt, note, batchId, undoneByEntryId, reversesEntryId, slotDate)
fun PrayerRecordEntity.toDto() = RecordDto(date, prayer, status, congregation, autoMarked, debtEntryId, updatedAt)
fun RecordDto.toEntity() = PrayerRecordEntity(date, prayer, status, congregation, autoMarked, debtEntryId, updatedAt)

@Dao
interface SalatiDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun getSettings(): SettingsEntity?

    @Upsert
    suspend fun upsertSettings(s: SettingsEntity)

    @Query("SELECT * FROM prayer_records ORDER BY date")
    fun observeRecords(): Flow<List<PrayerRecordEntity>>

    @Query("SELECT * FROM prayer_records ORDER BY date")
    suspend fun getAllRecords(): List<PrayerRecordEntity>

    @Query("SELECT * FROM prayer_records WHERE date = :date AND prayer = :prayer")
    suspend fun getRecord(date: String, prayer: String): PrayerRecordEntity?

    @Upsert
    suspend fun upsertRecord(r: PrayerRecordEntity)

    @Insert
    suspend fun insertRecords(list: List<PrayerRecordEntity>)

    @Query("SELECT * FROM qadaa_entries ORDER BY id DESC")
    fun observeEntries(): Flow<List<QadaaEntryEntity>>

    @Query("SELECT * FROM qadaa_entries ORDER BY id")
    suspend fun getAllEntries(): List<QadaaEntryEntity>

    @Insert
    suspend fun insertEntry(e: QadaaEntryEntity): Long

    @Insert
    suspend fun insertEntries(list: List<QadaaEntryEntity>)

    @Query("UPDATE qadaa_entries SET undoneByEntryId = :by WHERE id = :id")
    suspend fun markUndone(id: Long, by: Long)

    @Query("DELETE FROM prayer_records")
    suspend fun clearRecords()

    @Query("DELETE FROM qadaa_entries")
    suspend fun clearEntries()
}

@Database(
    entities = [SettingsEntity::class, PrayerRecordEntity::class, QadaaEntryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SalatiDatabase : RoomDatabase() {
    abstract fun dao(): SalatiDao

    companion object {
        fun create(context: Context): SalatiDatabase =
            Room.databaseBuilder(context.applicationContext, SalatiDatabase::class.java, "salati.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: qadaa_entries.slotDate (nullable) — existing rows keep NULL, no data is lost. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE qadaa_entries ADD COLUMN slotDate TEXT")
            }
        }
    }
}
