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

@Composable
fun StatsScreen(vm: StatsViewModel) {
    val range by vm.rangeDays.collectAsStateWithLifecycle()
    val data by vm.stats.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceChips(listOf(7, 30, 90, 365, 0), range, {
            if (it == 0) stringResource(R.string.range_all) else stringResource(R.string.range_days, it)
        }) { vm.rangeDays.value = it }
        val d = data
        if (d == null) {
            CircularProgressIndicator()
        } else {
            val (stats, made) = d
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatLine(com.salati.app.ui.theme.StatusColors.onTime, R.string.status_on_time, stats.total.onTime)
                    StatLine(com.salati.app.ui.theme.StatusColors.late, R.string.status_late, stats.total.late)
                    StatLine(com.salati.app.ui.theme.StatusColors.missed, R.string.stat_missed_confirmed, stats.total.missedConfirmed)
                    StatLine(com.salati.app.ui.theme.StatusColors.auto, R.string.stat_missed_auto, stats.total.missedAuto)
                    StatLine(com.salati.app.ui.theme.StatusColors.unrecorded, R.string.stat_unrecorded, stats.total.unrecorded)
                    HorizontalDivider()
                    StatLine(MaterialTheme.colorScheme.primary, R.string.in_congregation, stats.total.congregation)
                }
            }
            SectionTitle(stringResource(R.string.stat_per_prayer))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Text("", Modifier.weight(1.4f))
                        for (h in listOf(R.string.stat_h_on_time, R.string.stat_h_late, R.string.stat_h_missed, R.string.stat_h_auto, R.string.stat_h_unrecorded)) {
                            Text(stringResource(h), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    }
                    for (p in Prayer.entries) {
                        val c = stats.perPrayer.getValue(p)
                        Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(prayerNameRes(p)), Modifier.weight(1.4f))
                            for (v in listOf(c.onTime, c.late, c.missedConfirmed, c.missedAuto, c.unrecorded)) {
                                Text(formatNumber(v), Modifier.weight(1f), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            SectionTitle(stringResource(R.string.stat_qadaa_made_up))
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (p in Prayer.entries) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(prayerShortRes(p)), style = MaterialTheme.typography.labelLarge)
                            Text(formatNumber(made.of(p)))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.total), style = MaterialTheme.typography.labelLarge)
                        Text(formatNumber(made.total()), fontWeight = FontWeight.Bold)
                    }
                }
            }
            InfoText(stringResource(R.string.stats_note))
        }
    }
}

@Composable
private fun StatLine(color: Color, label: Int, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 32.dp)) {
        StatusDot(color, 10)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label), Modifier.weight(1f))
        Text(formatNumber(value), fontWeight = FontWeight.SemiBold)
    }
}
