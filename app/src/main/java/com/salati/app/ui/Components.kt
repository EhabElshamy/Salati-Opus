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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.salati.app.ui.theme.StatusColors
import java.time.ZonedDateTime

@Composable
fun EventSnackbarEffect(vm: BaseViewModel, host: SnackbarHostState) {
    val ctx = LocalContext.current
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            val undo = if (e.message == UiMessage.MADE_UP && e.batchId != null) ctx.getString(R.string.undo) else null
            val result = host.showSnackbar(eventText(ctx, e), actionLabel = undo, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed && e.batchId != null) vm.undoBatch(e.batchId)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun statusColor(r: DayRecord?): Color = when {
    r == null || r.status == PrayerStatus.NOT_RECORDED -> StatusColors.unrecorded
    r.status == PrayerStatus.ON_TIME -> StatusColors.onTime
    r.status == PrayerStatus.LATE -> StatusColors.late
    r.autoMarked -> StatusColors.auto
    else -> StatusColors.missed
}

@Composable
fun StatusDot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun PrayerStatusCard(
    prayer: Prayer,
    time: ZonedDateTime?,
    record: DayRecord?,
    highlight: Boolean,
    enabled: Boolean,
    onStatus: (PrayerStatus) -> Unit,
    onCongregation: (Boolean) -> Unit,
) {
    val status = record?.status ?: PrayerStatus.NOT_RECORDED
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(statusColor(record), 12)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(prayerNameRes(prayer)), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (time != null) Text(formatTime(time), style = MaterialTheme.typography.titleMedium)
            }
            val all = PrayerStatus.entries
            for (rowItems in listOf(all.take(2), all.drop(2))) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (st in rowItems) {
                        FilterChip(
                            selected = status == st,
                            onClick = { if (status != st || record?.autoMarked == true) onStatus(st) },
                            label = { Text(stringResource(statusRes(st)), maxLines = 1) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
            }
            if (record?.autoMarked == true && status == PrayerStatus.MISSED) {
                InfoText(stringResource(if (record.countedInQadaa) R.string.auto_marked_counted else R.string.auto_marked_hint))
            } else if (record?.countedInQadaa == true) {
                InfoText(stringResource(R.string.counted_in_qadaa))
            }
            val cong = record?.congregation == true
            val congEnabled = enabled && status.isPrayed
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = cong, enabled = congEnabled, role = Role.Checkbox, onValueChange = onCongregation),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = cong, onCheckedChange = null, enabled = congEnabled)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.in_congregation))
            }
        }
    }
}

@Composable
fun MadeUpDecisionDialog(d: StatusOutcome.NeedsDecision, onChoose: (MadeUpDecision?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoose(null) },
        title = { Text(stringResource(R.string.decision_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.decision_body, stringResource(prayerNameRes(d.prayer)), formatDate(d.date)))
                if (!d.canReduceBalance) Text(stringResource(R.string.decision_cannot_reduce))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (d.canReduceBalance) {
                    TextButton(onClick = { onChoose(MadeUpDecision.REDUCE_BALANCE) }) { Text(stringResource(R.string.decision_reduce)) }
                }
                TextButton(onClick = { onChoose(MadeUpDecision.KEEP_BALANCE) }) { Text(stringResource(R.string.decision_keep)) }
                TextButton(onClick = { onChoose(null) }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int, max: Int, label: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(formatNumber(value), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 40.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.increase))
        }
    }
}

@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, decimal: Boolean = false, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/** Requests location permission only when the user taps "Use GPS". */
@Composable
fun rememberGpsRequest(onResult: (Double?, Double?) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) LocationFetcher.fetch(ctx) { l -> callback(l?.latitude, l?.longitude) } else callback(null, null)
    }
    return {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) LocationFetcher.fetch(ctx) { l -> callback(l?.latitude, l?.longitude) }
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

@Composable
fun LocationEditor(lat: String, lng: String, onLat: (String) -> Unit, onLng: (String) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val request = rememberGpsRequest { la, lo ->
        busy = false
        if (la != null && lo != null) {
            failed = false
            onLat(String.format(java.util.Locale.US, "%.5f", la))
            onLng(String.format(java.util.Locale.US, "%.5f", lo))
        } else failed = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { busy = true; failed = false; request() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Filled.LocationOn, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (busy) R.string.gps_locating else R.string.gps_use))
        }
        if (failed) InfoText(stringResource(R.string.gps_failed))
        InfoText(stringResource(R.string.location_permission_note))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(lat, onLat, stringResource(R.string.latitude), Modifier.weight(1f), decimal = true, isError = parseCoordinate(lat, 90.0) == null)
            NumberField(lng, onLng, stringResource(R.string.longitude), Modifier.weight(1f), decimal = true, isError = parseCoordinate(lng, 180.0) == null)
        }
    }
}

/** Years -> per-prayer estimate editor (used in setup and later in settings). */
@Composable
fun EstimateEditor(
    yearsText: String,
    onYears: (String) -> Unit,
    perPrayer: Map<Prayer, String>,
    onPrayer: (Prayer, String) -> Unit,
) {
    val years = QadaaMath.parseYears(yearsText)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Text(stringResource(R.string.estimate_disclaimer), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        NumberField(yearsText, onYears, stringResource(R.string.years_missed), Modifier.fillMaxWidth(), decimal = true, isError = yearsText.isNotBlank() && years == null)
        InfoText(stringResource(R.string.years_hint))
        if (years != null) {
            Text(
                stringResource(R.string.estimate_formula, formatNumber(years), QadaaMath.DAYS_PER_YEAR, QadaaMath.estimateDays(years)),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
        }
        SectionTitle(stringResource(R.string.estimate_preview))
        InfoText(stringResource(R.string.estimate_adjust_hint))
        for (p in Prayer.entries) {
            val v = perPrayer[p] ?: ""
            NumberField(v, { onPrayer(p, it) }, stringResource(prayerNameRes(p)), Modifier.fillMaxWidth(), isError = parseCount(v) == null)
        }
        InfoText(stringResource(R.string.witr_note))
    }
}
