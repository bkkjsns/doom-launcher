@file:OptIn(ExperimentalMaterial3Api::class)

package com.msa.freedoom.ui.controlseditor

import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.msa.freedoom.R
import com.msa.freedoom.ui.fx.HomeAccent
import com.msa.freedoom.ui.fx.SlimeOverlay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

// The engine's touch grid is 26 x 16 (ScaleX / ScaleY in TouchControlsConfig.h). Positions in the
// layout file are stored NORMALISED, i.e. divided by those numbers, so a file holds 0..1 values.
// The editor works in grid units (more natural to look at) and converts at the file boundary.
private const val GRID_W = 26f
private const val GRID_H = 16f
private const val MIN_SIZE = 0.6f
private const val SNAP_STEP = 0.5f

private data class TouchControl(
    val name: String,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val hidden: Boolean,
)

private class Parsed(val controls: List<TouchControl>, val repaired: Int)

// One element per control, all at the top level (not valid single-root XML), so this reads and
// rewrites the text directly and only touches the attributes it understands.
private val ELEMENT = Regex("<([A-Za-z_][\\w.\\-]*)\\s+([^<>]*?)/?>")
private val ATTR = Regex("([A-Za-z_][\\w.\\-]*)=\"([^\"]*)\"")

/**
 * Reads the file into grid units. The engine always writes 0..1. A file saved by editor 1.6.0
 * holds grid units instead (values above 1), which made every button vanish in game; those are
 * detected per control and treated as grid units so the layout can be saved back correctly.
 */
private fun parseControls(text: String): Parsed {
    var repaired = 0
    val list = ELEMENT.findAll(text).mapNotNull { m ->
        val attrs = ATTR.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
        val l = attrs["left"]?.toFloatOrNull()
        val r = attrs["right"]?.toFloatOrNull()
        val t = attrs["top"]?.toFloatOrNull()
        val b = attrs["bottom"]?.toFloatOrNull()
        if (l == null || r == null || t == null || b == null) {
            null
        } else {
            val hidden = attrs["hidden"].let { it == "1" || it == "true" }
            val legacy = r > 1.5f || b > 1.5f
            if (legacy) {
                repaired++
                TouchControl(m.groupValues[1], l, r, t, b, hidden)
            } else {
                TouchControl(m.groupValues[1], l * GRID_W, r * GRID_W, t * GRID_H, b * GRID_H, hidden)
            }
        }
    }.toList()
    return Parsed(list, repaired)
}

private fun setAttr(element: String, name: String, value: String): String =
    Regex("(\\b$name=\")[^\"]*(\")").replace(element) { it.groupValues[1] + value + it.groupValues[2] }

private fun fmt(v: Float): String = String.format(Locale.US, "%.5f", v)

/** Writes grid-unit controls back as normalised 0..1, clamped to a sane size and the screen. */
private fun applyControls(text: String, controls: List<TouchControl>): String {
    val byName = controls.associateBy { it.name }
    return ELEMENT.replace(text) { m ->
        val c = byName[m.groupValues[1]] ?: return@replace m.value
        val l = c.left.coerceIn(0f, GRID_W - MIN_SIZE)
        val t = c.top.coerceIn(0f, GRID_H - MIN_SIZE)
        val r = c.right.coerceIn(l + MIN_SIZE, GRID_W)
        val b = c.bottom.coerceIn(t + MIN_SIZE, GRID_H)
        var s = m.value
        s = setAttr(s, "left", fmt(l / GRID_W))
        s = setAttr(s, "right", fmt(r / GRID_W))
        s = setAttr(s, "top", fmt(t / GRID_H))
        s = setAttr(s, "bottom", fmt(b / GRID_H))
        s = setAttr(s, "hidden", if (c.hidden) "1" else "0")
        s
    }
}

private fun listBases(dir: File): List<String> =
    (dir.listFiles()?.map { it.name } ?: emptyList())
        .filter { (it.endsWith(".xml") || it.endsWith(".xml.default")) && !it.startsWith("touch_settings") }
        .map { it.removeSuffix(".default") }
        .distinct()
        .sorted()

private fun pretty(tag: String): String = when (tag) {
    "stick" -> "Move stick"
    "touch" -> "Look area"
    "dpad_move" -> "WASD pad"
    else -> tag.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Big areas are drawn first so the small buttons on top of them stay selectable. */
private fun drawOrder(c: List<TouchControl>): List<Int> =
    c.indices.sortedByDescending { (c[it].right - c[it].left) * (c[it].bottom - c[it].top) }

private fun hit(c: List<TouchControl>, wx: Float, wy: Float, pad: Float): Int {
    for (i in drawOrder(c).asReversed()) {
        val k = c[i]
        if (wx >= k.left - pad && wx <= k.right + pad && wy >= k.top - pad && wy <= k.bottom + pad) return i
    }
    return -1
}

private fun baseUnit(size: IntSize): Float = minOf(size.width / GRID_W, size.height / GRID_H) * 0.96f

/** A full-screen page to move, resize and show or hide touch buttons without starting a game. */
@Composable
fun LayoutEditorScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp > config.screenHeightDp && config.screenWidthDp >= 600
    val dir = remember { LayoutPresets.layoutDir() }
    val bases = remember { listBases(dir) }
    var current by remember { mutableStateOf(bases.firstOrNull { it.startsWith("game") } ?: bases.firstOrNull()) }

    val controls = remember { mutableStateListOf<TouchControl>() }
    val snapshot = remember { HashMap<String, TouchControl>() }
    val history = remember { ArrayList<List<TouchControl>>() }
    var hIndex by remember { mutableStateOf(0) }
    var savedIndex by remember { mutableStateOf(0) }
    var original by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Int?>(null) }
    var snap by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(0) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var menu by remember { mutableStateOf(false) }
    var fileMenu by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var confirmDefaults by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }

    val dirty = hIndex != savedIndex
    val canUndo = hIndex > 0
    val canRedo = hIndex < history.size - 1

    fun toast(res: Int) = Toast.makeText(ctx, ctx.getString(res), Toast.LENGTH_SHORT).show()
    fun unit(): Float = baseUnit(viewSize) * zoom
    fun snapV(v: Float): Float = if (snap) (v / SNAP_STEP).roundToInt() * SNAP_STEP else v

    fun fitView() {
        if (viewSize.width == 0 || viewSize.height == 0) return
        zoom = 1f
        val u = baseUnit(viewSize)
        pan = Offset((viewSize.width - GRID_W * u) / 2f, (viewSize.height - GRID_H * u) / 2f)
    }

    fun zoomBy(factor: Float) {
        if (viewSize.width == 0) return
        val centre = Offset(viewSize.width / 2f, viewSize.height / 2f)
        val oldUnit = unit()
        zoom = (zoom * factor).coerceIn(0.6f, 6f)
        val ratio = unit() / oldUnit
        pan = centre - (centre - pan) * ratio
    }

    fun centreOn(i: Int, targetZoom: Float?) {
        val c = controls.getOrNull(i) ?: return
        if (viewSize.width == 0) return
        if (targetZoom != null) zoom = targetZoom
        val u = unit()
        pan = Offset(
            viewSize.width / 2f - (c.left + c.right) / 2f * u,
            viewSize.height / 2f - (c.top + c.bottom) / 2f * u,
        )
    }

    fun commit() {
        while (history.size > hIndex + 1) history.removeAt(history.lastIndex)
        history.add(controls.toList())
        if (history.size > 60) {
            history.removeAt(0)
            savedIndex -= 1
        }
        hIndex = history.lastIndex
    }

    fun restore(list: List<TouchControl>) {
        controls.clear()
        controls.addAll(list)
    }

    fun undo() {
        if (hIndex > 0) {
            hIndex -= 1
            restore(history[hIndex])
        }
    }

    fun redo() {
        if (hIndex < history.size - 1) {
            hIndex += 1
            restore(history[hIndex])
        }
    }

    fun load(base: String?) {
        controls.clear()
        snapshot.clear()
        history.clear()
        selected = null
        original = ""
        var repaired = 0
        if (base != null) {
            val f = File(dir, base)
            val src = if (f.exists()) f else File(dir, "$base.default")
            original = runCatching { src.readText() }.getOrDefault("")
            val parsed = parseControls(original)
            repaired = parsed.repaired
            controls.addAll(parsed.controls)
            controls.forEach { snapshot[it.name] = it }
        }
        history.add(controls.toList())
        hIndex = 0
        // A file written by the old editor is repaired in memory: mark it unsaved so Save shows.
        savedIndex = if (repaired > 0) -1 else 0
        if (repaired > 0) toast(R.string.layout_editor_repaired)
        fitView()
    }

    fun save(): Boolean {
        val b = current ?: return false
        val target = File(dir, b)
        val text = applyControls(original, controls.toList())
        // Never write a file that lost a button on the way.
        if (parseControls(text).controls.size != controls.size) {
            toast(R.string.layout_editor_save_failed)
            return false
        }
        runCatching { if (target.exists()) target.copyTo(File(dir, "$b.bak"), overwrite = true) }
        val ok = runCatching { target.writeText(text) }.isSuccess
        if (ok) {
            original = text
            savedIndex = hIndex
        }
        toast(if (ok) R.string.layout_editor_saved else R.string.layout_editor_save_failed)
        return ok
    }

    fun restoreFrom(file: File): Boolean {
        val b = current ?: return false
        if (!file.exists() || parseControls(runCatching { file.readText() }.getOrDefault("")).controls.size < 3) return false
        val ok = runCatching { file.copyTo(File(dir, b), overwrite = true) }.isSuccess
        if (ok) load(b)
        return ok
    }

    fun mirror() {
        for (i in controls.indices) {
            val c = controls[i]
            controls[i] = c.copy(left = GRID_W - c.right, right = GRID_W - c.left)
        }
        commit()
    }

    fun requestClose() {
        if (dirty) confirmExit = true else onClose()
    }

    LaunchedEffect(current) { load(current) }
    LaunchedEffect(viewSize.width, viewSize.height) { fitView() }
    LaunchedEffect(Unit) { shown = true }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SlimeOverlay(visible = shown) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF0A0A0F)) {
                Column(Modifier.fillMaxSize()) {
                    // Top bar
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF14141C))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextButton(onClick = { requestClose() }) { Text("\u2039 " + stringResource(R.string.instance_back)) }
                        Text(
                            stringResource(R.string.layout_editor_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        if (bases.size > 1) {
                            Box {
                                OutlinedButton(onClick = { fileMenu = true }) { Text(current ?: "-", maxLines = 1) }
                                DropdownMenu(expanded = fileMenu, onDismissRequest = { fileMenu = false }) {
                                    bases.forEach { b ->
                                        DropdownMenuItem(
                                            text = { Text(b) },
                                            onClick = {
                                                if (dirty) save()
                                                current = b
                                                fileMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        ToolButton("\u21B6", canUndo) { undo() }
                        ToolButton("\u21B7", canRedo) { redo() }
                        ToolButton("\u2212") { zoomBy(0.8f) }
                        Text("${(zoom * 100).roundToInt()}%", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                        ToolButton("+") { zoomBy(1.25f) }
                        ToolButton("\u2316") { fitView() }
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = { save() }, enabled = dirty) { Text(stringResource(R.string.instance_save)) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text((if (snap) "\u2713 " else "") + stringResource(R.string.layout_editor_snap)) },
                                    onClick = { snap = !snap; menu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.layout_editor_mirror)) },
                                    onClick = { menu = false; mirror() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.layout_editor_restore_backup)) },
                                    onClick = {
                                        menu = false
                                        val b = current
                                        val ok = b != null && restoreFrom(File(dir, "$b.bak"))
                                        toast(if (ok) R.string.layout_editor_restored else R.string.layout_editor_no_backup)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.layout_editor_reset)) },
                                    onClick = { menu = false; confirmDefaults = true },
                                )
                            }
                        }
                    }

                    if (bases.isEmpty()) {
                        Text(
                            stringResource(R.string.layout_editor_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    } else {
                        val canvasArea: @Composable (Modifier) -> Unit = { mod ->
                            Canvas(
                                mod
                                    .onSizeChanged { viewSize = it }
                                    .pointerInput(Unit) {
                                        detectTapGestures { off ->
                                            val u = unit()
                                            val h = hit(controls, (off.x - pan.x) / u, (off.y - pan.y) / u, 10.dp.toPx() / u)
                                            selected = if (h >= 0) h else null
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        var mode = 0 // 0 pan, 1 move, 2 resize
                                        var target = -1
                                        var start: TouchControl? = null
                                        var acc = Offset.Zero
                                        detectDragGestures(
                                            onDragStart = { off ->
                                                val u = unit()
                                                val handleR = 30.dp.toPx()
                                                val s = selected
                                                val sc = if (s != null) controls.getOrNull(s) else null
                                                mode = 0
                                                if (s != null && sc != null &&
                                                    (off - Offset(pan.x + sc.right * u, pan.y + sc.bottom * u)).getDistance() <= handleR
                                                ) {
                                                    mode = 2
                                                    target = s
                                                } else {
                                                    val h = hit(controls, (off.x - pan.x) / u, (off.y - pan.y) / u, 10.dp.toPx() / u)
                                                    if (h >= 0) {
                                                        mode = 1
                                                        target = h
                                                        selected = h
                                                    }
                                                }
                                                if (mode != 0) {
                                                    start = controls[target]
                                                    acc = Offset.Zero
                                                }
                                            },
                                            onDragEnd = {
                                                if (mode != 0) commit()
                                                mode = 0
                                            },
                                            onDragCancel = { mode = 0 },
                                        ) { change, drag ->
                                            change.consume()
                                            val u = unit()
                                            val s0 = start
                                            if (mode == 0 || s0 == null) {
                                                pan += drag
                                            } else {
                                                acc += Offset(drag.x / u, drag.y / u)
                                                if (mode == 1) {
                                                    val w = s0.right - s0.left
                                                    val h = s0.bottom - s0.top
                                                    val nl = snapV(s0.left + acc.x).coerceIn(0f, maxOf(0f, GRID_W - w))
                                                    val nt = snapV(s0.top + acc.y).coerceIn(0f, maxOf(0f, GRID_H - h))
                                                    controls[target] = s0.copy(left = nl, right = nl + w, top = nt, bottom = nt + h)
                                                } else {
                                                    val nr = snapV(s0.right + acc.x).coerceIn(s0.left + MIN_SIZE, GRID_W)
                                                    val nb = snapV(s0.bottom + acc.y).coerceIn(s0.top + MIN_SIZE, GRID_H)
                                                    controls[target] = s0.copy(right = nr, bottom = nb)
                                                }
                                            }
                                        }
                                    },
                            ) {
                                val u = baseUnit(viewSize) * zoom
                                val p = pan
                                val corner = 14.dp.toPx()
                                drawRoundRect(
                                    color = Color(0xFF101018),
                                    topLeft = p,
                                    size = Size(GRID_W * u, GRID_H * u),
                                    cornerRadius = CornerRadius(corner),
                                )
                                val strong = Color(0x26FFFFFF)
                                val soft = Color(0x12FFFFFF)
                                for (x in 0..26) {
                                    drawLine(
                                        if (x % 2 == 0) strong else soft,
                                        Offset(p.x + x * u, p.y),
                                        Offset(p.x + x * u, p.y + GRID_H * u),
                                        strokeWidth = 1f,
                                    )
                                }
                                for (y in 0..16) {
                                    drawLine(
                                        if (y % 2 == 0) strong else soft,
                                        Offset(p.x, p.y + y * u),
                                        Offset(p.x + GRID_W * u, p.y + y * u),
                                        strokeWidth = 1f,
                                    )
                                }
                                drawRoundRect(
                                    color = Color(0x55FFFFFF),
                                    topLeft = p,
                                    size = Size(GRID_W * u, GRID_H * u),
                                    cornerRadius = CornerRadius(corner),
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                                for (i in drawOrder(controls)) {
                                    val c = controls[i]
                                    val tl = Offset(p.x + c.left * u, p.y + c.top * u)
                                    val sz = Size((c.right - c.left) * u, (c.bottom - c.top) * u)
                                    val isSel = selected == i
                                    val big = (c.right - c.left) * (c.bottom - c.top) > 40f
                                    val base = if (c.hidden) Color(0xFF8E8E9A) else Color(0xFFFF7043)
                                    val a = if (c.hidden) 0.14f else if (big) 0.16f else 0.40f
                                    val radius = CornerRadius(minOf(sz.width, sz.height).coerceAtLeast(8f) * 0.2f)
                                    if (isSel) {
                                        drawRoundRect(
                                            color = HomeAccent.copy(alpha = 0.2f),
                                            topLeft = tl - Offset(7f, 7f),
                                            size = Size(sz.width + 14f, sz.height + 14f),
                                            cornerRadius = radius,
                                        )
                                    }
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            listOf(base.copy(alpha = a + 0.14f), base.copy(alpha = a)),
                                            startY = tl.y,
                                            endY = tl.y + sz.height,
                                        ),
                                        topLeft = tl,
                                        size = sz,
                                        cornerRadius = radius,
                                    )
                                    drawRoundRect(
                                        color = if (isSel) HomeAccent else base.copy(alpha = 0.85f),
                                        topLeft = tl,
                                        size = sz,
                                        cornerRadius = radius,
                                        style = Stroke(
                                            width = if (isSel) 3.dp.toPx() else 1.5.dp.toPx(),
                                            pathEffect = if (c.hidden) PathEffect.dashPathEffect(floatArrayOf(16f, 10f)) else null,
                                        ),
                                    )
                                    val label = pretty(c.name)
                                    paint.textSize = (u * 0.55f).coerceIn(18f, 42f)
                                    paint.color = if (c.hidden) android.graphics.Color.argb(150, 255, 255, 255) else android.graphics.Color.WHITE
                                    val avail = sz.width - 10f
                                    val tw = paint.measureText(label)
                                    if (tw > avail && avail > 20f) paint.textSize = maxOf(12f, paint.textSize * avail / tw)
                                    if (sz.width > 36f && sz.height > 28f) {
                                        drawIntoCanvas { canvas ->
                                            canvas.nativeCanvas.drawText(
                                                label,
                                                tl.x + sz.width / 2f,
                                                tl.y + sz.height / 2f - (paint.descent() + paint.ascent()) / 2f,
                                                paint,
                                            )
                                        }
                                    }
                                    if (isSel) {
                                        val hc = Offset(tl.x + sz.width, tl.y + sz.height)
                                        drawCircle(HomeAccent, radius = 14.dp.toPx(), center = hc)
                                        drawCircle(Color.White, radius = 14.dp.toPx(), center = hc, style = Stroke(width = 2.dp.toPx()))
                                    }
                                }
                            }
                        }

                        val list: @Composable (Modifier) -> Unit = { mod ->
                            Column(mod.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        0 to R.string.layout_editor_filter_all,
                                        1 to R.string.layout_editor_filter_visible,
                                        2 to R.string.layout_editor_filter_hidden,
                                    ).forEach { (id, res) ->
                                        FilterChip(
                                            selected = filter == id,
                                            onClick = { filter = id },
                                            label = { Text(stringResource(res), fontSize = 11.sp) },
                                        )
                                    }
                                }
                                val ids = controls.indices.filter {
                                    when (filter) {
                                        1 -> !controls[it].hidden
                                        2 -> controls[it].hidden
                                        else -> true
                                    }
                                }
                                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(ids, key = { it }) { i ->
                                        val c = controls[i]
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (selected == i) Color(0x33FF7043) else Color(0x14FFFFFF))
                                                .clickable {
                                                    selected = i
                                                    centreOn(i, null)
                                                }
                                                .padding(start = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                pretty(c.name),
                                                color = if (c.hidden) Color(0x88FFFFFF) else Color.White,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            IconButton(onClick = {
                                                controls[i] = c.copy(hidden = !c.hidden)
                                                commit()
                                            }) {
                                                Icon(
                                                    painterResource(if (c.hidden) R.drawable.ic_touch_eye_off else R.drawable.ic_touch_eye),
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val inspector: @Composable (Modifier) -> Unit = { mod ->
                            val i = selected
                            val c = if (i != null) controls.getOrNull(i) else null
                            Column(
                                mod.verticalScroll(rememberScrollState()).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                if (i == null || c == null) {
                                    Text(
                                        stringResource(R.string.layout_editor_pick),
                                        color = Color(0xB3FFFFFF),
                                        fontSize = 13.sp,
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            pretty(c.name),
                                            color = HomeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        IconButton(onClick = {
                                            controls[i] = c.copy(hidden = !c.hidden)
                                            commit()
                                        }) {
                                            Icon(
                                                painterResource(if (c.hidden) R.drawable.ic_touch_eye_off else R.drawable.ic_touch_eye),
                                                contentDescription = null,
                                                tint = Color.White,
                                            )
                                        }
                                    }
                                    val w = c.right - c.left
                                    val h = c.bottom - c.top
                                    NudgeRow("X", c.left, 0f, GRID_W - w, { v -> controls[i] = c.copy(left = v, right = v + w) }, { commit() })
                                    NudgeRow("Y", c.top, 0f, GRID_H - h, { v -> controls[i] = c.copy(top = v, bottom = v + h) }, { commit() })
                                    NudgeRow("W", w, MIN_SIZE, GRID_W - c.left, { v -> controls[i] = c.copy(right = c.left + v) }, { commit() })
                                    NudgeRow("H", h, MIN_SIZE, GRID_H - c.top, { v -> controls[i] = c.copy(bottom = c.top + v) }, { commit() })
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { centreOn(i, 2.5f) }) { Text(stringResource(R.string.layout_editor_focus), fontSize = 12.sp) }
                                        OutlinedButton(
                                            enabled = snapshot[c.name] != null && snapshot[c.name] != c,
                                            onClick = {
                                                snapshot[c.name]?.let { controls[i] = it }
                                                commit()
                                            },
                                        ) { Text(stringResource(R.string.layout_editor_reset_control), fontSize = 12.sp) }
                                    }
                                }
                            }
                        }

                        if (wide) {
                            Row(Modifier.fillMaxSize()) {
                                list(Modifier.width(210.dp).fillMaxSize())
                                canvasArea(Modifier.weight(1f).fillMaxSize())
                                inspector(Modifier.width(290.dp).fillMaxSize().background(Color(0xFF12121A)))
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                canvasArea(Modifier.weight(1f).fillMaxWidth())
                                inspector(Modifier.fillMaxWidth().height(250.dp).background(Color(0xFF12121A)))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.layout_editor_discard)) },
            text = { Text(stringResource(R.string.layout_editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    if (save()) {
                        confirmExit = false
                        onClose()
                    }
                }) { Text(stringResource(R.string.instance_save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.instance_cancel)) }
                    TextButton(onClick = {
                        confirmExit = false
                        onClose()
                    }) { Text(stringResource(R.string.layout_editor_discard_btn)) }
                }
            },
        )
    }

    if (confirmDefaults) {
        AlertDialog(
            onDismissRequest = { confirmDefaults = false },
            title = { Text(stringResource(R.string.layout_editor_reset)) },
            text = { Text(stringResource(R.string.layout_editor_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDefaults = false
                    val b = current
                    val ok = b != null && restoreFrom(File(dir, "$b.default"))
                    toast(if (ok) R.string.layout_editor_reset_done else R.string.controls_failed)
                }) { Text(stringResource(R.string.layout_editor_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDefaults = false }) { Text(stringResource(R.string.instance_cancel)) }
            },
        )
    }
}

@Composable
private fun ToolButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFFFFF))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Color(0x55FFFFFF),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A slider with fine -/+ steps and the current value, so a position can be set exactly. */
@Composable
private fun NudgeRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    val hi = if (max - min < 0.01f) min + 0.01f else max
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xB3FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
        ToolButton("\u2212") { onChange((value - 0.1f).coerceIn(min, hi)); onDone() }
        Slider(
            value = value.coerceIn(min, hi),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = min..hi,
            modifier = Modifier.weight(1f),
        )
        ToolButton("+") { onChange((value + 0.1f).coerceIn(min, hi)); onDone() }
        Text(
            String.format(Locale.US, "%.1f", value),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.width(36.dp),
        )
    }
}
