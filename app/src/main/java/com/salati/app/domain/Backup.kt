package com.salati.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

const val BACKUP_APP_ID = "com.salati.app"
const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class RecordDto(
    val date: String,
    val prayer: String,
    val status: String,
    val congregation: Boolean = false,
    val autoMarked: Boolean = false,
    val debtEntryId: Long? = null,
    val updatedAt: Long = 0L,
)

@Serializable
data class EntryDto(
    val id: Long,
    val prayer: String,
    val type: String,
    val delta: Int,
    val effectiveDate: String,
    val createdAt: Long,
    val note: String = "",
    val batchId: String? = null,
    val undoneByEntryId: Long? = null,
    val reversesEntryId: Long? = null,
)

@Serializable
data class BackupFile(
    val app: String = BACKUP_APP_ID,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val exportedAt: String,
    val settings: AppSettings,
    val records: List<RecordDto>,
    val entries: List<EntryDto>,
)

enum class BackupErrorCode {
    INVALID_JSON, WRONG_APP, UNSUPPORTED_VERSION, INVALID_SETTINGS, INVALID_RECORD, DUPLICATE_RECORD,
    INVALID_ENTRY, DUPLICATE_ENTRY_ID, BROKEN_REFERENCE, DOUBLE_COUNTED, NEGATIVE_BALANCE
}

data class BackupIssue(val code: BackupErrorCode, val detail: String)

data class BackupSummary(val exportedAt: String, val records: Int, val entries: Int, val balances: PrayerCounts)

sealed interface BackupParseResult {
    data class Valid(val file: BackupFile, val summary: BackupSummary) : BackupParseResult
    data class Invalid(val issues: List<BackupIssue>) : BackupParseResult
}

object BackupCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decodeAndValidate(text: String): BackupParseResult {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            return BackupParseResult.Invalid(listOf(BackupIssue(BackupErrorCode.INVALID_JSON, e.message?.take(200) ?: "")))
        }
        val issues = validate(file)
        return if (issues.isEmpty()) {
            BackupParseResult.Valid(file, BackupSummary(file.exportedAt, file.records.size, file.entries.size, balancesOf(file)))
        } else {
            BackupParseResult.Invalid(issues)
        }
    }

    private fun date(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

    fun balancesOf(file: BackupFile): PrayerCounts {
        val m = emptyCounts().toMutableMap()
        for (e in file.entries) Prayer.parse(e.prayer)?.let { m[it] = m.getValue(it) + e.delta }
        return m
    }

    fun validate(file: BackupFile): List<BackupIssue> {
        val issues = mutableListOf<BackupIssue>()
        if (file.app != BACKUP_APP_ID) issues += BackupIssue(BackupErrorCode.WRONG_APP, file.app)
        if (file.formatVersion != BACKUP_FORMAT_VERSION) issues += BackupIssue(BackupErrorCode.UNSUPPORTED_VERSION, file.formatVersion.toString())
        if (issues.isNotEmpty()) return issues

        val s = file.settings
        val settingsOk = s.language in setOf("ar", "en") &&
            s.latitude in -90.0..90.0 && s.longitude in -180.0..180.0 &&
            ThemeMode.parse(s.theme) != null && CalcMethod.parse(s.calcMethod) != null &&
            AsrMethod.parse(s.asrMethod) != null && TargetMode.parse(s.targetMode) != null &&
            s.customTarget.all { Prayer.parse(it.key) != null && it.value in 0..100 } &&
            s.adjustments.all { Prayer.parse(it.key) != null && it.value in -60..60 } &&
            s.qadaaReminderTimes.all { AppSettings.parseTime(it) != null } &&
            s.estimateYears in 0.0..QadaaMath.MAX_YEARS &&
            (s.qadaaStartDate.isBlank() || AppSettings.parseDate(s.qadaaStartDate) != null) &&
            (s.gridStartDate.isBlank() || AppSettings.parseDate(s.gridStartDate) != null)
        if (!settingsOk) issues += BackupIssue(BackupErrorCode.INVALID_SETTINGS, "settings")

        val byId = HashMap<Long, EntryDto>()
        for (e in file.entries) {
            if (e.id <= 0 || Prayer.parse(e.prayer) == null || EntryType.parse(e.type) == null || date(e.effectiveDate) == null) {
                issues += BackupIssue(BackupErrorCode.INVALID_ENTRY, "entry ${e.id}")
            }
            if (byId.put(e.id, e) != null) issues += BackupIssue(BackupErrorCode.DUPLICATE_ENTRY_ID, "entry ${e.id}")
        }
        for (e in file.entries) {
            val u = e.undoneByEntryId
            val r = e.reversesEntryId
            if (u != null && u !in byId) issues += BackupIssue(BackupErrorCode.BROKEN_REFERENCE, "entry ${e.id} undoneBy $u")
            if (r != null && r !in byId) issues += BackupIssue(BackupErrorCode.BROKEN_REFERENCE, "entry ${e.id} reverses $r")
        }

        val keys = HashSet<String>()
        val debtRefs = HashSet<Long>()
        for (rec in file.records) {
            val st = PrayerStatus.parse(rec.status)
            if (date(rec.date) == null || Prayer.parse(rec.prayer) == null || st == null) {
                issues += BackupIssue(BackupErrorCode.INVALID_RECORD, "${rec.date} ${rec.prayer}")
                continue
            }
            if (!keys.add("${rec.date}|${rec.prayer}")) issues += BackupIssue(BackupErrorCode.DUPLICATE_RECORD, "${rec.date} ${rec.prayer}")
            val debt = rec.debtEntryId
            if (debt != null) {
                val e = byId[debt]
                if (e == null || e.type != EntryType.MISSED_CONFIRMED.name || e.prayer != rec.prayer ||
                    e.undoneByEntryId != null || st != PrayerStatus.MISSED
                ) {
                    issues += BackupIssue(BackupErrorCode.BROKEN_REFERENCE, "${rec.date} ${rec.prayer} -> $debt")
                }
                if (!debtRefs.add(debt)) issues += BackupIssue(BackupErrorCode.DOUBLE_COUNTED, "${rec.date} ${rec.prayer} -> $debt")
            }
        }
        for ((p, b) in balancesOf(file)) if (b < 0) issues += BackupIssue(BackupErrorCode.NEGATIVE_BALANCE, "${p.name}=$b")
        return issues
    }
}
