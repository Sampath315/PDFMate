package com.example.pdfmate.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.pdfmate.ui.components.GradientButton
import com.example.pdfmate.ui.components.ToolCatalog
import com.example.pdfmate.ui.components.ToolSheetLayout
import com.example.pdfmate.ui.ToolId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val SignatureInks = listOf(Color(0xFF111827), Color(0xFF1D4ED8), Color(0xFFB91C1C))

/** Draw-your-signature pad. Produces a tightly cropped, transparent bitmap. */
@Composable
internal fun SignatureSheet(onSignature: (Bitmap) -> Unit, onImportImage: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val live = remember { mutableStateListOf<Offset>() }
    var inkIndex by remember { mutableIntStateOf(0) }
    var padWidth by remember { mutableStateOf(1f) }
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }
    ToolSheetLayout(ToolCatalog[ToolId.SIGN], "Draw your signature, then drag it into place on the page.") {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
        ) {
            if (strokes.isEmpty() && live.isEmpty()) {
                Text(
                    "Sign here",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFB8BECC),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 28.dp, vertical = 44.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFD5DAE5))
            )
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        padWidth = size.width.toFloat()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            live += down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                live += change.position
                            }
                            if (live.isNotEmpty()) strokes += live.toList()
                            live.clear()
                        }
                    }
            ) {
                val ink = SignatureInks[inkIndex]
                (strokes + listOf(live.toList())).forEach { points ->
                    if (points.isEmpty()) return@forEach
                    if (points.size == 1) {
                        drawCircle(ink, strokePx / 2, points[0])
                        return@forEach
                    }
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            val mid = (points[i - 1] + points[i]) / 2f
                            quadraticTo(points[i - 1].x, points[i - 1].y, mid.x, mid.y)
                        }
                        lineTo(points.last().x, points.last().y)
                    }
                    drawPath(path, ink, style = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignatureInks.forEachIndexed { index, color ->
                Box(
                    Modifier
                        .padding(end = 10.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (index == inkIndex) 3.dp else 0.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                        .clickable { inkIndex = index }
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) {
                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Clear")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onImportImage, modifier = Modifier.height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Image, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Import image")
            }
            GradientButton(
                text = "Place signature",
                icon = Icons.Rounded.Check,
                enabled = strokes.isNotEmpty(),
                onClick = { renderSignature(strokes, SignatureInks[inkIndex].toArgb(), strokePx, padWidth)?.let(onSignature) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Re-draws the strokes at 2x into a cropped transparent bitmap. */
private fun renderSignature(strokes: List<List<Offset>>, color: Int, strokePx: Float, padWidth: Float): Bitmap? {
    val points = strokes.flatten()
    if (points.isEmpty()) return null
    val minX = points.minOf { it.x }
    val minY = points.minOf { it.y }
    val maxX = points.maxOf { it.x }
    val maxY = points.maxOf { it.y }
    val margin = strokePx * 2
    val scale = (1600f / max(padWidth, 1f)).coerceIn(1f, 3f)
    val width = ((maxX - minX + margin * 2) * scale).roundToInt().coerceAtLeast(8)
    val height = ((maxY - minY + margin * 2) * scale).roundToInt().coerceAtLeast(8)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = strokePx * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    fun map(point: Offset) = Offset((point.x - minX + margin) * scale, (point.y - minY + margin) * scale)
    strokes.forEach { stroke ->
        if (stroke.isEmpty()) return@forEach
        val path = android.graphics.Path()
        val first = map(stroke[0])
        path.moveTo(first.x, first.y)
        if (stroke.size == 1) path.lineTo(first.x + 0.1f, first.y)
        for (i in 1 until stroke.size) {
            val previous = map(stroke[i - 1])
            val current = map(stroke[i])
            path.quadTo(previous.x, previous.y, (previous.x + current.x) / 2, (previous.y + current.y) / 2)
        }
        val last = map(stroke.last())
        path.lineTo(last.x, last.y)
        canvas.drawPath(path, paint)
    }
    return bitmap
}

/** Loads an image for use as a signature, turning a white paper background transparent. */
internal fun loadSignatureImage(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val bitmap = if (decoded.isMutable && decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, true)
    if (bitmap !== decoded) decoded.recycle()
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        val lightest = min(r, min(g, b))
        if (lightest > 200) {
            // Fade near-white smoothly to transparent so edges stay clean.
            val alpha = ((255 - lightest) * 255 / 55).coerceIn(0, 255)
            pixels[i] = (min(alpha, pixel ushr 24) shl 24) or (pixel and 0xFFFFFF)
        }
    }
    bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return bitmap
}
