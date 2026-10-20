package com.salati.app.domain

import java.time.LocalDate

/**
 * Finds hand-made marks in a photo of a paper grid. The user drags a four-corner frame over
 * the prayer boxes only; the frame is split into rows (days) x 5 columns (prayers) and the
 * ink coverage in the middle of each box is measured. It detects "something written in this
 * box"; it does not read digits, so every detected box counts as one make-up.
 */
object GridScan {
    data class Pt(val x: Float, val y: Float)

    /** 8-bit luminance image, row-major. */
    class GrayImage(val width: Int, val height: Int, val lum: ByteArray) {
        init { require(lum.size == width * height) }
        fun at(x: Int, y: Int): Int = lum[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)].toInt() and 0xFF
    }

    private fun lerp(a: Pt, b: Pt, t: Float) = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    /** quad = [top-left, top-right, bottom-right, bottom-left]; u across, v down, both 0..1. */
    fun map(quad: List<Pt>, u: Float, v: Float): Pt = lerp(lerp(quad[0], quad[1], u), lerp(quad[3], quad[2], u), v)

    /** Fraction (0..1) of dark samples inside each box, [row][col]. */
    fun inkRatios(img: GrayImage, quad: List<Pt>, rows: Int, cols: Int, inset: Float = 0.2f, samples: Int = 9): Array<FloatArray> {
        require(quad.size == 4 && rows > 0 && cols > 0)
        val values = Array(rows) { r ->
            Array(cols) { c ->
                IntArray(samples * samples) { k ->
                    val su = inset + (1f - 2 * inset) * ((k % samples) + 0.5f) / samples
                    val sv = inset + (1f - 2 * inset) * ((k / samples) + 0.5f) / samples
                    val p = map(quad, (c + su) / cols, (r + sv) / rows)
                    img.at(p.x.toInt(), p.y.toInt())
                }
            }
        }
        // Paper brightness is estimated per band of 7 rows so shadows across the page are tolerated.
        val result = Array(rows) { FloatArray(cols) }
        for (band in 0 until rows step 7) {
            val end = minOf(rows, band + 7)
            val hist = IntArray(256)
            for (r in band until end) for (c in 0 until cols) for (v in values[r][c]) hist[v]++
            val paper = percentile(hist, 0.8)
            val dark = (paper * 0.62).toInt()
            for (r in band until end) for (c in 0 until cols) {
                val arr = values[r][c]
                result[r][c] = arr.count { it < dark }.toFloat() / arr.size
            }
        }
        return result
    }

    private fun percentile(hist: IntArray, q: Double): Int {
        val total = hist.sum()
        if (total == 0) return 255
        var acc = 0
        for (i in hist.indices) {
            acc += hist[i]
            if (acc >= total * q) return i
        }
        return 255
    }

    fun marks(ratios: Array<FloatArray>, threshold: Float): Set<Pair<Int, Int>> =
        buildSet { ratios.forEachIndexed { r, row -> row.forEachIndexed { c, v -> if (v >= threshold) add(r to c) } } }

    /** Row 0 = [firstDate]; columns left-to-right are Fajr..Isha, or Isha..Fajr when [rightToLeft]. */
    fun toCounts(marks: Set<Pair<Int, Int>>, cols: Int, firstDate: LocalDate, rightToLeft: Boolean, today: LocalDate): Map<LocalDate, PrayerCounts> {
        val out = mutableMapOf<LocalDate, MutableMap<Prayer, Int>>()
        for ((r, c) in marks) {
            val idx = if (rightToLeft) cols - 1 - c else c
            val p = Prayer.entries.getOrNull(idx) ?: continue
            val d = firstDate.plusDays(r.toLong())
            if (d.isAfter(today)) continue
            out.getOrPut(d) { mutableMapOf() }[p] = 1
        }
        return out.mapValues { (_, m) -> Prayer.entries.associateWith { m[it] ?: 0 } }
    }
}

/** Pure helpers for the pending (not yet saved) bulk entries of the Old fashion grid. */
object BulkGrid {
    fun set(m: Map<LocalDate, PrayerCounts>, d: LocalDate, p: Prayer, n: Int): Map<LocalDate, PrayerCounts> {
        val day = (m[d] ?: emptyCounts()) + (p to n.coerceIn(0, GridImport.MAX_PER_CELL))
        return if (day.total() == 0) m - d else m + (d to day)
    }

    fun add(m: Map<LocalDate, PrayerCounts>, d: LocalDate, p: Prayer, n: Int = 1) = set(m, d, p, (m[d]?.of(p) ?: 0) + n)

    /** Marks all five prayers of a day once; if the day is already fully marked it is cleared. */
    fun toggleDay(m: Map<LocalDate, PrayerCounts>, d: LocalDate): Map<LocalDate, PrayerCounts> {
        val day = m[d]
        return if (day != null && Prayer.entries.all { day.of(it) > 0 }) m - d
        else m + (d to Prayer.entries.associateWith { maxOf(1, day?.of(it) ?: 0) })
    }

    fun merge(a: Map<LocalDate, PrayerCounts>, b: Map<LocalDate, PrayerCounts>): Map<LocalDate, PrayerCounts> {
        var out = a
        for ((d, c) in b) for (p in Prayer.entries) if (c.of(p) > 0) out = add(out, d, p, c.of(p))
        return out
    }

    fun totals(m: Map<LocalDate, PrayerCounts>): PrayerCounts = Prayer.entries.associateWith { p -> m.values.sumOf { it.of(p) } }

    /** Prayers whose pending total exceeds the outstanding balance: prayer -> (pending, balance). */
    fun shortfall(m: Map<LocalDate, PrayerCounts>, balances: PrayerCounts): Map<Prayer, Pair<Int, Int>> {
        val t = totals(m)
        return Prayer.entries.filter { t.of(it) > balances.of(it) }.associateWith { t.of(it) to balances.of(it) }
    }
}

/**
 * Old fashion grid semantics: each row is one MISSED day starting at the grid start date.
 * Make-ups tick boxes oldest first, independent of the day they were actually prayed, so
 * recording 20 Fajr make-ups ticks the first 20 Fajr boxes rather than writing "20" today.
 */
object GridSlots {
    fun index(origin: LocalDate, d: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(origin, d)

    /** Pending boxes count from the box after the last ticked one. */
    fun tickThrough(k: Long, filled: Int): Int = (k + 1 - filled).coerceAtLeast(0L).toInt()

    /** Pending count left after unticking box [k] and every box after it. */
    fun untickFrom(k: Long, filled: Int, pending: Int): Int = (k - filled).coerceIn(0L, pending.toLong()).toInt()

    /** Index of the first box that is neither ticked nor pending, for the least-advanced prayer. */
    fun nextEmpty(filled: PrayerCounts, pending: PrayerCounts): Int = Prayer.entries.minOf { filled.of(it) + pending.of(it) }

    /** Dates for saving pending boxes: each box is dated by its missed day (never later than today). */
    fun pendingByDate(origin: LocalDate, filled: PrayerCounts, pending: PrayerCounts, today: LocalDate): Map<LocalDate, PrayerCounts> {
        val out = mutableMapOf<LocalDate, MutableMap<Prayer, Int>>()
        for (p in Prayer.entries) for (i in 0 until pending.of(p)) {
            var d = origin.plusDays((filled.of(p) + i).toLong())
            if (d.isAfter(today)) d = today
            val m = out.getOrPut(d) { mutableMapOf() }
            m[p] = (m[p] ?: 0) + 1
        }
        return out.mapValues { (_, m) -> Prayer.entries.associateWith { m[it] ?: 0 } }
    }
}

enum class BoxState { EMPTY, MARKED, FLOATING }

/**
 * What each Old fashion box shows. MARKED = a make-up tied to that exact box (tapped, bulk,
 * import or photo). FLOATING = make-ups recorded on the Qadaa page (no box), which fill the
 * oldest boxes that are not MARKED, starting at [origin].
 */
class GridView(val origin: LocalDate, entries: List<LedgerEntry>) {
    private val marked: Map<Prayer, Map<LocalDate, LedgerEntry>>
    val floating: PrayerCounts
    private val markedIdx: Map<Prayer, LongArray>

    init {
        val m = Prayer.entries.associateWith { mutableMapOf<LocalDate, LedgerEntry>() }
        val f = emptyCounts().toMutableMap()
        for (e in entries) {
            if (e.type != EntryType.MADE_UP || e.isUndone) continue
            val s = e.slotDate
            val boxes = m.getValue(e.prayer)
            if (s != null && e.delta == -1 && !boxes.containsKey(s)) boxes[s] = e
            else f[e.prayer] = f.getValue(e.prayer) - e.delta
        }
        marked = m
        floating = f
        markedIdx = m.mapValues { (_, boxes) ->
            boxes.keys.map { GridSlots.index(origin, it) }.filter { it >= 0 }.sorted().toLongArray()
        }
    }

    fun markedEntry(d: LocalDate, p: Prayer): LedgerEntry? = marked.getValue(p)[d]

    fun state(d: LocalDate, p: Prayer): BoxState {
        if (marked.getValue(p).containsKey(d)) return BoxState.MARKED
        val k = GridSlots.index(origin, d)
        if (k < 0) return BoxState.EMPTY
        val idx = markedIdx.getValue(p)
        val pos = java.util.Arrays.binarySearch(idx, k)
        val markedBefore = if (pos >= 0) pos else -(pos + 1)
        return if (k - markedBefore < floating.of(p)) BoxState.FLOATING else BoxState.EMPTY
    }

    /** Index of the box the next Qadaa-page make-up of [p] will tick. */
    fun nextEmpty(p: Prayer): Long {
        var k = floating.of(p).toLong()
        for (e in markedIdx.getValue(p)) if (e <= k) k++
        return k
    }

    fun nextEmptyAll(): Long = Prayer.entries.minOf { nextEmpty(it) }

    fun ticked(p: Prayer): Int = marked.getValue(p).size + floating.of(p)
}
