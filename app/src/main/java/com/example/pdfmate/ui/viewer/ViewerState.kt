package com.example.pdfmate.ui.viewer

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.geometry.Offset
import com.example.pdfmate.core.pdf.FontFamilyHint
import com.example.pdfmate.core.pdf.InkStroke
import com.example.pdfmate.core.pdf.NormPoint
import com.example.pdfmate.core.pdf.NormRect
import com.example.pdfmate.core.pdf.PageTextPosition
import com.example.pdfmate.core.pdf.TextBlock
import com.example.pdfmate.core.pdf.TextStyleHint

/** What the user is currently typing into while in edit mode. */
sealed interface EditTarget {
    val pageIndex: Int
    val style: TextStyleHint

    data class Existing(override val pageIndex: Int, val block: TextBlock) : EditTarget {
        override val style: TextStyleHint get() = block.style
    }

    data class New(override val pageIndex: Int, val origin: NormPoint, override val style: TextStyleHint) : EditTarget
}

enum class InkTool { PEN, MARKER, HIGHLIGHT_TEXT, ERASER, PAN }

enum class TextSelectionHandle { START, END }

data class TextSelectionRange(val start: PageTextPosition, val end: PageTextPosition)

internal fun accumulatePinchTranslation(
    origin: Offset,
    translation: Offset,
    centroid: Offset,
    pan: Offset,
    scaleChange: Float
): Offset = centroid + pan + (origin + translation - centroid) * scaleChange - origin

private enum class MarkKind { INK, HIGHLIGHT }

private data class MarkRecord(val pageIndex: Int, val kind: MarkKind)

/** UI-only state for the viewer: zoom, in-progress edits, pending annotations, crop and signature placement. */
@Stable
class ViewerState {
    var zoom by mutableFloatStateOf(1f)

    // Edit mode
    var addTextMode by mutableStateOf(false)
    var editTarget by mutableStateOf<EditTarget?>(null)
    var editValue by mutableStateOf(TextFieldValue(""))
    var newTextStyle by mutableStateOf(DefaultNewTextStyle)

    /** Set once an edit has been committed; the overlay stays until the page renders this version. */
    var clearEditAtVersion by mutableStateOf<Int?>(null)

    // Annotate mode
    var inkTool by mutableStateOf(InkTool.PEN)
    var inkColor by mutableIntStateOf(InkColors.first())
    var inkWidth by mutableFloatStateOf(0.004f)
    val ink = mutableStateMapOf<Int, List<InkStroke>>()
    val highlights = mutableStateMapOf<Int, List<Pair<NormRect, Int>>>()
    private val history = mutableStateListOf<MarkRecord>()

    val hasPendingMarks: Boolean get() = history.isNotEmpty()

    // Crop mode
    var cropPage by mutableIntStateOf(0)
    var cropRect by mutableStateOf(NormRect(0.08f, 0.08f, 0.92f, 0.92f))
    var cropAllPages by mutableStateOf(false)

    // Signature placement
    var signature by mutableStateOf<Bitmap?>(null)
    var signaturePage by mutableIntStateOf(0)
    var signatureRect by mutableStateOf(NormRect(0.55f, 0.75f, 0.9f, 0.85f))

    fun startEditing(target: EditTarget) {
        editTarget = target
        clearEditAtVersion = null
        val text = (target as? EditTarget.Existing)?.block?.text.orEmpty()
        editValue = TextFieldValue(text, selection = androidx.compose.ui.text.TextRange(text.length))
    }

    fun cancelEditing() {
        editTarget = null
        clearEditAtVersion = null
    }

    fun addStroke(pageIndex: Int, stroke: InkStroke) {
        ink[pageIndex] = ink[pageIndex].orEmpty() + stroke
        history += MarkRecord(pageIndex, MarkKind.INK)
    }

    fun addHighlight(pageIndex: Int, rect: NormRect, color: Int) {
        val existing = highlights[pageIndex].orEmpty()
        if (existing.any { it.first == rect }) return
        highlights[pageIndex] = existing + (rect to color)
        history += MarkRecord(pageIndex, MarkKind.HIGHLIGHT)
    }

    /** Removes pending strokes that pass near [point] on the given page. */
    fun eraseAt(pageIndex: Int, point: NormPoint, radius: Float) {
        val strokes = ink[pageIndex] ?: return
        val kept = strokes.filterNot { stroke -> stroke.points.any { kotlin.math.hypot(it.x - point.x, it.y - point.y) < radius } }
        if (kept.size == strokes.size) return
        var removed = strokes.size - kept.size
        ink[pageIndex] = kept
        for (i in history.indices.reversed()) {
            if (removed == 0) break
            if (history[i].pageIndex == pageIndex && history[i].kind == MarkKind.INK) {
                history.removeAt(i)
                removed--
            }
        }
        val marks = highlights[pageIndex]
        if (marks != null) {
            val keptMarks = marks.filterNot { it.first.inflate(radius, radius).contains(point.x, point.y) }
            if (keptMarks.size != marks.size) {
                var removedMarks = marks.size - keptMarks.size
                highlights[pageIndex] = keptMarks
                for (i in history.indices.reversed()) {
                    if (removedMarks == 0) break
                    if (history[i].pageIndex == pageIndex && history[i].kind == MarkKind.HIGHLIGHT) {
                        history.removeAt(i)
                        removedMarks--
                    }
                }
            }
        }
    }

    fun undoMark() {
        val last = history.removeLastOrNull() ?: return
        when (last.kind) {
            MarkKind.INK -> ink[last.pageIndex] = ink[last.pageIndex].orEmpty().dropLast(1)
            MarkKind.HIGHLIGHT -> highlights[last.pageIndex] = highlights[last.pageIndex].orEmpty().dropLast(1)
        }
    }

    fun clearMarks() {
        ink.clear()
        highlights.clear()
        history.clear()
    }

    companion object {
        val InkColors = listOf(
            0xFF111827.toInt(), 0xFFE5394B.toInt(), 0xFF2563EB.toInt(),
            0xFF16A34A.toInt(), 0xFFFACC15.toInt(), 0xFFF472B6.toInt()
        )
        val DefaultNewTextStyle = TextStyleHint(
            fontName = "Helvetica",
            family = FontFamilyHint.SANS,
            bold = false,
            italic = false,
            colorArgb = 0xFF111827.toInt(),
            sizePt = 14f
        )
    }
}
