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

private enum class MainTab(val label: Int) { TODAY(R.string.tab_today), QADAA(R.string.tab_qadaa), CALENDAR(R.string.tab_calendar), STATS(R.string.tab_stats), SETTINGS(R.string.tab_settings) }

@Composable
fun MainScaffold(appVm: AppViewModel, todayVm: TodayViewModel, qadaaVm: QadaaViewModel, calendarVm: CalendarViewModel, statsVm: StatsViewModel) {
    var tab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    val host = remember { SnackbarHostState() }
    EventSnackbarEffect(appVm, host)
    EventSnackbarEffect(todayVm, host)
    EventSnackbarEffect(qadaaVm, host)
    EventSnackbarEffect(calendarVm, host)
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(tab.label)) }) },
        snackbarHost = { SnackbarHost(host) },
        bottomBar = {
            NavigationBar {
                for (t in MainTab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    MainTab.TODAY -> Icons.Filled.Home
                                    MainTab.QADAA -> Icons.Filled.CheckCircle
                                    MainTab.CALENDAR -> Icons.Filled.DateRange
                                    MainTab.STATS -> Icons.AutoMirrored.Filled.List
                                    MainTab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(t.label), maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                MainTab.TODAY -> TodayScreen(todayVm)
                MainTab.QADAA -> QadaaScreen(qadaaVm)
                MainTab.CALENDAR -> CalendarScreen(calendarVm)
                MainTab.STATS -> StatsScreen(statsVm)
                MainTab.SETTINGS -> SettingsScreen(appVm)
            }
        }
    }
}

@Composable
fun TodayScreen(vm: TodayViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val decision by vm.pendingDecision.collectAsStateWithLifecycle()
    val st = state
    if (st == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val locale = currentLocale()
    val today = st.now.toLocalDate()
    val current = Prayer.entries.lastOrNull { !st.times.of(it).isAfter(st.now) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(formatDate(today, java.time.format.FormatStyle.FULL), style = MaterialTheme.typography.titleMedium) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.next_prayer), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.next_prayer_at, stringResource(prayerNameRes(st.next.prayer)), formatTime(st.next.time)),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.countdown, formatCountdown(java.time.Duration.between(st.now, st.next.time), locale)),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    )
                    InfoText(stringResource(R.string.sunrise_at, formatTime(st.times.sunrise)))
                }
            }
        }
        items(Prayer.entries) { p ->
            val time = st.times.of(p)
            PrayerStatusCard(
                prayer = p, time = time, record = st.records[p], highlight = p == current,
                enabled = !time.isAfter(st.now),
                onStatus = { vm.setStatus(today, p, it) },
                onCongregation = { vm.setCongregation(today, p, it) },
            )
        }
        item { InfoText(stringResource(R.string.future_prayer_note)) }
        item {
            InfoText(
                stringResource(
                    R.string.times_basis,
                    stringResource(methodRes(st.settings.method())), stringResource(asrRes(st.settings.asr())),
                    formatNumber(st.settings.latitude), formatNumber(st.settings.longitude),
                )
            )
        }
        item { InfoText(stringResource(R.string.times_disclaimer)) }
    }
    decision?.let { d -> MadeUpDecisionDialog(d) { vm.resolveDecision(it) } }
}
