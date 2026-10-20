package com.salati.app.domain

import java.time.LocalDate

enum class Prayer {
    FAJR, DHUHR, ASR, MAGHRIB, ISHA;

    companion object {
        fun parse(value: String): Prayer? = entries.firstOrNull { it.name == value }
    }
}

enum class PrayerStatus {
    NOT_RECORDED, ON_TIME, LATE, MISSED;

    val isPrayed: Boolean get() = this == ON_TIME || this == LATE

    companion object {
        fun parse(value: String): PrayerStatus? = entries.firstOrNull { it.name == value }
    }
}

/** Every change to a qadaa balance is an immutable ledger row (audit trail). */
enum class EntryType {
    INITIAL_ESTIMATE, INITIAL_ADJUSTMENT, MISSED_CONFIRMED, MISSED_REVERSED, MADE_UP, CORRECTION, UNDO;

    val isUndoable: Boolean get() = this == MADE_UP || this == CORRECTION
    val isInitial: Boolean get() = this == INITIAL_ESTIMATE || this == INITIAL_ADJUSTMENT

    companion object {
        fun parse(value: String): EntryType? = entries.firstOrNull { it.name == value }
    }
}

enum class CalcMethod(val fajrAngle: Double, val ishaAngle: Double) {
    EGYPTIAN(19.5, 17.5), MWL(18.0, 17.0), KARACHI(18.0, 18.0);

    companion object {
        fun parse(value: String): CalcMethod? = entries.firstOrNull { it.name == value }
    }
}

enum class AsrMethod(val shadowFactor: Double) {
    SHAFII(1.0), HANAFI(2.0);

    companion object {
        fun parse(value: String): AsrMethod? = entries.firstOrNull { it.name == value }
    }
}

enum class TargetMode {
    FULL_DAY, CUSTOM;

    companion object {
        fun parse(value: String): TargetMode? = entries.firstOrNull { it.name == value }
    }
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun parse(value: String): ThemeMode? = entries.firstOrNull { it.name == value }
    }
}

typealias PrayerCounts = Map<Prayer, Int>

fun emptyCounts(): PrayerCounts = Prayer.entries.associateWith { 0 }
fun PrayerCounts.total(): Int = values.sum()
fun PrayerCounts.of(p: Prayer): Int = this[p] ?: 0

data class LedgerEntry(
    val id: Long,
    val prayer: Prayer,
    val type: EntryType,
    val delta: Int,
    val effectiveDate: LocalDate,
    val createdAt: Long,
    val note: String = "",
    val batchId: String? = null,
    val undoneByEntryId: Long? = null,
    val reversesEntryId: Long? = null,
    /** Old fashion grid box (missed day) this make-up ticks; null = fills the oldest free box. */
    val slotDate: LocalDate? = null,
) {
    val isUndone: Boolean get() = undoneByEntryId != null
}

data class DayRecord(
    val date: LocalDate,
    val prayer: Prayer,
    val status: PrayerStatus,
    val congregation: Boolean = false,
    val autoMarked: Boolean = false,
    /** Id of the MISSED_CONFIRMED ledger entry counting this prayer in qadaa (null = not counted). */
    val debtEntryId: Long? = null,
) {
    val countedInQadaa: Boolean get() = debtEntryId != null
}

data class NewEntry(
    val prayer: Prayer,
    val type: EntryType,
    val delta: Int,
    val effectiveDate: LocalDate,
    val note: String = "",
    val batchId: String? = null,
    val reversesEntryId: Long? = null,
    val slotDate: LocalDate? = null,
)

/** System notes stored in the ledger; the UI translates them. */
object SysNote {
    const val AUTO = "sys:auto"
    const val KEPT_BALANCE = "sys:kept_balance"
    const val REDUCED_BALANCE = "sys:reduced_balance"
    const val CLAMPED = "sys:clamped"
    const val CONFIRMED_AUTO = "sys:confirmed_auto"
}
