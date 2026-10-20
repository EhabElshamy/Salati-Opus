@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.salati.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salati.app.R
import com.salati.app.domain.*
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.FormatStyle

@Composable
fun CalendarScreen(vm: CalendarViewModel) {
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val decision by vm.pendingDecision.collectAsStateWithLifecycle()
    val s = settings ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            listOf(R.string.cal_prayers, R.string.cal_qadaa, R.string.cal_old_fashion).forEachIndexed { i, r ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(r), maxLines = 1) })
            }
        }
        when (tab) {
            0 -> PrayerMonth(vm, s)
            1 -> QadaaPeriods(vm, s)
            else -> OldFashion(vm, s)
        }
    }
    decision?.let { d -> MadeUpDecisionDialog(d) { vm.resolveDecision(it) } }
}

@Composable
private fun Navigator(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous))
        }
        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next))
        }
    }
}

@Composable
private fun PrayerMonth(vm: CalendarViewModel, s: AppSettings) {
    val records by vm.records.collectAsStateWithLifecycle()
    var monthText by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    val month = LocalDate.parse(monthText)
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val byKey = remember(records) { records.associateBy { it.date to it.prayer } }
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Navigator(formatPattern(month, "MMMM yyyy"), { monthText = month.minusMonths(1).toString() }, { monthText = month.plusMonths(1).toString() })
        Row {
            for (i in 0 until 7) {
                Text(stringResource(weekdayShortRes(DayOfWeek.SATURDAY.plus(i.toLong()))), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
        val offset = (month.dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
        val days = month.lengthOfMonth()
        val rows = (offset + days + 6) / 7
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - offset
                    if (idx in 0 until days) {
                        val d = month.plusDays(idx.toLong())
                        val future = d.isAfter(today)
                        Column(
                            Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (d == today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable(enabled = !future) { selected = d }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(formatNumber(d.dayOfMonth), style = MaterialTheme.typography.bodyMedium, color = if (future) MaterialTheme.colorScheme.outline else Color.Unspecified)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                for (p in Prayer.entries) StatusDot(if (future) Color.Transparent else statusColor(byKey[d to p]), 5)
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Legend()
        InfoText(stringResource(R.string.calendar_hint))
    }
    selected?.let { d -> DayEditorDialog(d, vm, s, byKey) { selected = null } }
}

@Composable
private fun Legend() {
    val items = listOf(
        com.salati.app.ui.theme.StatusColors.onTime to R.string.status_on_time,
        com.salati.app.ui.theme.StatusColors.late to R.string.status_late,
        com.salati.app.ui.theme.StatusColors.missed to R.string.stat_missed_confirmed,
        com.salati.app.ui.theme.StatusColors.auto to R.string.stat_missed_auto,
        com.salati.app.ui.theme.StatusColors.unrecorded to R.string.stat_unrecorded,
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((c, r) in items) Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(c, 10)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(r), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DayEditorDialog(date: LocalDate, vm: CalendarViewModel, s: AppSettings, byKey: Map<Pair<LocalDate, Prayer>, DayRecord>, onDismiss: () -> Unit) {
    val times = remember(date, s) { vm.times(s, date) }
    val now = ZonedDateTime.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(formatDate(date, FormatStyle.FULL)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoText(stringResource(R.string.past_edit_hint))
                for (p in Prayer.entries) {
                    val t = times.of(p)
                    PrayerStatusCard(p, t, byKey[date to p], false, !t.isAfter(now), { vm.setStatus(date, p, it) }, { vm.setCongregation(date, p, it) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun periodLabel(type: PeriodType, p: Period): String = when (type) {
    PeriodType.DAY -> formatDate(p.start, FormatStyle.FULL)
    PeriodType.WEEK -> formatDate(p.start) + " – " + formatDate(p.endInclusive)
    PeriodType.MONTH -> formatPattern(p.start, "MMMM yyyy")
    PeriodType.QUARTER -> stringResource(R.string.quarter_label, (p.start.monthValue - 1) / 3 + 1, p.start.year)
    PeriodType.HALF_YEAR -> stringResource(R.string.half_label, if (p.start.monthValue == 1) 1 else 2, p.start.year)
    PeriodType.YEAR -> formatPattern(p.start, "yyyy")
}

@Composable
private fun QadaaPeriods(vm: CalendarViewModel, s: AppSettings) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf(PeriodType.MONTH) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val period = Periods.containing(type, LocalDate.parse(anchor))
    val byDate = remember(entries) { QadaaMath.madeUpByDate(entries) }
    val target = s.dailyTarget()
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChips(PeriodType.entries.toList(), type, { stringResource(periodRes(it)) }) { type = it }
        Navigator(periodLabel(type, period), { anchor = Periods.shift(type, period, -1).start.toString() }, { anchor = Periods.shift(type, period, 1).start.toString() })
        val totals = Periods.sum(byDate, period)
        // Goal days are counted only from the qadaa start date up to today.
        val qStart = s.qadaaStart()
        val effStart = if (period.start.isAfter(qStart)) period.start else qStart
        val effEnd = if (period.endInclusive.isBefore(today)) period.endInclusive else today
        val elapsed = if (effEnd.isBefore(effStart)) 0L else java.time.temporal.ChronoUnit.DAYS.between(effStart, effEnd) + 1
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.period_total, totals.total()), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.period_goal, (target.total() * elapsed).toInt(), elapsed.toInt()))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                Row {
                    Text("", Modifier.weight(2f))
                    for (p in Prayer.entries) Text(stringResource(prayerShortRes(p)), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.total), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
                val monthly = type == PeriodType.QUARTER || type == PeriodType.HALF_YEAR || type == PeriodType.YEAR
                for (sp in Periods.subPeriods(type, period)) {
                    val c = Periods.sum(byDate, sp)
                    Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (monthly) formatPattern(sp.start, "MMM yyyy") else formatPattern(sp.start, "EEE d"), Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                        for (p in Prayer.entries) Text(if (c.of(p) == 0) "·" else formatNumber(c.of(p)), Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(formatNumber(c.total()), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        InfoText(stringResource(R.string.period_hint))
    }
}

@Composable
private fun OldFashion(vm: CalendarViewModel, s: AppSettings) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val bulk by vm.bulk.collectAsStateWithLifecycle()
    val importResult by vm.importResult.collectAsStateWithLifecycle()
    // Each row is one missed day from the grid start; make-ups tick boxes oldest first.
    val origin = s.gridStart()
    val firstSheet = Periods.sheetStart(origin, origin)
    val view = remember(entries, origin) { GridView(origin, entries) }
    val balances = remember(entries) { QadaaMath.balances(entries) }
    val nextSheet = Periods.sheetStart(origin.plusDays(view.nextEmptyAll()), origin)
    // "" = follow the sheet holding the next empty box.
    var sheetText by rememberSaveable { mutableStateOf("") }
    val start = if (sheetText.isEmpty()) nextSheet else Periods.sheetStart(LocalDate.parse(sheetText), origin)
    val blocks = Periods.sheetBlocks(start)
    var pickStart by remember { mutableStateOf(false) }
    var bulkMode by rememberSaveable { mutableStateOf(false) }
    val editing = bulkMode || bulk.isNotEmpty()
    // Pending boxes that are still empty (an import may include boxes that are already ticked).
    val pendingBoxes = remember(bulk, view) {
        bulk.flatMap { (d, c) ->
            Prayer.entries.filter { c.of(it) > 0 && !d.isBefore(origin) && view.state(d, it) == BoxState.EMPTY }.map { d to it }
        }
    }
    val pendingCounts: PrayerCounts = Prayer.entries.associateWith { p -> pendingBoxes.count { it.second == p } }
    var cell by remember { mutableStateOf<Pair<LocalDate, Prayer>?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var photoFailed by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { vm.importBytes(readUriBytes(ctx, uri)) }
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = loadScaledBitmap(ctx, uri)
            if (b == null) photoFailed = true else photo = b
        }
    }
    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val dates = (0 until Periods.SHEET_DAYS).map { start.plusDays(it.toLong()) }
            scope.launch { writeUriBytes(ctx, uri, GridImport.template(dates)) }
        }
    }
    val line = MaterialTheme.colorScheme.outline
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Navigator(
            formatDate(start) + " – " + formatDate(start.plusDays((Periods.SHEET_DAYS - 1).toLong())),
            { if (start.isAfter(firstSheet)) sheetText = start.minusDays(Periods.SHEET_DAYS.toLong()).toString() },
            { sheetText = start.plusDays(Periods.SHEET_DAYS.toLong()).toString() },
        )
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.grid_starts_on, formatDate(origin)),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pickStart = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.change)) }
        }
        Text(
            stringResource(R.string.old_ticked) + " " +
                prayerTotalsLine(Prayer.entries.associateWith { view.ticked(it) }),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { sheetText = firstSheet.toString() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.go_to_start))
            }
            OutlinedButton(onClick = { sheetText = "" }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.go_to_next))
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = editing,
                onClick = { if (bulk.isEmpty()) bulkMode = !bulkMode },
                label = { Text(stringResource(R.string.bulk_entry)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = {
                    fileLauncher.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream",
                    ))
                },
                label = { Text(stringResource(R.string.import_file)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text(stringResource(R.string.scan_photo)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { templateLauncher.launch("salati-grid-" + start + ".csv") },
                label = { Text(stringResource(R.string.import_template)) },
            )
        }
        if (editing) {
            val short = Prayer.entries.filter { pendingCounts.of(it) > balances.of(it) }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.bulk_pending, pendingBoxes.size, pendingBoxes.map { it.first }.distinct().size), fontWeight = FontWeight.Bold)
                    if (pendingBoxes.isNotEmpty()) {
                        Text(prayerTotalsLine(pendingCounts), style = MaterialTheme.typography.bodySmall)
                    }
                    for (pr in short) {
                        Text(
                            stringResource(R.string.bulk_over_balance, stringResource(prayerNameRes(pr)), pendingCounts.of(pr), balances.of(pr)),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    InfoText(stringResource(R.string.bulk_hint))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.saveBulk(pendingBoxes) }, enabled = pendingBoxes.isNotEmpty() && short.isEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.bulk_save, pendingBoxes.size))
                        }
                        TextButton(onClick = { vm.bulkDiscard(); bulkMode = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.bulk_discard))
                        }
                    }
                }
            }
        }
        InfoText(stringResource(R.string.old_fashion_hint))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (block in blocks) {
                Column(Modifier.weight(1f).border(1.dp, line)) {
                    Row {
                        GridCell(stringResource(R.string.old_dp), headerBg, line, Modifier.weight(1.2f), bold = true)
                        for (p in Prayer.entries) GridCell(stringResource(prayerLetterRes(p)), headerBg, line, Modifier.weight(1f), bold = true)
                    }
                    for (d in block) {
                        val k = GridSlots.index(origin, d)
                        val before = k < 0
                        Row {
                            GridCell(
                                stringResource(weekdayShortRes(d.dayOfWeek)), headerBg, line,
                                Modifier.weight(1.2f).clickable(enabled = editing && !before) {
                                    vm.bulkToggleDay(d, Prayer.entries.filter { view.state(d, it) == BoxState.EMPTY })
                                },
                                small = formatNumber(d.dayOfMonth),
                            )
                            for (p in Prayer.entries) {
                                val st = view.state(d, p)
                                val pending = !before && st == BoxState.EMPTY && (bulk[d]?.of(p) ?: 0) > 0
                                val bg = when {
                                    before -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    pending -> MaterialTheme.colorScheme.tertiaryContainer
                                    st == BoxState.MARKED -> MaterialTheme.colorScheme.primaryContainer
                                    st == BoxState.FLOATING -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                                GridCell(
                                    when { st != BoxState.EMPTY -> "✓"; pending -> "+"; else -> "" }, bg, line,
                                    Modifier.weight(1f).clickable(enabled = !before) {
                                        if (editing && st == BoxState.EMPTY) {
                                            vm.bulkToggle(d, p)
                                        } else {
                                            cell = d to p
                                        }
                                    },
                                    highlight = !before && k == view.nextEmpty(p),
                                )
                            }
                        }
                    }
                }
            }
        }
        InfoText(stringResource(R.string.old_fashion_legend))
    }
    importResult?.let { r ->
        ImportResultDialog(
            result = r,
            onReview = {
                val first = vm.acceptImport()
                bulkMode = true
                sheetText = first?.let { Periods.sheetStart(it, origin).toString() } ?: ""
            },
            onDismiss = { vm.dismissImport() },
        )
    }
    photo?.let { b ->
        GridPhotoScanDialog(
            source = b,
            defaultFirstDate = start,
            rightToLeftDefault = s.language == "ar",
            onDismiss = { photo = null },
            onUse = { counts ->
                photo = null
                vm.bulkMergeBoxes(counts)
                bulkMode = true
                counts.keys.minByOrNull { it.toEpochDay() }?.let { sheetText = Periods.sheetStart(it, origin).toString() }
            },
        )
    }
    if (photoFailed) {
        AlertDialog(
            onDismissRequest = { photoFailed = false },
            text = { Text(stringResource(R.string.scan_load_failed)) },
            confirmButton = { TextButton(onClick = { photoFailed = false }) { Text(stringResource(R.string.confirm)) } },
        )
    }
    if (pickStart) {
        PastDatePickerDialog(origin, { pickStart = false }) { d ->
            vm.setGridStart(d)
            sheetText = ""
        }
    }
    cell?.let { (d, p) ->
        val st = view.state(d, p)
        val mine = view.markedEntry(d, p)
        val latestQuick = entries.filter { it.type == EntryType.MADE_UP && !it.isUndone && it.prayer == p && it.slotDate == null }.maxByOrNull { it.id }
        AlertDialog(
            onDismissRequest = { cell = null },
            title = { Text(stringResource(prayerNameRes(p)) + " · " + formatDate(d)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(
                            when (st) {
                                BoxState.MARKED -> R.string.old_box_marked
                                BoxState.FLOATING -> R.string.old_box_floating
                                BoxState.EMPTY -> R.string.old_box_empty
                            }
                        )
                    )
                    Text(stringResource(R.string.current_balance, balances.of(p)))
                    if (st == BoxState.FLOATING && latestQuick != null) {
                        TextButton(onClick = { cell = null; vm.undoEntry(latestQuick.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.old_undo_latest, -latestQuick.delta))
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    st == BoxState.EMPTY -> TextButton(onClick = { cell = null; vm.markBox(d, p) }, enabled = balances.of(p) > 0) {
                        Text(stringResource(R.string.old_mark_box))
                    }
                    st == BoxState.MARKED && mine != null -> TextButton(onClick = { cell = null; vm.undoEntry(mine.id) }) {
                        Text(stringResource(R.string.old_untick_box))
                    }
                    else -> {}
                }
            },
            dismissButton = { TextButton(onClick = { cell = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun GridCell(text: String, bg: Color, line: Color, modifier: Modifier, bold: Boolean = false, small: String? = null, highlight: Boolean = false) {
    Box(
        modifier.height(if (small != null) 30.dp else 26.dp).background(bg).border(if (highlight) 1.5.dp else 0.5.dp, if (highlight) MaterialTheme.colorScheme.primary else line),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontSize = 10.sp, maxLines = 1, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
            if (small != null) Text(small, fontSize = 8.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun prayerLetterRes(p: Prayer): Int = when (p) {
    Prayer.FAJR -> R.string.letter_fajr
    Prayer.DHUHR -> R.string.letter_dhuhr
    Prayer.ASR -> R.string.letter_asr
    Prayer.MAGHRIB -> R.string.letter_maghrib
    Prayer.ISHA -> R.string.letter_isha
}
