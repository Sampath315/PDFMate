package com.example.pdfmate.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.pdfmate.core.pdf.FontFamilyHint
import com.example.pdfmate.core.pdf.InkStroke
import com.example.pdfmate.core.pdf.NormPoint
import com.example.pdfmate.core.pdf.NormRect
import com.example.pdfmate.core.pdf.PageTextLayout
import com.example.pdfmate.core.pdf.TextStyleHint
import com.example.pdfmate.ui.theme.BrandRed
import com.example.pdfmate.ui.theme.EditViolet
import kotlin.math.roundToInt

private fun Offset.toNorm(size: IntSize) = NormPoint(
    (x / size.width).coerceIn(0f, 1f),
    (y / size.height).coerceIn(0f, 1f)
)

private fun NormRect.toPx(size: Size) = androidx.compose.ui.geometry.Rect(left * size.width, top * size.height, right * size.width, bottom * size.height)

// region Search

@Composable
internal fun SearchHighlights(rects: List<NormRect>, current: List<NormRect>) {
    Canvas(Modifier.fillMaxSize()) {
        rects.forEach { rect ->
            val r = rect.inflate(0.002f, 0.002f).toPx(size)
            drawRoundRect(Color(0x66FFD54F), r.topLeft, r.size, CornerRadius(3f), blendMode = BlendMode.Multiply)
        }
        current.forEach { rect ->
            val r = rect.inflate(0.003f, 0.003f).toPx(size)
            drawRoundRect(Color(0x88FF9800), r.topLeft, r.size, CornerRadius(4f), blendMode = BlendMode.Multiply)
            drawRoundRect(Color(0xFFFF6F00), r.topLeft, r.size, CornerRadius(4f), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
internal fun TextSelectionHighlights(rects: List<NormRect>) {
    Canvas(Modifier.fillMaxSize()) {
        rects.forEach { rect ->
            val bounds = rect.inflate(0.002f, 0.003f).toPx(size)
            drawRoundRect(Color(0x885B9DFF), bounds.topLeft, bounds.size, CornerRadius(3f), blendMode = BlendMode.Multiply)
        }
    }
}

// endregion

// region Edit text

/**
 * Outlines editable text and hosts the in-place text field. The field copies the size, weight, slant, family and
 * color of the text it replaces, and sits on a patch sampled from the page background so the old text disappears.
 */
@Composable
internal fun EditOverlay(
    pageIndex: Int,
    layout: PageTextLayout?,
    state: ViewerState,
    bitmap: Bitmap?,
    pageWidthPt: Float,
    onLockedText: () -> Unit,
    onSubmit: () -> Unit
) {
    val target = state.editTarget?.takeIf { it.pageIndex == pageIndex }
    val currentLayout by rememberUpdatedState(layout)
    val editing = state.editTarget != null
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(pageIndex, editing) {
                if (editing) return@pointerInput
                detectTapGestures { offset ->
                    val point = offset.toNorm(size)
                    if (state.addTextMode) {
                        state.startEditing(EditTarget.New(pageIndex, point, state.newTextStyle))
                        return@detectTapGestures
                    }
                    val blocks = currentLayout?.blocks.orEmpty()
                    val hit = blocks.firstOrNull { it.bounds.contains(point.x, point.y) }
                        ?: blocks.firstOrNull { it.bounds.inflate(0.006f, 0.004f).contains(point.x, point.y) }
                    when {
                        hit == null -> Unit
                        hit.editable -> state.startEditing(EditTarget.Existing(pageIndex, hit))
                        else -> onLockedText()
                    }
                }
            }
    ) {
        val pageSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        if (!state.addTextMode) {
            Canvas(Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                layout?.blocks?.forEach { block ->
                    if ((target as? EditTarget.Existing)?.block?.id == block.id) return@forEach
                    val r = block.bounds.inflate(0.003f, 0.002f).toPx(size)
                    if (block.editable) {
                        drawRoundRect(EditViolet.copy(alpha = 0.07f), r.topLeft, r.size, CornerRadius(4f))
                        drawRoundRect(EditViolet.copy(alpha = 0.75f), r.topLeft, r.size, CornerRadius(4f), style = Stroke(1.dp.toPx(), pathEffect = dash))
                    } else {
                        drawRoundRect(Color.Gray.copy(alpha = 0.35f), r.topLeft, r.size, CornerRadius(4f), style = Stroke(1.dp.toPx(), pathEffect = dash))
                    }
                }
            }
        }
        if (target != null) {
            val pxPerPt = pageSize.width / pageWidthPt.coerceAtLeast(1f)
            InPlaceTextField(target, state, bitmap, pageSize, pxPerPt, onSubmit)
        }
    }
}

@Composable
private fun InPlaceTextField(target: EditTarget, state: ViewerState, bitmap: Bitmap?, pageSize: IntSize, pxPerPt: Float, onSubmit: () -> Unit) {
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val committing = state.clearEditAtVersion != null
    LaunchedEffect(target) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    val style = target.style
    val fontPx = style.sizePt * pxPerPt
    val textStyle = TextStyle(
        color = Color(style.colorArgb).copy(alpha = 1f),
        fontSize = with(density) { fontPx.toSp() },
        lineHeight = with(density) { (fontPx * 1.2f).toSp() },
        fontFamily = style.family.toCompose(),
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal
    )
    when (target) {
        is EditTarget.Existing -> {
            val bounds = target.block.bounds
            val pad = 2.dp
            val cover = remember(bitmap, bounds) { bitmap?.backgroundAround(bounds) ?: Color.White }
            val left = (bounds.left * pageSize.width).roundToInt()
            val top = (bounds.top * pageSize.height).roundToInt()
            val width = with(density) { (bounds.width * pageSize.width).toDp() }
            val height = with(density) { (bounds.height * pageSize.height).toDp() }
            Box(
                Modifier
                    .offset { IntOffset(left, top) }
                    .offset(-pad, -pad)
                    .size(width + pad * 2, height + pad * 2)
                    .background(cover)
            )
            Box(
                Modifier
                    .offset { IntOffset(left, top) }
                    .offset(-pad, -pad)
                    .border(1.5.dp, EditViolet, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    .background(cover)
                    .padding(pad)
            ) {
                BasicTextField(
                    value = state.editValue,
                    onValueChange = { state.editValue = it },
                    readOnly = committing,
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(EditViolet),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier
                        .widthIn(min = width)
                        .focusRequester(focus)
                )
            }
        }
        is EditTarget.New -> {
            val left = (target.origin.x * pageSize.width).roundToInt()
            val top = (target.origin.y * pageSize.height).roundToInt()
            Box(
                Modifier
                    .offset { IntOffset(left, top) }
                    .border(1.5.dp, EditViolet, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            ) {
                BasicTextField(
                    value = state.editValue,
                    onValueChange = { state.editValue = it },
                    readOnly = committing,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(EditViolet),
                    modifier = Modifier
                        .widthIn(min = 40.dp)
                        .focusRequester(focus)
                )
            }
        }
    }
}

internal fun FontFamilyHint.toCompose(): FontFamily = when (this) {
    FontFamilyHint.SANS -> FontFamily.SansSerif
    FontFamilyHint.SERIF -> FontFamily.Serif
    FontFamilyHint.MONO -> FontFamily.Monospace
}

internal fun TextStyleHint.describe(): String = buildList {
    add(fontName.ifBlank { family.name.lowercase().replaceFirstChar { it.uppercase() } })
    add("${(sizePt * 10).roundToInt() / 10f} pt")
    if (bold) add("Bold")
    if (italic) add("Italic")
}.joinToString(" · ")

/** Picks the most common color just outside the text box, so the cover patch blends into the page. */
private fun Bitmap.backgroundAround(bounds: NormRect): Color {
    if (isRecycled || width == 0 || height == 0) return Color.White
    val l = (bounds.left * width).roundToInt()
    val r = (bounds.right * width).roundToInt()
    val t = (bounds.top * height).roundToInt()
    val b = (bounds.bottom * height).roundToInt()
    val cy = (t + b) / 2
    val cx = (l + r) / 2
    val samples = listOf(
        l - 3 to cy, r + 3 to cy, cx to t - 3, cx to b + 3,
        l - 3 to t - 3, r + 3 to b + 3, l - 3 to b + 3, r + 3 to t - 3
    ).mapNotNull { (x, y) ->
        if (x in 0 until width && y in 0 until height) getPixel(x, y) else null
    }
    val winner = samples.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return Color.White
    return Color(winner).copy(alpha = 1f)
}

// endregion

// region Annotate

@Composable
internal fun InkOverlay(pageIndex: Int, state: ViewerState, layout: PageTextLayout?) {
    val live = remember { mutableStateListOf<NormPoint>() }
    val currentLayout by rememberUpdatedState(layout)
    val tool = state.inkTool
    val color = state.inkColor
    val width = state.inkWidth
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(pageIndex, tool, color, width) {
                if (tool == InkTool.PAN) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    down.consume()
                    fun handle(point: NormPoint) {
                        when (tool) {
                            InkTool.HIGHLIGHT_TEXT -> currentLayout?.blocks
                                ?.firstOrNull { it.bounds.inflate(0.004f, 0.003f).contains(point.x, point.y) }
                                ?.let { state.addHighlight(pageIndex, it.bounds, color) }
                            InkTool.ERASER -> state.eraseAt(pageIndex, point, 0.025f)
                            else -> live += point
                        }
                    }
                    handle(down.position.toNorm(size))
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || event.changes.any { it.isConsumed }) {
                            cancelled = true
                            break
                        }
                        val change = event.changes.first()
                        if (!change.pressed) break
                        change.consume()
                        handle(change.position.toNorm(size))
                    }
                    if (!cancelled && live.isNotEmpty() && (tool == InkTool.PEN || tool == InkTool.MARKER)) {
                        val points = if (live.size == 1) listOf(live[0], NormPoint(live[0].x + 0.001f, live[0].y)) else live.toList()
                        val marker = tool == InkTool.MARKER
                        state.addStroke(pageIndex, InkStroke(points, color, if (marker) width * 4f else width, marker))
                    }
                    live.clear()
                }
            }
    ) {
        state.highlights[pageIndex]?.forEach { (rect, markColor) ->
            val r = rect.toPx(size)
            drawRect(Color(markColor).copy(alpha = 0.4f), r.topLeft, r.size, blendMode = BlendMode.Multiply)
        }
        state.ink[pageIndex]?.forEach { drawStroke(it.points, it.colorArgb, it.width, it.highlighter) }
        if (live.isNotEmpty()) {
            val marker = tool == InkTool.MARKER
            drawStroke(live, color, if (marker) width * 4f else width, marker)
        }
    }
}

private fun DrawScope.drawStroke(points: List<NormPoint>, color: Int, width: Float, highlighter: Boolean) {
    if (points.isEmpty()) return
    val path = Path().apply {
        moveTo(points[0].x * size.width, points[0].y * size.height)
        for (i in 1 until points.size) lineTo(points[i].x * size.width, points[i].y * size.height)
    }
    drawPath(
        path,
        Color(color).copy(alpha = if (highlighter) 0.4f else 1f),
        style = Stroke(width * size.width, cap = if (highlighter) StrokeCap.Square else StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = if (highlighter) BlendMode.Multiply else BlendMode.SrcOver
    )
}

// endregion

// region Crop & signature placement

private enum class Grab { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, MOVE }

private suspend fun PointerInputScope.editRect(
    current: () -> NormRect,
    handleRadiusPx: Float,
    allowedCorners: Set<Grab>,
    update: (start: NormRect, grab: Grab, dx: Float, dy: Float) -> NormRect,
    onChange: (NormRect) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val rect = current()
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val corners = mapOf(
            Grab.TOP_LEFT to Offset(rect.left * w, rect.top * h),
            Grab.TOP_RIGHT to Offset(rect.right * w, rect.top * h),
            Grab.BOTTOM_RIGHT to Offset(rect.right * w, rect.bottom * h),
            Grab.BOTTOM_LEFT to Offset(rect.left * w, rect.bottom * h)
        ).filterKeys { it in allowedCorners }
        val nearest = corners.minByOrNull { (_, point) -> (point - down.position).getDistance() }
        val grab = when {
            nearest != null && (nearest.value - down.position).getDistance() <= handleRadiusPx -> nearest.key
            rect.contains(down.position.x / w, down.position.y / h) -> Grab.MOVE
            else -> return@awaitEachGesture
        }
        down.consume()
        var dx = 0f
        var dy = 0f
        drag(down.id) { change ->
            val delta = change.positionChange()
            change.consume()
            dx += delta.x / w
            dy += delta.y / h
            onChange(update(rect, grab, dx, dy))
        }
    }
}

private fun moved(start: NormRect, dx: Float, dy: Float): NormRect {
    val x = dx.coerceIn(-start.left, 1f - start.right)
    val y = dy.coerceIn(-start.top, 1f - start.bottom)
    return NormRect(start.left + x, start.top + y, start.right + x, start.bottom + y)
}

@Composable
internal fun CropOverlay(state: ViewerState) {
    val density = LocalDensity.current
    val handle = with(density) { 28.dp.toPx() }
    val rect by rememberUpdatedState(state.cropRect)
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                editRect(
                    current = { rect },
                    handleRadiusPx = handle,
                    allowedCorners = setOf(Grab.TOP_LEFT, Grab.TOP_RIGHT, Grab.BOTTOM_RIGHT, Grab.BOTTOM_LEFT),
                    update = { start, grab, dx, dy ->
                        val min = 0.06f
                        when (grab) {
                            Grab.MOVE -> moved(start, dx, dy)
                            Grab.TOP_LEFT -> start.copy(
                                left = (start.left + dx).coerceIn(0f, start.right - min),
                                top = (start.top + dy).coerceIn(0f, start.bottom - min)
                            )
                            Grab.TOP_RIGHT -> start.copy(
                                right = (start.right + dx).coerceIn(start.left + min, 1f),
                                top = (start.top + dy).coerceIn(0f, start.bottom - min)
                            )
                            Grab.BOTTOM_RIGHT -> start.copy(
                                right = (start.right + dx).coerceIn(start.left + min, 1f),
                                bottom = (start.bottom + dy).coerceIn(start.top + min, 1f)
                            )
                            Grab.BOTTOM_LEFT -> start.copy(
                                left = (start.left + dx).coerceIn(0f, start.right - min),
                                bottom = (start.bottom + dy).coerceIn(start.top + min, 1f)
                            )
                        }
                    },
                    onChange = { state.cropRect = it }
                )
            }
    ) {
        val r = rect.toPx(size)
        val shade = Color.Black.copy(alpha = 0.5f)
        drawRect(shade, Offset.Zero, Size(size.width, r.top))
        drawRect(shade, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
        drawRect(shade, Offset(0f, r.top), Size(r.left, r.height))
        drawRect(shade, Offset(r.right, r.top), Size(size.width - r.right, r.height))
        drawRect(Color.White, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
        val third = Stroke(1.dp.toPx())
        for (i in 1..2) {
            drawLine(Color.White.copy(alpha = 0.5f), Offset(r.left + r.width * i / 3, r.top), Offset(r.left + r.width * i / 3, r.bottom), third.width)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(r.left, r.top + r.height * i / 3), Offset(r.right, r.top + r.height * i / 3), third.width)
        }
        listOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft).forEach {
            drawCircle(Color.White, 9.dp.toPx(), it)
            drawCircle(BrandRed, 6.dp.toPx(), it)
        }
    }
}

@Composable
internal fun SignatureOverlay(state: ViewerState, signature: Bitmap) {
    val density = LocalDensity.current
    val handle = with(density) { 32.dp.toPx() }
    val rect by rememberUpdatedState(state.signatureRect)
    val image = remember(signature) { signature.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageAspect = constraints.maxWidth.toFloat() / constraints.maxHeight.coerceAtLeast(1)
        val signatureAspect = signature.width.toFloat() / signature.height.coerceAtLeast(1)
        // Normalized height that keeps the signature's own proportions on this page.
        val heightFor = { width: Float -> width * pageAspect / signatureAspect }
        LaunchedEffect(signature, pageAspect) {
            val r = state.signatureRect
            val h = heightFor(r.width)
            val top = r.top.coerceAtMost(1f - h).coerceAtLeast(0f)
            state.signatureRect = NormRect(r.left, top, r.right, top + h)
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(pageAspect, signatureAspect) {
                    editRect(
                        current = { rect },
                        handleRadiusPx = handle,
                        allowedCorners = setOf(Grab.BOTTOM_RIGHT),
                        update = { start, grab, dx, dy ->
                            if (grab == Grab.MOVE) {
                                moved(start, dx, dy)
                            } else {
                                val maxWidth = minOf(1f - start.left, (1f - start.top) * signatureAspect / pageAspect)
                                val width = (start.width + dx).coerceIn(0.08f, maxWidth)
                                NormRect(start.left, start.top, start.left + width, start.top + heightFor(width))
                            }
                        },
                        onChange = { state.signatureRect = it }
                    )
                }
        ) {
            val r = rect.toPx(size)
            drawImage(
                image,
                dstOffset = androidx.compose.ui.unit.IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                dstSize = IntSize(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1))
            )
            drawRect(
                EditViolet,
                r.topLeft,
                r.size,
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())))
            )
            drawCircle(Color.White, 11.dp.toPx(), r.bottomRight)
            drawCircle(EditViolet, 8.dp.toPx(), r.bottomRight)
        }
    }
}

// endregion
