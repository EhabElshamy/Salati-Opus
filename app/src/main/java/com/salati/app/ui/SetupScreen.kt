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

@Composable
fun SetupScreen(vm: AppViewModel, settings: AppSettings) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var lat by rememberSaveable { mutableStateOf(settings.latitude.toString()) }
    var lng by rememberSaveable { mutableStateOf(settings.longitude.toString()) }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var yearsText by rememberSaveable { mutableStateOf("") }
    var perPrayer by rememberSaveable { mutableStateOf(Prayer.entries.associate { it.name to "0" }) }
    val pp = Prayer.entries.associateWith { perPrayer[it.name] ?: "" }

    val latV = parseCoordinate(lat, 90.0)
    val lngV = parseCoordinate(lng, 180.0)
    val years = QadaaMath.parseYears(yearsText.ifBlank { "0" })
    val values = Prayer.entries.associateWith { parseCount(pp.getValue(it)) }
    val valuesOk = values.values.all { it != null }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.setup_step, step + 1, 4), style = MaterialTheme.typography.labelLarge)
            when (step) {
                0 -> {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.setup_welcome))
                    SectionTitle(stringResource(R.string.language))
                    ChoiceChips(listOf("ar", "en"), settings.language, { if (it == "ar") "العربية" else "English" }) { lang ->
                        vm.update { it.copy(language = lang) }
                    }
                    InfoText(stringResource(R.string.language_later))
                    InfoText(stringResource(R.string.privacy_short))
                }
                1 -> {
                    SectionTitle(stringResource(R.string.location))
                    Text(stringResource(R.string.setup_location_intro))
                    LocationEditor(lat, lng, { lat = it; confirmed = false }, { lng = it; confirmed = false })
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(value = confirmed, enabled = latV != null && lngV != null, role = Role.Checkbox, onValueChange = { confirmed = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = confirmed, onCheckedChange = null, enabled = latV != null && lngV != null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.confirm_coordinates))
                    }
                    InfoText(stringResource(R.string.setup_method_note))
                }
                2 -> {
                    SectionTitle(stringResource(R.string.estimate_title))
                    EstimateEditor(
                        yearsText,
                        { t ->
                            yearsText = t
                            QadaaMath.parseYears(t)?.let { y ->
                                val d = QadaaMath.estimateDays(y).toString()
                                perPrayer = Prayer.entries.associate { it.name to d }
                            }
                        },
                        pp,
                        { p, v -> perPrayer = perPrayer + (p.name to v) },
                    )
                }
                else -> {
                    SectionTitle(stringResource(R.string.setup_review))
                    Text(stringResource(R.string.review_location, lat, lng))
                    Text(stringResource(R.string.review_method, stringResource(methodRes(CalcMethod.EGYPTIAN)), stringResource(asrRes(AsrMethod.SHAFII))))
                    Text(stringResource(R.string.review_years, formatNumber(years ?: 0.0)))
                    for (p in Prayer.entries) {
                        Text(stringResource(R.string.review_prayer_line, stringResource(prayerNameRes(p)), values[p] ?: 0))
                    }
                    InfoText(stringResource(R.string.estimate_disclaimer))
                    InfoText(stringResource(R.string.review_change_later))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.back)) }
                }
                val canNext = when (step) {
                    1 -> latV != null && lngV != null && confirmed
                    2 -> years != null && valuesOk
                    else -> true
                }
                Button(
                    onClick = {
                        if (step < 3) step++
                        else if (latV != null && lngV != null && years != null && valuesOk) {
                            vm.completeSetup(settings.language, latV, lngV, years, values.mapValues { it.value ?: 0 })
                        }
                    },
                    enabled = canNext,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text(stringResource(if (step < 3) R.string.next else R.string.finish_setup)) }
            }
        }
    }
}
