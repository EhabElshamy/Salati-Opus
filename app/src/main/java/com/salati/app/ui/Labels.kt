package com.salati.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.salati.app.R
import com.salati.app.domain.AsrMethod
import com.salati.app.domain.BackupErrorCode
import com.salati.app.domain.CalcMethod
import com.salati.app.domain.EntryType
import com.salati.app.domain.PeriodType
import com.salati.app.domain.Prayer
import com.salati.app.domain.PrayerStatus
import com.salati.app.domain.RejectReason
import com.salati.app.domain.SysNote
import com.salati.app.domain.ThemeMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun prayerNameRes(p: Prayer): Int = when (p) {
    Prayer.FAJR -> R.string.prayer_fajr
    Prayer.DHUHR -> R.string.prayer_dhuhr
    Prayer.ASR -> R.string.prayer_asr
    Prayer.MAGHRIB -> R.string.prayer_maghrib
    Prayer.ISHA -> R.string.prayer_isha
}

fun prayerShortRes(p: Prayer): Int = when (p) {
    Prayer.FAJR -> R.string.prayer_short_fajr
    Prayer.DHUHR -> R.string.prayer_short_dhuhr
    Prayer.ASR -> R.string.prayer_short_asr
    Prayer.MAGHRIB -> R.string.prayer_short_maghrib
    Prayer.ISHA -> R.string.prayer_short_isha
}

fun statusRes(s: PrayerStatus): Int = when (s) {
    PrayerStatus.NOT_RECORDED -> R.string.status_not_recorded
    PrayerStatus.ON_TIME -> R.string.status_on_time
    PrayerStatus.LATE -> R.string.status_late
    PrayerStatus.MISSED -> R.string.status_missed
}

fun entryTypeRes(t: EntryType): Int = when (t) {
    EntryType.INITIAL_ESTIMATE -> R.string.entry_initial_estimate
    EntryType.INITIAL_ADJUSTMENT -> R.string.entry_initial_adjustment
    EntryType.MISSED_CONFIRMED -> R.string.entry_missed_confirmed
    EntryType.MISSED_REVERSED -> R.string.entry_missed_reversed
    EntryType.MADE_UP -> R.string.entry_made_up
    EntryType.CORRECTION -> R.string.entry_correction
    EntryType.UNDO -> R.string.entry_undo
}

fun sysNoteRes(note: String): Int? = when (note) {
    SysNote.AUTO -> R.string.note_auto
    SysNote.KEPT_BALANCE -> R.string.note_kept_balance
    SysNote.REDUCED_BALANCE -> R.string.note_reduced_balance
    SysNote.CLAMPED -> R.string.note_clamped
    SysNote.CONFIRMED_AUTO -> R.string.note_confirmed_auto
    else -> null
}

fun rejectRes(r: RejectReason): Int = when (r) {
    RejectReason.INVALID_QUANTITY -> R.string.reject_invalid_quantity
    RejectReason.NOTHING_OUTSTANDING -> R.string.reject_nothing_outstanding
    RejectReason.WOULD_BE_NEGATIVE -> R.string.reject_would_be_negative
    RejectReason.ALREADY_UNDONE -> R.string.reject_already_undone
    RejectReason.NOT_UNDOABLE -> R.string.reject_not_undoable
    RejectReason.NO_CHANGE -> R.string.reject_no_change
    RejectReason.NOT_FOUND -> R.string.reject_not_found
}

fun periodRes(t: PeriodType): Int = when (t) {
    PeriodType.DAY -> R.string.period_day
    PeriodType.WEEK -> R.string.period_week
    PeriodType.MONTH -> R.string.period_month
    PeriodType.QUARTER -> R.string.period_quarter
    PeriodType.HALF_YEAR -> R.string.period_half_year
    PeriodType.YEAR -> R.string.period_year
}

fun backupErrorRes(c: BackupErrorCode): Int = when (c) {
    BackupErrorCode.INVALID_JSON -> R.string.backup_err_invalid_json
    BackupErrorCode.WRONG_APP -> R.string.backup_err_wrong_app
    BackupErrorCode.UNSUPPORTED_VERSION -> R.string.backup_err_version
    BackupErrorCode.INVALID_SETTINGS -> R.string.backup_err_settings
    BackupErrorCode.INVALID_RECORD -> R.string.backup_err_record
    BackupErrorCode.DUPLICATE_RECORD -> R.string.backup_err_duplicate_record
    BackupErrorCode.INVALID_ENTRY -> R.string.backup_err_entry
    BackupErrorCode.DUPLICATE_ENTRY_ID -> R.string.backup_err_duplicate_entry
    BackupErrorCode.BROKEN_REFERENCE -> R.string.backup_err_reference
    BackupErrorCode.DOUBLE_COUNTED -> R.string.backup_err_double_counted
    BackupErrorCode.NEGATIVE_BALANCE -> R.string.backup_err_negative
}

fun methodRes(m: CalcMethod): Int = when (m) {
    CalcMethod.EGYPTIAN -> R.string.method_egyptian
    CalcMethod.MWL -> R.string.method_mwl
    CalcMethod.KARACHI -> R.string.method_karachi
}

fun asrRes(m: AsrMethod): Int = when (m) {
    AsrMethod.SHAFII -> R.string.asr_shafii
    AsrMethod.HANAFI -> R.string.asr_hanafi
}

fun themeRes(t: ThemeMode): Int = when (t) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

fun weekdayShortRes(d: DayOfWeek): Int = when (d) {
    DayOfWeek.SATURDAY -> R.string.dow_sa
    DayOfWeek.SUNDAY -> R.string.dow_su
    DayOfWeek.MONDAY -> R.string.dow_mo
    DayOfWeek.TUESDAY -> R.string.dow_tu
    DayOfWeek.WEDNESDAY -> R.string.dow_we
    DayOfWeek.THURSDAY -> R.string.dow_th
    DayOfWeek.FRIDAY -> R.string.dow_fr
}

fun messageRes(m: UiMessage): Int = when (m) {
    UiMessage.ADDED_TO_QADAA -> R.string.msg_added_to_qadaa
    UiMessage.REMOVED_FROM_QADAA -> R.string.msg_removed_from_qadaa
    UiMessage.REDUCED_BALANCE -> R.string.msg_reduced_balance
    UiMessage.KEPT_BALANCE -> R.string.msg_kept_balance
    UiMessage.SAVED -> R.string.msg_saved
    UiMessage.MADE_UP -> R.string.msg_made_up
    UiMessage.UNDONE -> R.string.msg_undone
    UiMessage.CORRECTED -> R.string.msg_corrected
    UiMessage.CONFIRMED_AUTO -> R.string.msg_confirmed_auto
    UiMessage.EXPORTED -> R.string.msg_exported
    UiMessage.EXPORT_FAILED -> R.string.msg_export_failed
    UiMessage.RESTORED -> R.string.msg_restored
    UiMessage.ESTIMATE_SAVED -> R.string.msg_estimate_saved
    UiMessage.FAILED -> R.string.msg_failed
    UiMessage.LOCATION_FAILED -> R.string.msg_location_failed
}

fun eventText(ctx: Context, e: UiEvent): String {
    val base = ctx.getString(messageRes(e.message))
    return if (e.reject != null) base + " " + ctx.getString(rejectRes(e.reject)) else base
}

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

@Composable
fun formatTime(t: ZonedDateTime): String =
    t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()))

@Composable
fun formatDate(d: LocalDate, style: FormatStyle = FormatStyle.MEDIUM): String =
    d.format(DateTimeFormatter.ofLocalizedDate(style).withLocale(currentLocale()))

@Composable
fun formatPattern(d: LocalDate, pattern: String): String =
    d.format(DateTimeFormatter.ofPattern(pattern, currentLocale()))

@Composable
fun formatNumber(n: Number): String = String.format(currentLocale(), "%s", n)

fun formatCountdown(d: Duration, locale: Locale): String {
    val s = d.seconds.coerceAtLeast(0)
    return String.format(locale, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}
