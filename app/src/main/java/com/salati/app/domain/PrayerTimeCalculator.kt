package com.salati.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

data class CalcParams(
    val latitude: Double,
    val longitude: Double,
    val method: CalcMethod = CalcMethod.EGYPTIAN,
    val asr: AsrMethod = AsrMethod.SHAFII,
    val adjustmentsMinutes: Map<Prayer, Int> = emptyMap(),
)

data class DayTimes(
    val date: LocalDate,
    val fajr: ZonedDateTime,
    val sunrise: ZonedDateTime,
    val dhuhr: ZonedDateTime,
    val asr: ZonedDateTime,
    val maghrib: ZonedDateTime,
    val isha: ZonedDateTime,
) {
    fun of(p: Prayer): ZonedDateTime = when (p) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
}

/**
 * Local astronomical calculation (standard solar-position algorithm used by PrayTimes.org).
 * Fajr/Isha by sun depression angle, Asr by shadow factor (Shafi'i = 1), Maghrib = sunset.
 * Rounded to the nearest minute, then user minute adjustments are applied. Time zone and DST
 * come from the device's java.time zone rules.
 */
class PrayerTimeCalculator {

    fun compute(date: LocalDate, zone: ZoneId, p: CalcParams): DayTimes {
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - p.longitude / (15.0 * 24.0)
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(3) { t = computeOnce(jd, p, t) }
        val base = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        fun toZoned(localSolarHours: Double, adj: Int): ZonedDateTime {
            val utcHours = localSolarHours - p.longitude / 15.0
            val minutes = (utcHours * 60.0).roundToLong() + adj
            return base.plusSeconds(minutes * 60).atZone(zone)
        }
        val a = p.adjustmentsMinutes
        return DayTimes(
            date = date,
            fajr = toZoned(t[0], a[Prayer.FAJR] ?: 0),
            sunrise = toZoned(t[1], 0),
            dhuhr = toZoned(t[2], a[Prayer.DHUHR] ?: 0),
            asr = toZoned(t[3], a[Prayer.ASR] ?: 0),
            maghrib = toZoned(t[4], a[Prayer.MAGHRIB] ?: 0),
            isha = toZoned(t[5], a[Prayer.ISHA] ?: 0),
        )
    }

    private fun computeOnce(jd: Double, p: CalcParams, t: DoubleArray): DoubleArray {
        val lat = p.latitude
        return doubleArrayOf(
            sunAngleTime(jd, lat, p.method.fajrAngle, t[0] / 24.0, ccw = true),
            sunAngleTime(jd, lat, RISE_SET_ANGLE, t[1] / 24.0, ccw = true),
            midDay(jd, t[2] / 24.0),
            asrTime(jd, lat, p.asr.shadowFactor, t[3] / 24.0),
            sunAngleTime(jd, lat, RISE_SET_ANGLE, t[4] / 24.0, ccw = false),
            sunAngleTime(jd, lat, p.method.ishaAngle, t[5] / 24.0, ccw = false),
        )
    }

    private fun midDay(jd: Double, portion: Double): Double = fixHour(12.0 - sun(jd + portion).second)

    private fun sunAngleTime(jd: Double, lat: Double, angle: Double, portion: Double, ccw: Boolean): Double {
        val decl = sun(jd + portion).first
        val noon = midDay(jd, portion)
        val cosArg = ((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))).coerceIn(-1.0, 1.0)
        val tt = darccos(cosArg) / 15.0
        return noon + if (ccw) -tt else tt
    }

    private fun asrTime(jd: Double, lat: Double, factor: Double, portion: Double): Double {
        val decl = sun(jd + portion).first
        val angle = -darccot(factor + dtan(abs(lat - decl)))
        return sunAngleTime(jd, lat, angle, portion, ccw = false)
    }

    /** Returns (declination degrees, equation of time hours). */
    private fun sun(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    companion object {
        const val RISE_SET_ANGLE = 0.833

        fun julian(year: Int, month: Int, day: Int): Double {
            var y = year
            var m = month
            if (m <= 2) {
                y -= 1
                m += 12
            }
            val a = floor(y / 100.0)
            val b = 2 - a + floor(a / 4.0)
            return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
        }

        private fun rad(d: Double) = d * Math.PI / 180.0
        private fun deg(r: Double) = r * 180.0 / Math.PI
        private fun dsin(d: Double) = sin(rad(d))
        private fun dcos(d: Double) = cos(rad(d))
        private fun dtan(d: Double) = tan(rad(d))
        private fun darcsin(x: Double) = deg(asin(x))
        private fun darccos(x: Double) = deg(acos(x))
        private fun darctan2(y: Double, x: Double) = deg(atan2(y, x))
        private fun darccot(x: Double) = deg(atan(1.0 / x))
        private fun fix(a: Double, b: Double): Double {
            val r = a - b * floor(a / b)
            return if (r < 0) r + b else r
        }
        private fun fixAngle(a: Double) = fix(a, 360.0)
        private fun fixHour(a: Double) = fix(a, 24.0)
    }
}

object NextPrayer {
    data class Next(val prayer: Prayer, val time: ZonedDateTime)

    fun find(today: DayTimes, tomorrow: DayTimes, now: ZonedDateTime): Next {
        for (p in Prayer.entries) {
            val t = today.of(p)
            if (t.isAfter(now)) return Next(p, t)
        }
        return Next(Prayer.FAJR, tomorrow.fajr)
    }
}
