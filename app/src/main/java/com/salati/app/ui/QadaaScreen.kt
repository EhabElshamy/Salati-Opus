@file:OptIn(ExperimentalMaterial3Api::class)

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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun QadaaScreen(vm: QadaaViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val st = state
    if (st == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            listOf(R.string.qadaa_overview, R.string.qadaa_history, R.string.qadaa_goal).forEachIndexed { i, r ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(r)) })
            }
        }
        when (tab) {
            0 -> QadaaOverview(st, vm)
            1 -> QadaaHistory(st, vm)
            else -> QadaaGoal(st, vm)
        }
    }
}

@Composable
private fun QadaaOverview(st: QadaaState, vm: QadaaViewModel) {
    var fullDayConfirm by remember { mutableStateOf(false) }
    var confirmAuto by remember { mutableStateOf(false) }
    var actionFor by remember { mutableStateOf<Prayer?>(null) }
    val outstanding = st.balances.total()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (st.autoPending > 0) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.auto_pending, st.autoPending))
                    InfoText(stringResource(R.string.auto_pending_hint))
                    Button(onClick = { confirmAuto = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.auto_confirm_all)) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.total_outstanding, outstanding), style = MaterialTheme.typography.titleLarge)
                    val accrued = outstanding + st.totalMadeUp
                    if (accrued > 0) {
                        LinearProgressIndicator(progress = { st.totalMadeUp.toFloat() / accrued }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.overall_progress, st.totalMadeUp, accrued))
                    }
                    if (outstanding == 0) Text(stringResource(R.string.all_clear))
                }
            }
        }
        item { TodayGoalCard(st) }
        item {
            Button(onClick = { fullDayConfirm = true }, enabled = outstanding > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.made_up_full_day))
            }
        }
        items(Prayer.entries) { p ->
            val bal = st.balances.of(p)
            val b = st.breakdown.getValue(p)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(prayerNameRes(p)), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text(formatNumber(bal), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    InfoText(stringResource(R.string.breakdown_fmt, b.fromEstimate, b.fromConfirmedMissed))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.makeUp(p) }, enabled = bal > 0, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.made_up_one))
                        }
                        OutlinedButton(onClick = { actionFor = p }, modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.more_actions)) }
                    }
                }
            }
        }
        item { CompletionCard(st) }
        item { InfoText(stringResource(R.string.fifo_note)) }
    }
    if (fullDayConfirm) {
        AlertDialog(
            onDismissRequest = { fullDayConfirm = false },
            title = { Text(stringResource(R.string.made_up_full_day)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.full_day_confirm_intro))
                    for (p in Prayer.entries) {
                        val bal = st.balances.of(p)
                        if (bal > 0) Text(stringResource(R.string.full_day_line, stringResource(prayerNameRes(p)), bal, bal - 1))
                        else Text(stringResource(R.string.full_day_skip, stringResource(prayerNameRes(p))))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { fullDayConfirm = false; vm.fullDay() }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { fullDayConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmAuto) {
        AlertDialog(
            onDismissRequest = { confirmAuto = false },
            title = { Text(stringResource(R.string.auto_confirm_all)) },
            text = { Text(stringResource(R.string.auto_confirm_body, st.autoPending)) },
            confirmButton = { TextButton(onClick = { confirmAuto = false; vm.confirmAuto() }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { confirmAuto = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    actionFor?.let { p -> PrayerActionDialog(p, st.balances.of(p), vm) { actionFor = null } }
}

@Composable
private fun TodayGoalCard(st: QadaaState) {
    val goal = st.target.total()
    val done = st.madeUpToday.total()
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.today_goal), style = MaterialTheme.typography.titleMedium)
            if (goal == 0) {
                Text(stringResource(R.string.no_goal_set))
            } else {
                LinearProgressIndicator(progress = { (done.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.today_goal_progress, done, goal))
                if (done >= goal) Text(stringResource(R.string.goal_met))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (p in Prayer.entries) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(prayerShortRes(p)), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.ratio, st.madeUpToday.of(p), st.target.of(p)))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionCard(st: QadaaState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.completion_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.by_target), fontWeight = FontWeight.SemiBold)
            EstimateText(st.byTarget, pace = false)
            InfoText(stringResource(R.string.by_target_assumption))
            Text(stringResource(R.string.by_pace), fontWeight = FontWeight.SemiBold)
            EstimateText(st.byPace, pace = true)
            InfoText(stringResource(R.string.by_pace_assumption, ProgressEstimator.PACE_WINDOW_DAYS, ProgressEstimator.MIN_ACTIVE_DAYS))
        }
    }
}

@Composable
private fun EstimateText(e: CompletionEstimate, pace: Boolean) {
    when (e) {
        CompletionEstimate.AlreadyComplete -> Text(stringResource(R.string.all_clear))
        is CompletionEstimate.Estimated -> {
            Text(stringResource(R.string.estimate_result, e.days, formatDate(e.date, FormatStyle.LONG)))
            if (pace) {
                val rate = e.dailyRates.values.sum()
                Text(stringResource(R.string.pace_detail, String.format(currentLocale(), "%.2f", rate), e.activeDays, e.windowDays))
            }
        }
        is CompletionEstimate.Insufficient -> Text(
            when (e.reason) {
                InsufficientReason.NO_TARGET_FOR_OUTSTANDING_PRAYER -> stringResource(R.string.insufficient_no_target)
                InsufficientReason.TOO_FEW_ACTIVE_DAYS -> stringResource(R.string.insufficient_few_days, e.activeDays, ProgressEstimator.MIN_ACTIVE_DAYS)
                InsufficientReason.NO_ACTIVITY_FOR_A_PRAYER -> stringResource(R.string.insufficient_prayer)
            }
        )
    }
}

@Composable
private fun PrayerActionDialog(p: Prayer, balance: Int, vm: QadaaViewModel, onDismiss: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var qty by remember { mutableStateOf("1") }
    var newBal by remember { mutableStateOf(balance.toString()) }
    var note by remember { mutableStateOf("") }
    val q = parseCount(qty)
    val nb = parseCount(newBal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(prayerNameRes(p))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.current_balance, balance))
                ChoiceChips(listOf(0, 1), mode, { stringResource(if (it == 0) R.string.record_several else R.string.correct_balance) }) { mode = it }
                if (mode == 0) {
                    NumberField(qty, { qty = it }, stringResource(R.string.quantity), Modifier.fillMaxWidth(), isError = q == null || q == 0 || q > balance)
                    InfoText(stringResource(R.string.quantity_hint))
                } else {
                    NumberField(newBal, { newBal = it }, stringResource(R.string.new_balance), Modifier.fillMaxWidth(), isError = nb == null)
                    OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.correction_note)) }, modifier = Modifier.fillMaxWidth())
                    InfoText(stringResource(R.string.correction_hint))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = if (mode == 0) q != null && q in 1..balance else nb != null && nb != balance,
                onClick = {
                    if (mode == 0 && q != null) vm.makeUp(p, q) else if (nb != null) vm.correct(p, nb, note)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun QadaaHistory(st: QadaaState, vm: QadaaViewModel) {
    var undoTarget by remember { mutableStateOf<LedgerEntry?>(null) }
    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(currentLocale())
    if (st.history.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.history_empty)) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { InfoText(stringResource(R.string.history_hint)) }
        items(st.history, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(entryTypeRes(e.type)) + " · " + stringResource(prayerNameRes(e.prayer)),
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                        )
                        Text(if (e.delta > 0) "+" + formatNumber(e.delta) else formatNumber(e.delta), fontWeight = FontWeight.Bold)
                    }
                    InfoText(stringResource(R.string.history_dates, formatDate(e.effectiveDate), Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).format(fmt)))
                    if (e.note.isNotBlank()) InfoText(sysNoteRes(e.note)?.let { stringResource(it) } ?: e.note)
                    when {
                        e.isUndone -> InfoText(stringResource(R.string.history_undone))
                        e.type.isUndoable -> TextButton(onClick = { undoTarget = e }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.undo)) }
                        e.type == EntryType.MISSED_CONFIRMED -> InfoText(stringResource(R.string.history_edit_day_hint))
                    }
                }
            }
        }
    }
    undoTarget?.let { e ->
        AlertDialog(
            onDismissRequest = { undoTarget = null },
            title = { Text(stringResource(R.string.undo)) },
            text = { Text(stringResource(R.string.undo_confirm, stringResource(entryTypeRes(e.type)), stringResource(prayerNameRes(e.prayer)), e.delta)) },
            confirmButton = { TextButton(onClick = { undoTarget = null; vm.undo(e.id) }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { undoTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun QadaaGoal(st: QadaaState, vm: QadaaViewModel) {
    var mode by remember(st.settings.targetMode) { mutableStateOf(st.settings.target()) }
    var custom by remember(st.settings.customTarget) {
        mutableStateOf(Prayer.entries.associateWith { st.settings.customTarget[it.name] ?: 0 })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.daily_target))
        ChoiceChips(TargetMode.entries.toList(), mode, { stringResource(if (it == TargetMode.FULL_DAY) R.string.target_full_day else R.string.target_custom) }) { mode = it }
        if (mode == TargetMode.FULL_DAY) {
            InfoText(stringResource(R.string.target_full_day_hint))
        } else {
            for (p in Prayer.entries) {
                Stepper(custom.of(p), { v -> custom = custom + (p to v) }, 0, 50, stringResource(prayerNameRes(p)))
            }
        }
        Button(onClick = { vm.setTarget(mode, custom) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.save)) }
        InfoText(stringResource(R.string.target_note))
    }
}
