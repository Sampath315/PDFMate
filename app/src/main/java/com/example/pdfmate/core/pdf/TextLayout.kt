package com.example.pdfmate.core.pdf

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorN
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.font.encoding.GlyphList
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

enum class FontFamilyHint { SANS, SERIF, MONO }

data class TextStyleHint(
    val fontName: String,
    val family: FontFamilyHint,
    val bold: Boolean,
    val italic: Boolean,
    val colorArgb: Int,
    val sizePt: Float
)

/** A run of text drawn by consecutive content-stream operators with one font, size, and color. */
data class TextBlock(
    val id: String,
    val text: String,
    val bounds: NormRect,
    val style: TextStyleHint,
    val editable: Boolean
)

data class PageTextSelection(
    val pageIndex: Int,
    val text: String,
    val rects: List<NormRect>,
    val startIndex: Int,
    val endIndex: Int
)

data class PageTextPosition(val pageIndex: Int, val characterIndex: Int) : Comparable<PageTextPosition> {
    override fun compareTo(other: PageTextPosition): Int =
        compareValuesBy(this, other, { it.pageIndex }, { it.characterIndex })
}

class PageTextLayout internal constructor(
    val pageIndex: Int,
    val blocks: List<TextBlock>,
    private val searchText: String,
    private val charGlyphs: IntArray,
    private val glyphRects: List<NormRect>
) {
    val hasText: Boolean get() = glyphRects.isNotEmpty()
    val characterCount: Int get() = searchText.length

    fun selectWordAt(point: NormPoint): PageTextSelection? {
        val glyphIndex = glyphRects.indexOfFirst { it.inflate(0.002f, 0.004f).contains(point.x, point.y) }
        if (glyphIndex < 0) return null
        val characterIndex = charGlyphs.indexOf(glyphIndex)
        if (characterIndex < 0 || !searchText[characterIndex].isWordCharacter()) return null

        var start = characterIndex
        while (start > 0 && searchText[start - 1].isWordCharacter()) start--
        var end = characterIndex + 1
        while (end < searchText.length && searchText[end].isWordCharacter()) end++
        return selection(start, end)
    }

    fun selectAll(): PageTextSelection? =
        if (searchText.isBlank()) null else selection(0, searchText.length)

    fun selectRange(startIndex: Int, endIndex: Int): PageTextSelection? {
        val start = startIndex.coerceIn(0, searchText.length)
        val end = endIndex.coerceIn(start, searchText.length)
        return if (start == end) null else selection(start, end)
    }

    fun characterIndexAt(point: NormPoint): Int? {
        var closestIndex = -1
        var closestDistance = Float.POSITIVE_INFINITY
        searchText.indices.forEach { index ->
            val glyphIndex = charGlyphs[index]
            if (glyphIndex < 0) return@forEach
            val rect = glyphRects[glyphIndex]
            val dx = maxOf(rect.left - point.x, 0f, point.x - rect.right)
            val dy = maxOf(rect.top - point.y, 0f, point.y - rect.bottom)
            val distance = dx * dx + dy * dy
            if (distance < closestDistance) {
                closestIndex = index
                closestDistance = distance
            }
        }
        return closestIndex.takeIf { it >= 0 && closestDistance <= 0.0064f }
    }

    /** Each match is returned as one rectangle per line it spans. */
    fun find(query: String): List<List<NormRect>> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val matches = mutableListOf<List<NormRect>>()
        var start = searchText.indexOf(needle, 0, ignoreCase = true)
        while (start >= 0) {
            val rects = mutableListOf<NormRect>()
            for (index in start until start + needle.length) {
                val glyph = charGlyphs[index]
                if (glyph < 0) continue
                val rect = glyphRects[glyph]
                val last = rects.lastOrNull()
                if (last != null && abs(last.top - rect.top) < last.height * 0.5f) {
                    rects[rects.lastIndex] = last.union(rect)
                } else {
                    rects += rect
                }
            }
            if (rects.isNotEmpty()) matches += rects
            start = searchText.indexOf(needle, start + needle.length, ignoreCase = true)
        }
        return matches
    }

    private fun selection(start: Int, end: Int): PageTextSelection? {
        val rects = mutableListOf<NormRect>()
        for (index in start until end) {
            val glyphIndex = charGlyphs[index]
            if (glyphIndex < 0) continue
            val rect = glyphRects[glyphIndex]
            val previous = rects.lastOrNull()
            if (previous != null && abs(previous.top - rect.top) < max(previous.height, rect.height) * 0.5f) {
                rects[rects.lastIndex] = previous.union(rect)
            } else {
                rects += rect
            }
        }
        if (rects.isEmpty()) return null
        return PageTextSelection(pageIndex, searchText.substring(start, end), rects, start, end)
    }
}

private fun Char.isWordCharacter(): Boolean = isLetterOrDigit() || this == '_'

internal class RawGlyph(
    val op: Int,
    val unicode: String?,
    val trm: Matrix,
    val advance: Float,
    val font: PDFont,
    val colorRgb: Int,
    val renderingMode: RenderingMode,
    val characterSpacing: Float,
    val fontSizeParameter: Float,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val originX: Float get() = trm.translateX
    val originY: Float get() = trm.translateY
    val sizePt: Float get() = hypot(trm.shearX, trm.scaleY)
    private val directionLength: Float get() = hypot(trm.scaleX, trm.shearY).takeIf { it > 0f } ?: 1f
    val dirX: Float get() = trm.scaleX / directionLength
    val dirY: Float get() = trm.shearY / directionLength
    val endX: Float get() = originX + trm.scaleX * advance
    val endY: Float get() = originY + trm.shearY * advance

    fun gapTo(next: RawGlyph): Float = (next.originX - endX) * dirX + (next.originY - endY) * dirY

    fun offsetAcross(next: RawGlyph): Float = (next.originX - originX) * -dirY + (next.originY - originY) * dirX

    fun sameLineAs(next: RawGlyph): Boolean {
        val size = max(sizePt, 1f)
        return dirX * next.dirX + dirY * next.dirY > 0.999f &&
            abs(offsetAcross(next)) < size * 0.3f &&
            gapTo(next) > -size * 0.6f
    }
}

internal class RawBlock(
    val ops: List<Int>,
    val glyphs: List<RawGlyph>,
    val text: String,
    val editable: Boolean
) {
    val id: String get() = "${ops.first()}-${ops.last()}"
}

internal class RawPageText(
    val frame: PageFrame,
    val glyphs: List<RawGlyph>,
    val blocks: List<RawBlock>,
    val advanceAdjustments: Map<Int, Float>
)

internal object PageTextAnalyzer {
    fun analyzeRaw(document: PDDocument, pageIndex: Int): RawPageText {
        val collector = GlyphCollector()
        collector.collect(document, pageIndex)
        val frame = document.getPage(pageIndex).frame()
        return RawPageText(frame, collector.glyphs, buildBlocks(collector.glyphs, collector.adjustments), collector.adjustments)
    }

    fun analyze(document: PDDocument, pageIndex: Int): PageTextLayout {
        val raw = analyzeRaw(document, pageIndex)
        val frame = raw.frame
        val blocks = raw.blocks.mapNotNull { block ->
            if (block.text.isBlank()) return@mapNotNull null
            val first = block.glyphs.first()
            val bounds = block.glyphs.map { frame.userRectToDisplay(it.minX, it.minY, it.maxX, it.maxY) }
                .reduce(NormRect::union)
            TextBlock(
                id = block.id,
                text = block.text,
                bounds = bounds,
                style = FontResolver.describe(first.font, first.colorRgb, first.sizePt),
                editable = block.editable
            )
        }
        val visible = raw.glyphs
        val rects = visible.map { frame.userRectToDisplay(it.minX, it.minY, it.maxX, it.maxY) }
        val text = StringBuilder()
        val mapping = ArrayList<Int>()
        visible.forEachIndexed { index, glyph ->
            val previous = visible.getOrNull(index - 1)
            if (previous != null) {
                val separate = !previous.sameLineAs(glyph) || previous.gapTo(glyph) > previous.sizePt * 0.18f
                if (separate && text.isNotEmpty() && text.last() != ' ') {
                    text.append(' ')
                    mapping += -1
                }
            }
            glyph.unicode.orEmpty().forEach { char ->
                text.append(char)
                mapping += index
            }
        }
        return PageTextLayout(pageIndex, blocks, text.toString(), mapping.toIntArray(), rects)
    }

    private fun buildBlocks(glyphs: List<RawGlyph>, adjustments: Map<Int, Float>): List<RawBlock> {
        val opGroups = glyphs
            .filter { it.op >= 0 && it.renderingMode != RenderingMode.NEITHER }
            .groupBy(RawGlyph::op)
        val grouped = mutableListOf<MutableList<Pair<Int, List<RawGlyph>>>>()
        for ((op, opGlyphs) in opGroups) {
            val current = grouped.lastOrNull()
            if (current != null && continues(current.last().second.last(), opGlyphs.first())) {
                current += op to opGlyphs
            } else {
                grouped += mutableListOf(op to opGlyphs)
            }
        }
        return grouped.map { parts ->
            val all = parts.flatMap { it.second }
            val first = all.first()
            val editable = all.all { it.unicode != null } &&
                first.font !is PDType3Font &&
                first.renderingMode in EDITABLE_MODES &&
                parts.all { adjustments.containsKey(it.first) }
            RawBlock(parts.map { it.first }, all, joinText(all), editable)
        }
    }

    private fun continues(previous: RawGlyph, next: RawGlyph): Boolean {
        if (previous.font !== next.font || previous.colorRgb != next.colorRgb) return false
        if (previous.renderingMode != next.renderingMode) return false
        val size = max(previous.sizePt, 1f)
        if (abs(size - next.sizePt) > size * 0.05f) return false
        return previous.sameLineAs(next) && previous.gapTo(next) < size * 1.2f
    }

    private fun joinText(glyphs: List<RawGlyph>): String = buildString {
        glyphs.forEachIndexed { index, glyph ->
            val previous = glyphs.getOrNull(index - 1)
            val unicode = glyph.unicode.orEmpty()
            if (previous != null && previous.gapTo(glyph) > previous.sizePt * 0.18f &&
                isNotEmpty() && last() != ' ' && !unicode.startsWith(" ")
            ) {
                append(' ')
            }
            append(unicode)
        }
    }

    private val EDITABLE_MODES = setOf(RenderingMode.FILL, RenderingMode.STROKE, RenderingMode.FILL_STROKE)
}

/**
 * Records every glyph together with the index of the top-level page content operator that drew it,
 * plus the text advance of each show-text operator so it can be replaced without shifting later text.
 */
private class GlyphCollector : PDFTextStripper() {
    private var depth = 0
    private var operatorCounter = 0
    private var currentOperator = -1
    val glyphs = ArrayList<RawGlyph>()
    val adjustments = HashMap<Int, Float>()

    init {
        addOperator(SetNonStrokingColor())
        addOperator(SetNonStrokingColorN())
        addOperator(SetNonStrokingColorSpace())
        addOperator(SetNonStrokingDeviceCMYKColor())
        addOperator(SetNonStrokingDeviceGrayColor())
        addOperator(SetNonStrokingDeviceRGBColor())
        sortByPosition = false
    }

    fun collect(document: PDDocument, pageIndex: Int) {
        startPage = pageIndex + 1
        endPage = pageIndex + 1
        getText(document)
    }

    override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        val topLevel = depth == 0
        if (topLevel) currentOperator = operatorCounter++
        val name = operator.name
        val showsText = topLevel && name in SHOW_TEXT_OPERATORS
        val before = if (showsText && (name == "Tj" || name == "TJ")) textMatrix?.clone() else null
        depth++
        try {
            super.processOperator(operator, operands)
        } finally {
            depth--
        }
        if (showsText) recordAdvance(currentOperator, before)
    }

    private fun recordAdvance(operatorIndex: Int, before: Matrix?) {
        val after = textMatrix ?: return
        val start = before ?: textLineMatrix ?: return
        val state = graphicsState.textState
        val dx = after.translateX - start.translateX
        val dy = after.translateY - start.translateY
        val a = start.scaleX
        val b = start.shearY
        val advance = when {
            abs(a) >= abs(b) && abs(a) > EPSILON -> dx / a
            abs(b) > EPSILON -> dy / b
            else -> 0f
        }
        val scale = state.fontSize * state.horizontalScaling / 100f
        adjustments[operatorIndex] = if (abs(scale) > EPSILON) -advance * 1000f / scale else 0f
    }

    override fun showGlyph(
        textRenderingMatrix: Matrix,
        font: PDFont,
        code: Int,
        unicode: String?,
        displacement: Vector
    ) {
        super.showGlyph(textRenderingMatrix, font, code, unicode, displacement)
        val state = graphicsState
        val textState = state.textState
        val descriptor = font.fontDescriptor
        val ascent = ((descriptor?.ascent ?: 0f) / 1000f).takeIf { it in 0.3f..1.3f } ?: 0.78f
        val descent = ((descriptor?.descent ?: 0f) / 1000f).takeIf { it in -0.6f..-0.02f } ?: -0.22f
        val trm = textRenderingMatrix.clone()
        val advance = displacement.x
        val corners = listOf(
            trm.transformPoint(0f, descent),
            trm.transformPoint(advance, descent),
            trm.transformPoint(advance, ascent),
            trm.transformPoint(0f, ascent)
        )
        val color = runCatching { state.nonStrokingColor.toRGB() }.getOrDefault(0)
        val text = unicode ?: runCatching { font.toUnicode(code, GlyphList.getAdobeGlyphList()) }.getOrNull()
        glyphs += RawGlyph(
            op = if (depth == 1) currentOperator else -1,
            unicode = text,
            trm = trm,
            advance = advance,
            font = font,
            colorRgb = color,
            renderingMode = textState.renderingMode,
            characterSpacing = textState.characterSpacing,
            fontSizeParameter = textState.fontSize,
            minX = corners.minOf { it.x },
            minY = corners.minOf { it.y },
            maxX = corners.maxOf { it.x },
            maxY = corners.maxOf { it.y }
        )
    }

    private companion object {
        val SHOW_TEXT_OPERATORS = setOf("Tj", "TJ", "'", "\"")
        const val EPSILON = 1e-6f
    }
}
