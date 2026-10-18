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
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.salati.app.notify.AlarmScheduler
import com.salati.app.notify.Notifications
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val initial by vm.initialTotals.collectAsStateWithLifecycle()
    val backup by vm.backupUi.collectAsStateWithLifecycle()
    val s = settings ?: return
    val ctx = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val canExact = remember(resumeTick) { AlarmScheduler.canExact(ctx) }
    val hasNotifPermission = remember(resumeTick) { Notifications.hasPermission(ctx) }
    var locationDialog by remember { mutableStateOf(false) }
    var estimateDialog by remember { mutableStateOf(false) }
    var addTime by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.readImport(uri) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++; vm.reschedule() }
    val askNotif = { if (Build.VERSION.SDK_INT >= 33 && !Notifications.hasPermission(ctx)) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.language))
        ChoiceChips(listOf("ar", "en"), s.language, { if (it == "ar") "العربية" else "English" }) { l -> vm.update(true) { it.copy(language = l) } }

        SectionTitle(stringResource(R.string.theme))
        ChoiceChips(ThemeMode.entries.toList(), s.themeMode(), { stringResource(themeRes(it)) }) { t -> vm.update { it.copy(theme = t.name) } }

        SectionTitle(stringResource(R.string.location))
        Text(stringResource(R.string.current_coordinates, formatNumber(s.latitude), formatNumber(s.longitude)))
        OutlinedButton(onClick = { locationDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.change_location)) }

        SectionTitle(stringResource(R.string.calculation))
        ChoiceChips(CalcMethod.entries.toList(), s.method(), { stringResource(methodRes(it)) }) { m -> vm.update(true) { it.copy(calcMethod = m.name) } }
        ChoiceChips(AsrMethod.entries.toList(), s.asr(), { stringResource(asrRes(it)) }) { m -> vm.update(true) { it.copy(asrMethod = m.name) } }
        InfoText(stringResource(R.string.method_note))
        Text(stringResource(R.string.minute_adjustments), fontWeight = FontWeight.SemiBold)
        for (p in Prayer.entries) {
            Stepper(s.adjustment(p), { v -> vm.update(true) { it.copy(adjustments = it.adjustments + (p.name to v)) } }, -30, 30, stringResource(prayerNameRes(p)))
        }

        SectionTitle(stringResource(R.string.tracking))
        SwitchRow(stringResource(R.string.auto_counts_setting), s.autoMissedCountsAsQadaa) { v -> vm.update { it.copy(autoMissedCountsAsQadaa = v) } }
        InfoText(stringResource(R.string.auto_miss_explain))

        SectionTitle(stringResource(R.string.estimate_title))
        Text(stringResource(R.string.initial_current_years, formatNumber(s.estimateYears)))
        for (p in Prayer.entries) Text(stringResource(R.string.review_prayer_line, stringResource(prayerNameRes(p)), initial.of(p)))
        OutlinedButton(onClick = { estimateDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.change_estimate)) }

        SectionTitle(stringResource(R.string.dates_section))
        DateSettingCard(
            title = stringResource(R.string.qadaa_start_title),
            hint = stringResource(R.string.qadaa_start_hint),
            value = s.qadaaStart(),
            isDefault = s.qadaaStartDate.isBlank(),
            defaultLabel = stringResource(R.string.date_default_setup),
            onPick = { d -> vm.update { it.copy(qadaaStartDate = d.toString()) } },
            onReset = { vm.update { it.copy(qadaaStartDate = "") } },
        )
        DateSettingCard(
            title = stringResource(R.string.grid_start_title),
            hint = stringResource(R.string.grid_start_hint),
            value = s.gridStart(),
            isDefault = s.gridStartDate.isBlank(),
            defaultLabel = stringResource(R.string.grid_start_same),
            onPick = { d -> vm.update { it.copy(gridStartDate = d.toString()) } },
            onReset = { vm.update { it.copy(gridStartDate = "") } },
        )

        SectionTitle(stringResource(R.string.notifications))
        SwitchRow(stringResource(R.string.notif_prayer_enable), s.prayerNotifEnabled) { v -> if (v) askNotif(); vm.update(true) { it.copy(prayerNotifEnabled = v) } }
        SwitchRow(stringResource(R.string.notif_prayer_mute), s.prayerNotifMuted, enabled = s.prayerNotifEnabled) { v -> vm.update(true) { it.copy(prayerNotifMuted = v) } }
        SwitchRow(stringResource(R.string.notif_qadaa_enable), s.qadaaNotifEnabled) { v -> if (v) askNotif(); vm.update(true) { it.copy(qadaaNotifEnabled = v) } }
        SwitchRow(stringResource(R.string.notif_qadaa_mute), s.qadaaNotifMuted, enabled = s.qadaaNotifEnabled) { v -> vm.update(true) { it.copy(qadaaNotifMuted = v) } }
        Text(stringResource(R.string.reminder_times), fontWeight = FontWeight.SemiBold)
        for (t in s.reminderTimes()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(currentLocale())), Modifier.weight(1f))
                IconButton(onClick = {
                    val key = String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
                    vm.update(true) { it.copy(qadaaReminderTimes = it.qadaaReminderTimes.filter { x -> AppSettings.parseTime(x) != AppSettings.parseTime(key) }) }
                }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
            }
        }
        OutlinedButton(onClick = { addTime = true }, enabled = s.reminderTimes().size < AlarmScheduler.MAX_REMINDERS, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.add_reminder_time))
        }
        if (!hasNotifPermission) {
            InfoText(stringResource(R.string.notif_permission_missing))
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.grant_permission)) }
        }
        if (!canExact && Build.VERSION.SDK_INT >= 31) {
            InfoText(stringResource(R.string.exact_alarm_off))
            OutlinedButton(onClick = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.allow_exact_alarms)) }
        }
        InfoText(stringResource(R.string.background_limits))
        OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.battery_settings))
        }
        InfoText(stringResource(R.string.notif_not_ruling))

        SectionTitle(stringResource(R.string.backup))
        InfoText(stringResource(R.string.backup_explain))
        Button(onClick = { exportLauncher.launch("salati-backup-" + LocalDate.now() + ".json") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.export_backup)) }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.import_backup))
        }

        SectionTitle(stringResource(R.string.privacy))
        InfoText(stringResource(R.string.privacy_full))
        InfoText(stringResource(R.string.estimate_disclaimer))
        InfoText(stringResource(R.string.version_label, "1.0.0"))
    }

    if (locationDialog) LocationDialog(s, vm) { locationDialog = false }
    if (estimateDialog) EstimateChangeDialog(s, initial, vm) { estimateDialog = false }
    if (addTime) {
        val state = rememberTimePickerState(initialHour = 20, initialMinute = 0)
        AlertDialog(
            onDismissRequest = { addTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val t = String.format(Locale.US, "%02d:%02d", state.hour, state.minute)
                    vm.update(true) { it.copy(qadaaReminderTimes = (it.qadaaReminderTimes + t).distinct().sorted().take(AlarmScheduler.MAX_REMINDERS)) }
                    addTime = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { addTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    when (val b = backup) {
        is BackupUi.Preview -> AlertDialog(
            onDismissRequest = { vm.dismissBackup() },
            title = { Text(stringResource(R.string.restore_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.restore_valid))
                    Text(stringResource(R.string.restore_summary, b.summary.exportedAt, b.summary.records, b.summary.entries))
                    for (p in Prayer.entries) Text(stringResource(R.string.review_prayer_line, stringResource(prayerNameRes(p)), b.summary.balances.of(p)))
                    Text(stringResource(R.string.restore_replace_warning), fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmRestore() }) { Text(stringResource(R.string.restore_confirm)) } },
            dismissButton = { TextButton(onClick = { vm.dismissBackup() }) { Text(stringResource(R.string.cancel)) } },
        )
        is BackupUi.Invalid -> AlertDialog(
            onDismissRequest = { vm.dismissBackup() },
            title = { Text(stringResource(R.string.restore_invalid_title)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.restore_nothing_changed))
                    for (i in b.issues.take(30)) Text("• " + stringResource(backupErrorRes(i.code)) + if (i.detail.isNotBlank()) " (" + i.detail + ")" else "")
                }
            },
            confirmButton = { TextButton(onClick = { vm.dismissBackup() }) { Text(stringResource(R.string.close)) } },
        )
        BackupUi.Idle -> Unit
    }
}

@Composable
private fun LocationDialog(s: AppSettings, vm: AppViewModel, onDismiss: () -> Unit) {
    var lat by remember { mutableStateOf(s.latitude.toString()) }
    var lng by remember { mutableStateOf(s.longitude.toString()) }
    var confirmed by remember { mutableStateOf(false) }
    val la = parseCoordinate(lat, 90.0)
    val lo = parseCoordinate(lng, 180.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_location)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LocationEditor(lat, lng, { lat = it; confirmed = false }, { lng = it; confirmed = false })
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = confirmed, enabled = la != null && lo != null, role = Role.Checkbox, onValueChange = { confirmed = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = confirmed, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.confirm_coordinates))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = la != null && lo != null && confirmed, onClick = {
                if (la != null && lo != null) vm.update(true) { it.copy(latitude = la, longitude = lo, locationConfirmed = true) }
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EstimateChangeDialog(s: AppSettings, initial: PrayerCounts, vm: AppViewModel, onDismiss: () -> Unit) {
    var yearsText by remember { mutableStateOf(if (s.estimateYears == 0.0) "" else s.estimateYears.toString()) }
    var per by remember { mutableStateOf(Prayer.entries.associateWith { initial.of(it).toString() }) }
    var preview by remember { mutableStateOf<List<InitialChangeLine>?>(null) }
    val values = per.mapValues { parseCount(it.value) }
    val years = QadaaMath.parseYears(yearsText.ifBlank { "0" })
    val ok = years != null && values.values.all { it != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_estimate)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val pv = preview
                if (pv == null) {
                    EstimateEditor(yearsText, { t ->
                        yearsText = t
                        QadaaMath.parseYears(t)?.let { y -> val d = QadaaMath.estimateDays(y).toString(); per = Prayer.entries.associateWith { d } }
                    }, per, { p, v -> per = per + (p to v) })
                } else {
                    Text(stringResource(R.string.estimate_change_preview))
                    for (l in pv) {
                        Text(stringResource(R.string.estimate_change_line, stringResource(prayerNameRes(l.prayer)), l.currentInitial, l.requested, l.appliedDelta))
                        if (l.clamped) InfoText(stringResource(R.string.estimate_clamped))
                    }
                    InfoText(stringResource(R.string.estimate_change_keeps_history))
                }
            }
        },
        confirmButton = {
            val scope = rememberCoroutineScope()
            TextButton(enabled = ok, onClick = {
                val v = values.mapValues { it.value ?: 0 }
                if (preview == null) scope.launch { preview = vm.previewInitial(v) }
                else { vm.applyInitial(v, years ?: 0.0); onDismiss() }
            }) { Text(stringResource(if (preview == null) R.string.preview else R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { if (preview != null) preview = null else onDismiss() }) {
                Text(stringResource(if (preview != null) R.string.back else R.string.cancel))
            }
        },
    )
}
