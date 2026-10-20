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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.salati.app.ui.theme.SalatiTheme
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    val cs = MaterialTheme.colorScheme
    EventSnackbarEffect(appVm, host)
    EventSnackbarEffect(todayVm, host)
    EventSnackbarEffect(qadaaVm, host)
    EventSnackbarEffect(calendarVm, host)
    Scaffold(
        containerColor = cs.background,
        topBar = {
            if (tab != MainTab.TODAY) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(tab.label),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary,
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = cs.background),
                )
            }
        },
        snackbarHost = { SnackbarHost(host) },
        bottomBar = {
            Column {
                HorizontalDivider(color = cs.outlineVariant)
                NavigationBar(containerColor = cs.surfaceContainerLowest, tonalElevation = 0.dp) {
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
                            label = {
                                Text(
                                    stringResource(t.label), maxLines = 1,
                                    fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = cs.primary,
                                selectedTextColor = cs.primary,
                                indicatorColor = cs.primaryContainer,
                                unselectedIconColor = cs.onSurfaceVariant,
                                unselectedTextColor = cs.onSurfaceVariant,
                            ),
                        )
                    }
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
    val today = st.now.toLocalDate()
    val current = Prayer.entries.lastOrNull { !st.times.of(it).isAfter(st.now) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TodayHero(st) }
        item { PrayerTimesStrip(st, current) }
        item {
            SectionTitle(
                stringResource(R.string.tab_today) + " · " + formatDate(today, FormatStyle.MEDIUM),
                Modifier.padding(horizontal = 20.dp),
            )
        }
        items(Prayer.entries) { p ->
            val time = st.times.of(p)
            Box(Modifier.padding(horizontal = 16.dp)) {
                PrayerStatusCard(
                    prayer = p, time = time, record = st.records[p], highlight = p == current,
                    enabled = !time.isAfter(st.now),
                    onStatus = { vm.setStatus(today, p, it) },
                    onCongregation = { vm.setCongregation(today, p, it) },
                )
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoText(stringResource(R.string.future_prayer_note))
                InfoText(
                    stringResource(
                        R.string.times_basis,
                        stringResource(methodRes(st.settings.method())), stringResource(asrRes(st.settings.asr())),
                        formatNumber(st.settings.latitude), formatNumber(st.settings.longitude),
                    )
                )
                InfoText(stringResource(R.string.times_disclaimer))
            }
        }
    }
    decision?.let { d -> MadeUpDecisionDialog(d) { vm.resolveDecision(it) } }
}

/** Golden-hour header: big clock, Hijri date, mosque skyline and next-prayer panel. */
@Composable
private fun TodayHero(st: TodayState) {
    val cs = MaterialTheme.colorScheme
    val brushes = SalatiTheme.brushes
    val locale = currentLocale()
    val today = st.now.toLocalDate()
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(brushes.hero),
    ) {
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 24.dp).size(260.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(brushes.glow, Color.Transparent))),
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name).uppercase(locale),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = cs.primary,
            )
            Spacer(Modifier.height(10.dp))
            BigClock(st.now)
            hijriDateText(today)?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = cs.primary.copy(alpha = 0.85f))
            }
            MosqueSilhouette(brushes.mosque, Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 12.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(cs.surface.copy(alpha = 0.88f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.next_prayer).uppercase(locale),
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                    )
                    Text(
                        stringResource(prayerNameRes(st.next.prayer)) + " · " + formatTime(st.next.time),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.primary,
                    )
                    Text(
                        stringResource(R.string.countdown, formatCountdown(java.time.Duration.between(st.now, st.next.time), locale)),
                        style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                    )
                }
                VerticalDivider(Modifier.height(52.dp).padding(horizontal = 12.dp), color = cs.outlineVariant)
                Column(Modifier.weight(1f)) {
                    Text(
                        formatDate(today, FormatStyle.MEDIUM),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface,
                    )
                    Text(
                        stringResource(R.string.sunrise_at, formatTime(st.times.sunrise)),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** HH ☾ MM clock; always laid out left-to-right so Arabic mode does not swap hours and minutes. */
@Composable
private fun BigClock(now: ZonedDateTime) {
    val ctx = LocalContext.current
    val locale = currentLocale()
    val cs = MaterialTheme.colorScheme
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val h = now.format(DateTimeFormatter.ofPattern(if (is24) "HH" else "h", locale))
    val m = now.format(DateTimeFormatter.ofPattern("mm", locale))
    val ampm = if (is24) null else now.format(DateTimeFormatter.ofPattern("a", locale))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(h, style = MaterialTheme.typography.displayLarge, color = cs.primary)
            CrescentGlyph(Modifier.padding(horizontal = 10.dp).size(28.dp), color = cs.tertiary)
            Text(m, style = MaterialTheme.typography.displayLarge, color = cs.primary)
            if (ampm != null) {
                Text(
                    ampm, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.primary,
                    modifier = Modifier.padding(start = 6.dp, top = 22.dp),
                )
            }
        }
    }
}

/** Horizontally scrolling prayer-time tiles; the next prayer gets the warm gradient. */
@Composable
private fun PrayerTimesStrip(st: TodayState, current: Prayer?) {
    val cs = MaterialTheme.colorScheme
    val brushes = SalatiTheme.brushes
    val nextToday = st.next.time.toLocalDate() == st.now.toLocalDate()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(Prayer.entries) { p ->
            val isNext = nextToday && p == st.next.prayer
            val shape = RoundedCornerShape(20.dp)
            Column(
                Modifier.width(88.dp)
                    .clip(shape)
                    .then(
                        if (isNext) Modifier.background(brushes.card).border(1.5.dp, cs.primary.copy(alpha = 0.5f), shape)
                        else Modifier.background(cs.surfaceContainerHighest)
                    )
                    .padding(vertical = 14.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PrayerGlyph(p, Modifier.size(32.dp))
                Text(
                    stringResource(prayerNameRes(p)), style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant, maxLines = 1,
                )
                Text(
                    formatTime(st.times.of(p)), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = if (isNext) cs.primary else cs.onSurface, maxLines = 1,
                )
                StatusDot(if (p == current || st.records[p] != null) statusColor(st.records[p]) else Color.Transparent, 6)
            }
        }
    }
}
