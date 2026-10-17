package com.salati.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salati.app.data.SalatiRepository
import com.salati.app.domain.*
import com.salati.app.notify.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class UiMessage {
    ADDED_TO_QADAA, REMOVED_FROM_QADAA, REDUCED_BALANCE, KEPT_BALANCE, SAVED, MADE_UP, UNDONE, CORRECTED,
    CONFIRMED_AUTO, EXPORTED, EXPORT_FAILED, RESTORED, ESTIMATE_SAVED, FAILED, LOCATION_FAILED
}

data class UiEvent(val message: UiMessage, val batchId: String? = null, val reject: RejectReason? = null)

open class BaseViewModel(protected val repo: SalatiRepository) : ViewModel() {
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    fun emit(e: UiEvent) {
        _events.tryEmit(e)
    }

    protected fun handle(r: OpResult, success: UiMessage) = when (r) {
        is OpResult.Success -> emit(UiEvent(success, r.batchId))
        is OpResult.Failure -> emit(UiEvent(UiMessage.FAILED, reject = r.reason))
    }

    fun undoBatch(batchId: String) {
        viewModelScope.launch { handle(repo.service.undoBatch(batchId), UiMessage.UNDONE) }
    }

    fun times(s: AppSettings, date: LocalDate): DayTimes = repo.times(s, date)

    val settingsState: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

open class StatusEditingViewModel(repo: SalatiRepository) : BaseViewModel(repo) {
    val pendingDecision = MutableStateFlow<StatusOutcome.NeedsDecision?>(null)
    val records: StateFlow<List<DayRecord>> = repo.records.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setStatus(date: LocalDate, prayer: Prayer, status: PrayerStatus, decision: MadeUpDecision? = null) {
        viewModelScope.launch {
            val s = repo.getSettings()
            when (val r = repo.service.setStatus(date, prayer, status, s.autoMissedCountsAsQadaa, decision)) {
                is StatusOutcome.NeedsDecision -> pendingDecision.value = r
                is StatusOutcome.Applied -> {
                    pendingDecision.value = null
                    when (r.effect) {
                        LedgerEffect.ADDED_TO_QADAA -> emit(UiEvent(UiMessage.ADDED_TO_QADAA))
                        LedgerEffect.REMOVED_FROM_QADAA -> emit(UiEvent(UiMessage.REMOVED_FROM_QADAA))
                        LedgerEffect.REDUCED_BALANCE -> emit(UiEvent(UiMessage.REDUCED_BALANCE))
                        LedgerEffect.KEPT_BALANCE -> emit(UiEvent(UiMessage.KEPT_BALANCE))
                        LedgerEffect.NONE -> Unit
                    }
                }
            }
        }
    }

    fun resolveDecision(d: MadeUpDecision?) {
        val p = pendingDecision.value ?: return
        pendingDecision.value = null
        if (d != null) setStatus(p.date, p.prayer, p.newStatus, d)
    }

    fun setCongregation(date: LocalDate, prayer: Prayer, value: Boolean) {
        viewModelScope.launch { repo.service.setCongregation(date, prayer, value) }
    }
}

data class TodayState(
    val settings: AppSettings,
    val now: ZonedDateTime,
    val times: DayTimes,
    val next: NextPrayer.Next,
    val records: Map<Prayer, DayRecord>,
)

class TodayViewModel(repo: SalatiRepository) : StatusEditingViewModel(repo) {
    private val ticker = flow {
        while (true) {
            emit(ZonedDateTime.now(ZoneId.systemDefault()))
            delay(1000)
        }
    }

    val state: StateFlow<TodayState?> = combine(repo.settings, records, ticker) { s, recs, now ->
        val today = now.toLocalDate()
        val t = repo.times(s, today, now.zone)
        val tm = repo.times(s, today.plusDays(1), now.zone)
        TodayState(s, now, t, NextPrayer.find(t, tm, now), recs.filter { it.date == today }.associateBy { it.prayer })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            while (true) {
                runCatching { repo.runAutoMiss() }
                delay(60_000)
            }
        }
    }
}

data class QadaaState(
    val settings: AppSettings,
    val today: LocalDate,
    val balances: PrayerCounts,
    val breakdown: Map<Prayer, QadaaMath.Breakdown>,
    val madeUpToday: PrayerCounts,
    val target: PrayerCounts,
    val totalMadeUp: Int,
    val byTarget: CompletionEstimate,
    val byPace: CompletionEstimate,
    val history: List<LedgerEntry>,
    val autoPending: Int,
)

class QadaaViewModel(repo: SalatiRepository) : BaseViewModel(repo) {
    private val todayFlow = flow {
        while (true) {
            emit(LocalDate.now())
            delay(30_000)
        }
    }.distinctUntilChanged()

    val state: StateFlow<QadaaState?> = combine(repo.settings, repo.entries, repo.records, todayFlow) { s, entries, records, today ->
        val balances = QadaaMath.balances(entries)
        val counted = records.filter { it.countedInQadaa }.groupingBy { it.prayer }.eachCount()
        val byDate = QadaaMath.madeUpByDate(entries)
        QadaaState(
            settings = s,
            today = today,
            balances = balances,
            breakdown = Prayer.entries.associateWith { QadaaMath.breakdown(balances.of(it), counted[it] ?: 0) },
            madeUpToday = byDate[today] ?: emptyCounts(),
            target = s.dailyTarget(),
            totalMadeUp = QadaaMath.totalMadeUp(entries).total(),
            byTarget = ProgressEstimator.byTarget(balances, s.dailyTarget(), today),
            byPace = ProgressEstimator.byPace(balances, byDate, today),
            history = entries,
            autoPending = records.count { it.autoMarked && it.status == PrayerStatus.MISSED && !it.countedInQadaa },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun makeUp(p: Prayer, qty: Int = 1, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch { handle(repo.service.makeUp(p, qty, date), UiMessage.MADE_UP) }
    }

    fun fullDay() {
        viewModelScope.launch { handle(repo.service.makeUpFullDay(LocalDate.now()), UiMessage.MADE_UP) }
    }

    fun undo(id: Long) {
        viewModelScope.launch { handle(repo.service.undo(id), UiMessage.UNDONE) }
    }

    fun correct(p: Prayer, newBalance: Int, note: String) {
        viewModelScope.launch { handle(repo.service.correct(p, newBalance, note), UiMessage.CORRECTED) }
    }

    fun confirmAuto() {
        viewModelScope.launch {
            repo.service.confirmAutoMissed()
            emit(UiEvent(UiMessage.CONFIRMED_AUTO))
        }
    }

    fun setTarget(mode: TargetMode, custom: PrayerCounts) {
        viewModelScope.launch {
            repo.updateSettings { s -> s.copy(targetMode = mode.name, customTarget = custom.mapKeys { it.key.name }) }
            emit(UiEvent(UiMessage.SAVED))
        }
    }
}

class CalendarViewModel(repo: SalatiRepository) : StatusEditingViewModel(repo) {
    val entries: StateFlow<List<LedgerEntry>> = repo.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun makeUpOn(p: Prayer, date: LocalDate) {
        viewModelScope.launch { handle(repo.service.makeUp(p, 1, date), UiMessage.MADE_UP) }
    }

    fun undoEntry(id: Long) {
        viewModelScope.launch { handle(repo.service.undo(id), UiMessage.UNDONE) }
    }
}

class StatsViewModel(repo: SalatiRepository) : BaseViewModel(repo) {
    val rangeDays = MutableStateFlow(30)   // 0 = since tracking started

    val stats: StateFlow<Pair<PrayerStats, PrayerCounts>?> =
        combine(repo.settings, repo.records, repo.entries, rangeDays) { s, records, entries, range ->
            withContext(Dispatchers.Default) {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val start = Instant.ofEpochMilli(s.trackingStartEpochMs)
                val trackingDate = start.atZone(zone).toLocalDate()
                val earliestRecord = records.minOfOrNull { it.date } ?: trackingDate
                val from = if (range == 0) minOf(trackingDate, earliestRecord) else today.minusDays(range.toLong() - 1)
                val cache = HashMap<LocalDate, DayTimes>()
                val st = StatsCalculator.compute(records, from, today, start, Instant.now()) { d ->
                    cache.getOrPut(d) { repo.times(s, d, zone) }
                }
                val made = Periods.sum(QadaaMath.madeUpByDate(entries), Period(from, today))
                st to made
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

sealed interface BackupUi {
    data object Idle : BackupUi
    data class Preview(val file: BackupFile, val summary: BackupSummary) : BackupUi
    data class Invalid(val issues: List<BackupIssue>) : BackupUi
}

class AppViewModel(repo: SalatiRepository, private val app: Application) : BaseViewModel(repo) {
    val backupUi = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val initialTotals: StateFlow<PrayerCounts> =
        repo.entries.map { QadaaMath.initialTotals(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyCounts())

    fun update(reschedule: Boolean = false, transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repo.updateSettings(transform)
            if (reschedule) AlarmScheduler.rescheduleAll(app, repo)
        }
    }

    fun reschedule() {
        viewModelScope.launch { AlarmScheduler.rescheduleAll(app, repo) }
    }

    fun completeSetup(language: String, lat: Double, lng: Double, years: Double, values: PrayerCounts) {
        viewModelScope.launch { repo.completeSetup(language, lat, lng, years, values) }
    }

    suspend fun previewInitial(values: PrayerCounts): List<InitialChangeLine> = repo.service.previewInitial(values)

    fun applyInitial(values: PrayerCounts, years: Double) {
        viewModelScope.launch {
            repo.service.applyInitial(values)
            repo.updateSettings { it.copy(estimateYears = years) }
            emit(UiEvent(UiMessage.ESTIMATE_SAVED))
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = repo.exportBackup()
                    val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("no stream")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            emit(UiEvent(if (ok) UiMessage.EXPORTED else UiMessage.EXPORT_FAILED))
        }
    }

    fun readImport(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > 20 * 1024 * 1024) null else bytes.toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            backupUi.value = if (text == null) {
                BackupUi.Invalid(listOf(BackupIssue(BackupErrorCode.INVALID_JSON, "")))
            } else {
                when (val r = withContext(Dispatchers.Default) { BackupCodec.decodeAndValidate(text) }) {
                    is BackupParseResult.Valid -> BackupUi.Preview(r.file, r.summary)
                    is BackupParseResult.Invalid -> BackupUi.Invalid(r.issues)
                }
            }
        }
    }

    fun confirmRestore() {
        val p = backupUi.value as? BackupUi.Preview ?: return
        viewModelScope.launch {
            repo.restore(p.file)
            backupUi.value = BackupUi.Idle
            emit(UiEvent(UiMessage.RESTORED))
            AlarmScheduler.rescheduleAll(app, repo)
        }
    }

    fun dismissBackup() {
        backupUi.value = BackupUi.Idle
    }
}
