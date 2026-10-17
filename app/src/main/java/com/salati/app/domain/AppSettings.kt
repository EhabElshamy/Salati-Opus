package com.salati.app.domain

import kotlinx.serialization.Serializable
import java.time.LocalTime

const val DEFAULT_LAT = 30.5877   // Zagazig, Sharkia - reference point only; the user must confirm
const val DEFAULT_LNG = 31.5020

@Serializable
data class AppSettings(
    val language: String = "ar",
    val theme: String = "SYSTEM",
    val latitude: Double = DEFAULT_LAT,
    val longitude: Double = DEFAULT_LNG,
    val locationConfirmed: Boolean = false,
    val calcMethod: String = "EGYPTIAN",
    val asrMethod: String = "SHAFII",
    val adjustments: Map<String, Int> = emptyMap(),
    val setupComplete: Boolean = false,
    val trackingStartEpochMs: Long = 0L,
    val estimateYears: Double = 0.0,
    val autoMissedCountsAsQadaa: Boolean = false,
    val targetMode: String = "FULL_DAY",
    val customTarget: Map<String, Int> = mapOf("FAJR" to 1, "DHUHR" to 1, "ASR" to 1, "MAGHRIB" to 1, "ISHA" to 1),
    val prayerNotifEnabled: Boolean = false,
    val prayerNotifMuted: Boolean = false,
    val qadaaNotifEnabled: Boolean = false,
    val qadaaNotifMuted: Boolean = false,
    val qadaaReminderTimes: List<String> = listOf("20:00"),
) {
    fun method(): CalcMethod = CalcMethod.parse(calcMethod) ?: CalcMethod.EGYPTIAN
    fun asr(): AsrMethod = AsrMethod.parse(asrMethod) ?: AsrMethod.SHAFII
    fun themeMode(): ThemeMode = ThemeMode.parse(theme) ?: ThemeMode.SYSTEM
    fun target(): TargetMode = TargetMode.parse(targetMode) ?: TargetMode.FULL_DAY
    fun adjustment(p: Prayer): Int = adjustments[p.name] ?: 0

    fun dailyTarget(): PrayerCounts = when (target()) {
        TargetMode.FULL_DAY -> Prayer.entries.associateWith { 1 }
        TargetMode.CUSTOM -> Prayer.entries.associateWith { (customTarget[it.name] ?: 0).coerceAtLeast(0) }
    }

    fun calcParams(): CalcParams =
        CalcParams(latitude, longitude, method(), asr(), Prayer.entries.associateWith { adjustment(it) })

    fun reminderTimes(): List<LocalTime> = qadaaReminderTimes.mapNotNull { parseTime(it) }.distinct().sorted()

    companion object {
        fun parseTime(s: String): LocalTime? = runCatching {
            val parts = s.split(":")
            require(parts.size == 2)
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        }.getOrNull()
    }
}
