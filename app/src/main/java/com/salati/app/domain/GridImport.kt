package com.salati.app.domain

import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

/** Problems found in individual rows of an imported sheet (row numbers are 1-based, as in Excel). */
enum class ImportIssue { BAD_DATE, BAD_VALUE, FUTURE_DATE }

data class ImportRowIssue(val row: Int, val issue: ImportIssue, val text: String)

enum class ImportFailure { EMPTY, UNREADABLE, UNSUPPORTED_XLS, NO_HEADER, NOTHING_FOUND }

sealed interface GridImportResult {
    data class Ok(
        val counts: Map<LocalDate, PrayerCounts>,
        val issues: List<ImportRowIssue>,
    ) : GridImportResult {
        val total: Int get() = counts.values.sumOf { it.total() }
        val firstDate: LocalDate? get() = counts.keys.minByOrNull { it.toEpochDay() }
        val lastDate: LocalDate? get() = counts.keys.maxByOrNull { it.toEpochDay() }
    }
    data class Failed(val reason: ImportFailure) : GridImportResult
}

/**
 * Reads past make-ups from an Excel (.xlsx) or CSV file laid out like the Old fashion grid:
 * one row per day, a Date column and one column per prayer (English or Arabic headers).
 * A cell counts as made up when it holds a tick (✓ ✔ x v yes نعم 1) or a number (that many make-ups).
 */
object GridImport {
    const val MAX_PER_CELL = 50
    const val MAX_BYTES = 10 * 1024 * 1024

    private val aliases: Map<Prayer, List<String>> = mapOf(
        Prayer.FAJR to listOf("fajr", "fajir", "fajar", "subh", "sobh", "فجر", "صبح"),
        Prayer.DHUHR to listOf("dhuhr", "zuhr", "duhr", "thuhr", "dhuhur", "zuhur", "dhohr", "zohr", "ظهر"),
        Prayer.ASR to listOf("asr", "عصر"),
        Prayer.MAGHRIB to listOf("maghrib", "magrib", "maghreb", "مغرب"),
        Prayer.ISHA to listOf("isha", "ishaa", "esha", "eshaa", "عشاء", "عشا"),
    )
    private val dateAliases = listOf("date", "d/p", "ي/ص", "day", "تاريخ", "يوم")

    fun parse(bytes: ByteArray, today: LocalDate): GridImportResult {
        if (bytes.isEmpty()) return GridImportResult.Failed(ImportFailure.EMPTY)
        val table = when {
            isZip(bytes) -> Xlsx.firstSheet(bytes) ?: return GridImportResult.Failed(ImportFailure.UNREADABLE)
            isOle(bytes) -> return GridImportResult.Failed(ImportFailure.UNSUPPORTED_XLS)
            else -> Csv.parse(decodeText(bytes))
        }
        return fromTable(table, today)
    }

    /** One date column plus its prayer columns. A sheet may hold several side by side. */
    private data class Block(val dateCol: Int, val cols: Map<Prayer, Int>)

    /** Paper-style letter headers (as in the app's Old fashion grid): F D A M I / ف ظ ع م عش. */
    private val letterHeaders = listOf(listOf("f", "d", "a", "m", "i"), listOf("ف", "ظ", "ع", "م", "عش"))

    private fun matches(cell: String, p: Prayer) = cell.isNotEmpty() && aliases.getValue(p).any { cell == it || cell.contains(it) }

    /** Finds the blocks described by a header row, or an empty list if it is not a header row. */
    private fun detectBlocks(raw: List<String>): List<Block> {
        val row = raw.map { normalize(it) }
        val blocks = mutableListOf<Block>()
        // 1) Five consecutive letter headers; the date / day column is the one just before them.
        for (seq in letterHeaders) {
            var i = 0
            while (i + 4 < row.size) {
                if ((0..4).all { row[i + it] == seq[it] }) {
                    if (i >= 1) blocks += Block(i - 1, Prayer.entries.withIndex().associate { (j, p) -> p to i + j })
                    i += 5
                } else i++
            }
        }
        if (blocks.isNotEmpty()) return blocks
        // 2) Prayer names; every Fajr column starts a new block.
        val fajrCols = row.indices.filter { matches(row[it], Prayer.FAJR) }
        for ((n, f) in fajrCols.withIndex()) {
            val end = fajrCols.getOrNull(n + 1) ?: row.size
            val cols = mutableMapOf(Prayer.FAJR to f)
            for (p in Prayer.entries.drop(1)) {
                val c = (f + 1 until end).firstOrNull { it !in cols.values && matches(row[it], p) }
                if (c != null) cols[p] = c
            }
            if (cols.size < 3) continue
            val lo = (blocks.lastOrNull()?.cols?.values?.maxOrNull() ?: -1) + 1
            // Prefer a "Date" column over a weekday "Day" column, searching leftwards from Fajr.
            val dateLeft = dateAliases.asSequence()
                .mapNotNull { a -> (f - 1 downTo lo).firstOrNull { c -> row[c].isNotEmpty() && (row[c] == a || row[c].contains(a)) } }
                .firstOrNull()
            val dateCol = when {
                dateLeft != null -> dateLeft
                n == 0 -> dateAliases.asSequence()
                    .map { a -> row.indexOfFirst { c -> c.isNotEmpty() && (c == a || c.contains(a)) } }
                    .firstOrNull { it >= 0 && it !in cols.values }
                    ?: (0..row.size).first { it !in cols.values }
                else -> f - 1
            }
            blocks += Block(dateCol, cols)
        }
        return blocks
    }

    /**
     * Accepts a simple list (one row per day) or the Old fashion layout: pages of several
     * blocks side by side, each "D/P | F | D | A | M | I", with the header repeated per page.
     */
    fun fromTable(table: List<List<String>>, today: LocalDate): GridImportResult {
        if (table.all { r -> r.all { it.isBlank() } }) return GridImportResult.Failed(ImportFailure.EMPTY)
        var blocks: List<Block> = emptyList()
        var dataStart = 0
        val firstHeader = table.indices.firstOrNull { detectBlocks(table[it]).isNotEmpty() }
        if (firstHeader == null) {
            // No header: accept "Date, Fajr, Dhuhr, Asr, Maghrib, Isha" when the first column holds dates.
            val firstData = table.indexOfFirst { r -> r.any { it.isNotBlank() } }
            if (firstData < 0 || parseDate(table[firstData].getOrElse(0) { "" }) == null) {
                return GridImportResult.Failed(ImportFailure.NO_HEADER)
            }
            blocks = listOf(Block(0, Prayer.entries.withIndex().associate { (i, p) -> p to i + 1 }))
            dataStart = firstData
        } else dataStart = firstHeader
        val counts = mutableMapOf<LocalDate, MutableMap<Prayer, Int>>()
        val issues = mutableListOf<ImportRowIssue>()
        for (i in dataStart until table.size) {
            val row = table[i]
            if (row.all { it.isBlank() }) continue
            val header = if (firstHeader != null) detectBlocks(row) else emptyList()
            if (header.isNotEmpty()) { blocks = header; continue }
            for (block in blocks) {
                val rawDate = row.getOrElse(block.dateCol) { "" }
                val values = block.cols.mapValues { (_, c) -> row.getOrElse(c) { "" } }
                // Nothing written in this block (titles, empty days, sub-totals without values): skip quietly.
                if (values.values.all { it.isBlank() }) continue
                val date = parseDate(rawDate)
                if (date == null) {
                    issues += ImportRowIssue(i + 1, ImportIssue.BAD_DATE, rawDate)
                    continue
                }
                if (date.isAfter(today)) {
                    if (values.values.any { (parseCell(it) ?: 0) > 0 }) issues += ImportRowIssue(i + 1, ImportIssue.FUTURE_DATE, date.toString())
                    continue
                }
                for ((p, raw) in values) {
                    val n = parseCell(raw)
                    if (n == null) {
                        issues += ImportRowIssue(i + 1, ImportIssue.BAD_VALUE, raw)
                        continue
                    }
                    if (n > 0) {
                        val day = counts.getOrPut(date) { mutableMapOf() }
                        day[p] = ((day[p] ?: 0) + n).coerceAtMost(MAX_PER_CELL)
                    }
                }
            }
        }
        if (counts.isEmpty() && issues.isEmpty()) return GridImportResult.Failed(ImportFailure.NOTHING_FOUND)
        val result = counts.mapValues { (_, m) -> Prayer.entries.associateWith { m[it] ?: 0 } }
        return GridImportResult.Ok(result, issues)
    }

    /** Returns the number of make-ups in a cell, 0 for empty / not done, or null if unreadable. */
    fun parseCell(raw: String): Int? {
        val t = latinDigits(raw).trim().lowercase()
        if (t.isEmpty()) return 0
        if (t in setOf("0", "-", "–", "—", "no", "n", "لا", "✗", "✘", "×", "false", "o", ".")) return 0
        if (t in setOf("✓", "✔", "☑", "✅", "v", "y", "yes", "x", "نعم", "true", "+", "*", "•", "√")) return 1
        val num = t.toDoubleOrNull() ?: return null
        if (num < 0 || num != Math.floor(num)) return null
        return num.toInt().coerceAtMost(MAX_PER_CELL)
    }

    private val iso = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")
    private val dmy = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2}|\d{4})$""")

    /** ISO (2025-03-01), day-first (01/03/2025, 1-3-25, 1.3.2025) or an Excel serial date number. */
    fun parseDate(raw: String): LocalDate? {
        val t = latinDigits(raw).trim()
        if (t.isEmpty()) return null
        t.toDoubleOrNull()?.let { serial ->
            if (serial < 20000 || serial > 80000) return null
            return LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
        }
        iso.find(t)?.let { m ->
            val (y, mo, d) = m.destructured
            return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
        }
        dmy.find(t.substringBefore(' '))?.let { m ->
            val (d, mo, y) = m.destructured
            val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
            return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
        }
        return null
    }

    fun latinDigits(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                '٫' -> '.'
                else -> c
            }
        )
    }

    private fun normalize(s: String): String {
        var t = s.trim().lowercase().replace("ـ", "")
        t = t.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه')
        if (t.startsWith("ال")) t = t.removePrefix("ال")
        return t
    }

    private fun isZip(b: ByteArray) = b.size > 3 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte()
    private fun isOle(b: ByteArray) = b.size > 7 && b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte() && b[2] == 0x11.toByte() && b[3] == 0xE0.toByte()

    fun decodeText(b: ByteArray): String = when {
        b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16LE)
        b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16BE)
        b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte() -> String(b, 3, b.size - 3, Charsets.UTF_8)
        else -> String(b, Charsets.UTF_8)
    }

    /** CSV template for one Old fashion sheet (UTF-8 with BOM so Excel shows Arabic correctly). */
    fun template(dates: List<LocalDate>): ByteArray {
        val sb = StringBuilder("\uFEFFDate,Fajr,Dhuhr,Asr,Maghrib,Isha\r\n")
        for (d in dates) sb.append(d.toString()).append(",,,,,\r\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}

object Csv {
    fun parse(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delim = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                delim -> { row.add(cell.toString()); cell.clear() }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row); row = mutableListOf() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }
}

/** Minimal .xlsx reader: values of the first worksheet as text (dates arrive as Excel serial numbers). */
object Xlsx {
    private const val MAX_ENTRY = 20 * 1024 * 1024

    fun firstSheet(bytes: ByteArray): List<List<String>>? = runCatching {
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.trimStart('/')
                if (!e.isDirectory && name.startsWith("xl/") && name.endsWith(".xml") || name.endsWith(".rels")) {
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_ENTRY) error("entry too large")
                    }
                    files[name] = out.toString("UTF-8")
                }
            }
        }
        val sheetPath = firstSheetPath(files) ?: return null
        val sheet = files[sheetPath] ?: return null
        readSheet(sheet, sharedStrings(files["xl/sharedStrings.xml"]))
    }.getOrNull()

    private fun firstSheetPath(files: Map<String, String>): String? {
        val wb = files["xl/workbook.xml"]
        val rels = files["xl/_rels/workbook.xml.rels"]
        if (wb != null && rels != null) {
            val rid = Regex("""<(?:\w+:)?sheet\b[^>]*?\br:id="([^"]+)"""").find(wb)?.groupValues?.get(1)
            if (rid != null) {
                val rel = Regex("""<Relationship\b[^>]*>""").findAll(rels).map { it.value }
                    .firstOrNull { Regex("""\bId="${Regex.escape(rid)}"""").containsMatchIn(it) }
                val target = rel?.let { Regex("""\bTarget="([^"]+)"""").find(it)?.groupValues?.get(1) }
                if (target != null) {
                    val p = if (target.startsWith("/")) target.trimStart('/') else "xl/" + target.removePrefix("./")
                    if (files.containsKey(p)) return p
                }
            }
        }
        return files.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.sorted().firstOrNull()
    }

    private val tRegex = Regex("""<(?:\w+:)?t\b[^>]*>(.*?)</(?:\w+:)?t>""", RegexOption.DOT_MATCHES_ALL)

    fun sharedStrings(xml: String?): List<String> {
        if (xml == null) return emptyList()
        return Regex("""<(?:\w+:)?si\b[^>]*>(.*?)</(?:\w+:)?si>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
            val body = si.groupValues[1].replace(Regex("""<(?:\w+:)?rPh\b.*?</(?:\w+:)?rPh>""", RegexOption.DOT_MATCHES_ALL), "")
            tRegex.findAll(body).joinToString("") { unescape(it.groupValues[1]) }
        }.toList()
    }

    fun readSheet(xml: String, shared: List<String>): List<List<String>> {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        var nextRow = 1
        val rowRegex = Regex("""<(?:\w+:)?row\b([^>]*?)(?:/>|>(.*?)</(?:\w+:)?row>)""", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("""<(?:\w+:)?c\b([^>]*?)(?:/>|>(.*?)</(?:\w+:)?c>)""", RegexOption.DOT_MATCHES_ALL)
        val vRegex = Regex("""<(?:\w+:)?v\b[^>]*>(.*?)</(?:\w+:)?v>""", RegexOption.DOT_MATCHES_ALL)
        for (rm in rowRegex.findAll(xml)) {
            val rNum = attr(rm.groupValues[1], "r")?.toIntOrNull() ?: nextRow
            nextRow = rNum + 1
            val cells = rows.getOrPut(rNum) { mutableMapOf() }
            var nextCol = 0
            for (cm in cellRegex.findAll(rm.groupValues[2])) {
                val attrs = cm.groupValues[1]
                val col = attr(attrs, "r")?.let { colIndex(it) } ?: nextCol
                nextCol = col + 1
                val body = cm.groupValues[2]
                val v = vRegex.find(body)?.groupValues?.get(1)?.let { unescape(it) }
                val text = when (attr(attrs, "t")) {
                    "s" -> v?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> tRegex.findAll(body).joinToString("") { unescape(it.groupValues[1]) }
                    "b" -> if (v == "1") "1" else "0"
                    else -> v ?: ""
                }
                cells[col] = text
            }
        }
        if (rows.isEmpty()) return emptyList()
        val last = rows.lastKey()
        return (1..last).map { r ->
            val m = rows[r] ?: return@map emptyList()
            val width = (m.keys.maxOrNull() ?: -1) + 1
            List(width) { m[it] ?: "" }
        }
    }

    private fun attr(attrs: String, name: String): String? =
        Regex("""(?:^|\s)$name="([^"]*)"""").find(attrs)?.groupValues?.get(1)

    fun colIndex(ref: String): Int {
        var n = 0
        for (c in ref) {
            if (c !in 'A'..'Z' && c !in 'a'..'z') break
            n = n * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }

    fun unescape(s: String): String {
        if (!s.contains('&')) return s
        return Regex("""&(#x[0-9a-fA-F]+|#\d+|lt|gt|amp|quot|apos);""").replace(s) { m ->
            val k = m.groupValues[1]
            when {
                k == "lt" -> "<"; k == "gt" -> ">"; k == "amp" -> "&"; k == "quot" -> "\""; k == "apos" -> "'"
                k.startsWith("#x") -> String(Character.toChars(k.substring(2).toInt(16)))
                else -> String(Character.toChars(k.substring(1).toInt()))
            }
        }
    }
}
