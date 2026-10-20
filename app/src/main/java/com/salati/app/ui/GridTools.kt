@file:OptIn(ExperimentalMaterial3Api::class)

package com.salati.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.salati.app.R
import com.salati.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.hypot
import kotlin.math.max

// ---------------------------------------------------------------- file helpers

suspend fun readUriBytes(ctx: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > GridImport.MAX_BYTES) error("file too large")
            }
            out.toByteArray()
        }
    }.getOrNull()
}

suspend fun writeUriBytes(ctx: Context, uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
    runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)
}

/** Decodes a picked photo as a software bitmap no larger than [maxDim] (EXIF rotation applied on API 28+). */
suspend fun loadScaledBitmap(ctx: Context, uri: Uri, maxDim: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                val big = max(info.size.width, info.size.height)
                if (big > maxDim) {
                    val f = maxDim.toFloat() / big
                    dec.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
                }
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }.getOrNull()
}

private fun Bitmap.rotated90(): Bitmap = Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(90f) }, true)

private fun Bitmap.toGray(): GridScan.GrayImage {
    val px = IntArray(width * height)
    getPixels(px, 0, width, 0, 0, width, height)
    val lum = ByteArray(px.size) { i ->
        val c = px[i]
        (((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000).toByte()
    }
    return GridScan.GrayImage(width, height, lum)
}

// ---------------------------------------------------------------- import summary

@Composable
fun ImportResultDialog(result: GridImportResult, onReview: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (result) {
                    is GridImportResult.Failed -> Text(stringResource(importFailureRes(result.reason)))
                    is GridImportResult.Ok -> {
                        if (result.total > 0) {
                            Text(
                                stringResource(
                                    R.string.import_found, result.total, result.counts.size,
                                    formatDate(result.firstDate!!), formatDate(result.lastDate!!),
                                ),
                                fontWeight = FontWeight.SemiBold,
                            )
                            val t = BulkGrid.totals(result.counts)
                            Text(Prayer.entries.joinToString(" · ") { p -> stringResource(prayerShortRes(p)) + " " + formatNumber(t.of(p)) })
                        } else Text(stringResource(R.string.import_nothing))
                        if (result.issues.isNotEmpty()) {
                            Text(stringResource(R.string.import_skipped, result.issues.size), color = MaterialTheme.colorScheme.error)
                            for (i in result.issues.take(6)) {
                                Text(stringResource(R.string.import_issue_line, i.row, stringResource(importIssueRes(i.issue)), i.text), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        InfoText(stringResource(R.string.import_review_note))
                    }
                }
            }
        },
        confirmButton = {
            if (result is GridImportResult.Ok && result.total > 0) TextButton(onClick = onReview) { Text(stringResource(R.string.import_review)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun importFailureRes(f: ImportFailure) = when (f) {
    ImportFailure.EMPTY -> R.string.import_fail_empty
    ImportFailure.UNREADABLE -> R.string.import_fail_unreadable
    ImportFailure.UNSUPPORTED_XLS -> R.string.import_fail_xls
    ImportFailure.NO_HEADER -> R.string.import_fail_header
    ImportFailure.NOTHING_FOUND -> R.string.import_nothing
}

private fun importIssueRes(i: ImportIssue) = when (i) {
    ImportIssue.BAD_DATE -> R.string.import_issue_date
    ImportIssue.BAD_VALUE -> R.string.import_issue_value
    ImportIssue.FUTURE_DATE -> R.string.import_issue_future
}

// ---------------------------------------------------------------- photo scan

/**
 * Full-screen photo scanner: drag the 4 corners onto the prayer boxes, set the date of the
 * first row and the number of rows, tune sensitivity, then send the detected marks for review.
 */
@Composable
fun GridPhotoScanDialog(
    source: Bitmap,
    defaultFirstDate: LocalDate,
    rightToLeftDefault: Boolean,
    onDismiss: () -> Unit,
    onUse: (Map<LocalDate, PrayerCounts>) -> Unit,
) {
    var bmp by remember { mutableStateOf(source) }
    val image = remember(bmp) { bmp.asImageBitmap() }
    val gray by produceState<GridScan.GrayImage?>(null, bmp) { value = withContext(Dispatchers.Default) { bmp.toGray() } }
    val quad = remember(bmp) {
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        mutableStateListOf(Offset(w * .15f, h * .1f), Offset(w * .85f, h * .1f), Offset(w * .85f, h * .9f), Offset(w * .15f, h * .9f))
    }
    var rows by remember { mutableIntStateOf(Periods.SHEET_DAYS / 3) }
    var firstDate by remember { mutableStateOf(defaultFirstDate) }
    var rtl by remember { mutableStateOf(rightToLeftDefault) }
    var sensitivity by remember { mutableFloatStateOf(0.12f) }
    var pickDate by remember { mutableStateOf(false) }
    val quadKey = quad.toList()
    val ratios by produceState<Array<FloatArray>?>(null, gray, quadKey, rows) {
        val g = gray ?: return@produceState
        value = withContext(Dispatchers.Default) {
            GridScan.inkRatios(g, quadKey.map { GridScan.Pt(it.x, it.y) }, rows, Prayer.entries.size)
        }
    }
    val marks = remember(ratios, sensitivity) { ratios?.let { GridScan.marks(it, sensitivity) } ?: emptySet() }
    val today = LocalDate.now()
    val counts = remember(marks, firstDate, rtl) { GridScan.toCounts(marks, Prayer.entries.size, firstDate, rtl, today) }
    val cs = MaterialTheme.colorScheme
    val handleR = with(LocalDensity.current) { 14.dp.toPx() }
    val touchR = with(LocalDensity.current) { 40.dp.toPx() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                InfoText(stringResource(R.string.scan_hint))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val cw = constraints.maxWidth.toFloat()
                    val ch = constraints.maxHeight.toFloat()
                    val scale = minOf(cw / bmp.width, ch / bmp.height)
                    val ox = (cw - bmp.width * scale) / 2f
                    val oy = (ch - bmp.height * scale) / 2f
                    fun toScreen(p: Offset) = Offset(ox + p.x * scale, oy + p.y * scale)
                    fun gridPt(u: Float, v: Float): Offset {
                        val p = GridScan.map(quad.map { GridScan.Pt(it.x, it.y) }, u, v)
                        return toScreen(Offset(p.x, p.y))
                    }
                    var active by remember { mutableIntStateOf(-1) }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(bmp, scale) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val d = quad.map { val s = toScreen(it); hypot(s.x - start.x, s.y - start.y) }
                                    val i = d.indices.minByOrNull { d[it] } ?: -1
                                    active = if (i >= 0 && d[i] <= touchR) i else -1
                                },
                                onDragEnd = { active = -1 },
                                onDragCancel = { active = -1 },
                                onDrag = { change, amount ->
                                    if (active >= 0) {
                                        change.consume()
                                        val n = quad[active] + amount / scale
                                        quad[active] = Offset(n.x.coerceIn(0f, bmp.width.toFloat()), n.y.coerceIn(0f, bmp.height.toFloat()))
                                    }
                                },
                            )
                        }
                    ) {
                        drawImage(
                            image,
                            dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                            dstSize = IntSize((bmp.width * scale).toInt(), (bmp.height * scale).toInt()),
                        )
                        val cols = Prayer.entries.size
                        for ((r, c) in marks) {
                            val path = Path().apply {
                                val a = gridPt(c.toFloat() / cols, r.toFloat() / rows); moveTo(a.x, a.y)
                                val b = gridPt((c + 1f) / cols, r.toFloat() / rows); lineTo(b.x, b.y)
                                val d2 = gridPt((c + 1f) / cols, (r + 1f) / rows); lineTo(d2.x, d2.y)
                                val e = gridPt(c.toFloat() / cols, (r + 1f) / rows); lineTo(e.x, e.y)
                                close()
                            }
                            drawPath(path, Color(0x8834A853))
                        }
                        for (r in 0..rows) {
                            val strong = r % 7 == 0 || r == rows
                            drawLine(if (strong) cs.primary else cs.primary.copy(alpha = 0.35f), gridPt(0f, r.toFloat() / rows), gridPt(1f, r.toFloat() / rows), strokeWidth = if (strong) 3f else 1f)
                        }
                        for (c in 0..cols) drawLine(cs.primary, gridPt(c.toFloat() / cols, 0f), gridPt(c.toFloat() / cols, 1f), strokeWidth = 2f)
                        quad.forEachIndexed { i, p ->
                            val s = toScreen(p)
                            drawCircle(if (i == active) cs.tertiary else cs.primary, handleR, s)
                            drawCircle(Color.White, handleR, s, style = Stroke(width = 3f))
                        }
                    }
                }
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.scan_first_row, formatDate(firstDate)), Modifier.weight(1f))
                        TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.change)) }
                    }
                    Stepper(rows, { rows = it }, 1, 62, stringResource(R.string.scan_rows))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.scan_rtl), Modifier.weight(1f))
                        Switch(checked = rtl, onCheckedChange = { rtl = it })
                    }
                    Text(stringResource(R.string.scan_sensitivity))
                    Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 0.03f..0.4f)
                    Text(stringResource(R.string.scan_detected, marks.size, counts.values.sumOf { it.total() }), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { bmp = bmp.rotated90() }) { Text(stringResource(R.string.scan_rotate)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = { onUse(counts) }, enabled = counts.isNotEmpty()) { Text(stringResource(R.string.import_review)) }
                    }
                }
            }
        }
    }
    if (pickDate) PastDatePickerDialog(firstDate, { pickDate = false }) { firstDate = it }
}
