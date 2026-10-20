package com.salati.app

import com.salati.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GridImportTest {
    private val today = LocalDate.of(2026, 10, 18)

    @Test fun csvWithEnglishHeaderAndTicks() {
        val csv = "Date,Fajr,Dhuhr,Asr,Maghrib,Isha\n2026-10-01,✓,x,,1,2\n02/10/2026,,,,,\n2026-12-01,1,1,1,1,1\n"
        val r = GridImport.parse(csv.toByteArray(), today) as GridImportResult.Ok
        assertEquals(1, r.counts.size)
        val day = r.counts.getValue(LocalDate.of(2026, 10, 1))
        assertEquals(listOf(1, 1, 0, 1, 2), Prayer.entries.map { day.of(it) })
        assertEquals(ImportIssue.FUTURE_DATE, r.issues.single().issue)
    }

    @Test fun arabicHeadersSemicolonsAndArabicDigits() {
        val csv = "\uFEFFالتاريخ;الفجر;الظهر;العصر;المغرب;العشاء\n٠٥/١٠/٢٠٢٦;١;١;١;١;١\n"
        val r = GridImport.parse(csv.toByteArray(), today) as GridImportResult.Ok
        assertEquals(5, r.total)
        assertTrue(r.counts.containsKey(LocalDate.of(2026, 10, 5)))
    }

    @Test fun prefersDateColumnOverWeekdayColumn() {
        val t = listOf(listOf("Day", "Date", "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"), listOf("Sat", "2026-10-03", "1", "", "", "", ""))
        val r = GridImport.fromTable(t, today) as GridImportResult.Ok
        assertEquals(1, r.counts.getValue(LocalDate.of(2026, 10, 3)).of(Prayer.FAJR))
    }

    @Test fun badCellsAreReported() {
        val t = listOf(listOf("Date", "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"), listOf("total", "3", "", "", "", ""), listOf("2026-10-03", "maybe", "1", "", "", ""))
        val r = GridImport.fromTable(t, today) as GridImportResult.Ok
        assertEquals(setOf(ImportIssue.BAD_DATE, ImportIssue.BAD_VALUE), r.issues.map { it.issue }.toSet())
        assertEquals(1, r.total)
    }

    @Test fun excelSerialDates() {
        assertEquals(LocalDate.of(2025, 3, 1), GridImport.parseDate("45717"))
        assertEquals(LocalDate.of(2025, 3, 1), GridImport.parseDate("2025-03-01 00:00:00"))
        assertEquals(LocalDate.of(2025, 3, 1), GridImport.parseDate("1.3.25"))
    }

    @Test fun oldXlsRejected() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0)
        assertEquals(GridImportResult.Failed(ImportFailure.UNSUPPORTED_XLS), GridImport.parse(ole, today))
    }

    @Test fun readsMinimalXlsx() {
        val shared = """<sst xmlns="x"><si><t>Date</t></si><si><t>Fajr</t></si><si><t>Dhuhr</t></si><si><t>Asr</t></si><si><t>Maghrib</t></si><si><t>Isha</t></si><si><t>✓</t></si></sst>"""
        val sheet = """<worksheet><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c><c r="F1" t="s"><v>5</v></c></row>
            <row r="2"><c r="A2" s="1"><v>45717</v></c><c r="B2" t="s"><v>6</v></c><c r="F2"><v>3</v></c></row>
            </sheetData></worksheet>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("xl/sharedStrings.xml")); z.write(shared.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); z.write(sheet.toByteArray()); z.closeEntry()
        }
        val r = GridImport.parse(out.toByteArray(), today) as GridImportResult.Ok
        val day = r.counts.getValue(LocalDate.of(2025, 3, 1))
        assertEquals(1, day.of(Prayer.FAJR))
        assertEquals(3, day.of(Prayer.ISHA))
    }

    @Test fun bulkPlanIsAllOrNothing() {
        val bal = Prayer.entries.associateWith { 2 }
        val ok = QadaaPlanner.bulkMakeUp(bal, mapOf(today to Prayer.entries.associateWith { 2 }), "b")
        assertEquals(5, (ok as LedgerPlan.Apply).entries.size)
        val tooMany = QadaaPlanner.bulkMakeUp(bal, mapOf(today to Prayer.entries.associateWith { 1 }, today.minusDays(1) to Prayer.entries.associateWith { 2 }), "b")
        assertEquals(LedgerPlan.Rejected(RejectReason.WOULD_BE_NEGATIVE), tooMany)
    }

    @Test fun twentyFajrMakeUpsTickTwentyBoxesFromGridStart() {
        // Bug report: recording 20 Fajr wrote "20" in today's box; it must tick the 20 oldest Fajr boxes.
        val origin = LocalDate.of(2020, 1, 1)
        val filled = emptyCounts() + (Prayer.FAJR to 20)
        val ticked = (0L until 30L).filter { k -> k < filled.of(Prayer.FAJR) }
        assertEquals((0L until 20L).toList(), ticked)
        assertEquals(0, GridSlots.nextEmpty(filled, emptyCounts()))   // other prayers still at box 0
        assertEquals(LocalDate.of(2020, 1, 20), origin.plusDays(19))
    }

    @Test fun slotTapAndPendingDates() {
        assertEquals(5, GridSlots.tickThrough(k = 24, filled = 20))
        assertEquals(0, GridSlots.tickThrough(k = 10, filled = 20))
        assertEquals(2, GridSlots.untickFrom(k = 22, filled = 20, pending = 5))
        val origin = LocalDate.of(2020, 1, 1)
        val byDate = GridSlots.pendingByDate(origin, emptyCounts() + (Prayer.ASR to 3), emptyCounts() + (Prayer.ASR to 2), today)
        assertEquals(setOf(LocalDate.of(2020, 1, 4), LocalDate.of(2020, 1, 5)), byDate.keys)
        val late = GridSlots.pendingByDate(today, emptyCounts(), emptyCounts() + (Prayer.ISHA to 3), today)
        assertEquals(3, late.getValue(today).of(Prayer.ISHA))   // boxes after today are dated today
    }

    @Test fun bulkHelpers() {
        var m = BulkGrid.add(emptyMap(), today, Prayer.ASR)
        m = BulkGrid.add(m, today, Prayer.ASR)
        assertEquals(2, m.getValue(today).of(Prayer.ASR))
        m = BulkGrid.toggleDay(m, today)
        assertTrue(Prayer.entries.all { m.getValue(today).of(it) >= 1 })
        m = BulkGrid.toggleDay(m, today)
        assertTrue(m.isEmpty())
    }

    @Test fun photoScanFindsInkedBoxes() {
        val w = 500; val h = 1000; val rows = 10; val cols = 5
        val lum = ByteArray(w * h) { 220.toByte() }
        val inked = setOf(0 to 0, 3 to 4, 9 to 2)
        for ((r, c) in inked) {
            val x0 = c * w / cols + 30; val y0 = r * h / rows + 30
            for (y in y0 until y0 + 40) for (x in x0 until x0 + 40) lum[y * w + x] = 30
        }
        val img = GridScan.GrayImage(w, h, lum)
        val quad = listOf(GridScan.Pt(0f, 0f), GridScan.Pt(w.toFloat(), 0f), GridScan.Pt(w.toFloat(), h.toFloat()), GridScan.Pt(0f, h.toFloat()))
        val marks = GridScan.marks(GridScan.inkRatios(img, quad, rows, cols), 0.12f)
        assertEquals(inked, marks)
        val counts = GridScan.toCounts(marks, cols, today.minusDays(9), rightToLeft = true, today = today)
        assertEquals(1, counts.getValue(today.minusDays(9)).of(Prayer.ISHA))
        assertEquals(1, counts.getValue(today.minusDays(6)).of(Prayer.FAJR))
    }
}
