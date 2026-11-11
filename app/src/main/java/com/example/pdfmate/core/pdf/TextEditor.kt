package com.example.pdfmate.core.pdf

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDSimpleFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.font.PDVectorFont
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayInputStream
import java.io.File

/** Which font ended up being used for an edit, so the UI can be honest about substitutions. */
data class TextEditOutcome(val fontLabel: String, val exactFont: Boolean)

class TextEditException(message: String) : IllegalStateException(message)

object TextEditor {
    /**
     * Replaces a text block in place: the original show-text operators are swapped for pure advances
     * (so following text keeps its position) and the new text is drawn at the same baseline with the
     * same font, size, color and spacing whenever the embedded font contains the needed glyphs.
     */
    fun replaceText(
        document: PDDocument,
        pageIndex: Int,
        blockId: String,
        expectedText: String,
        newText: String
    ): TextEditOutcome {
        val raw = PageTextAnalyzer.analyzeRaw(document, pageIndex)
        val block = raw.blocks.firstOrNull { it.id == blockId && it.text == expectedText }
            ?: throw TextEditException("This text has changed. Tap it again to edit.")
        if (!block.editable) throw TextEditException("This text can't be edited safely.")
        val first = block.glyphs.first()
        val content = newText.trimEnd()
        val resolved = if (content.isBlank()) {
            null
        } else {
            FontResolver(document).resolve(first.font, content, FontResolver.describe(first.font, first.colorRgb, first.sizePt))
        }

        val page = document.getPage(pageIndex)
        val parser = PDFStreamParser(page)
        parser.parse()
        val replaced = block.ops.toSet()
        val output = ArrayList<Any>(parser.tokens.size)
        val operands = ArrayList<Any>()
        var operatorIndex = 0
        for (token in parser.tokens) {
            if (token !is Operator) {
                operands += token
                continue
            }
            val index = operatorIndex++
            if (index in replaced) {
                when (token.name) {
                    "'" -> output += Operator.getOperator("T*")
                    "\"" -> {
                        if (operands.size >= 2) {
                            output += operands[0]
                            output += Operator.getOperator("Tw")
                            output += operands[1]
                            output += Operator.getOperator("Tc")
                        }
                        output += Operator.getOperator("T*")
                    }
                }
                output.add(COSArray().apply { add(COSFloat(raw.advanceAdjustments.getValue(index))) })
                output += Operator.getOperator("TJ")
            } else {
                output.addAll(operands)
                output += token
            }
            operands.clear()
        }
        output.addAll(operands)

        val stream = PDStream(document)
        stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(output) }
        page.setContents(stream)

        if (resolved == null) return TextEditOutcome(FontResolver.cleanName(first.font), true)
        PDPageContentStream(document, page, AppendMode.APPEND, true, true).use { stream ->
            stream.beginText()
            stream.setFont(resolved.font, 1f)
            stream.setTextMatrix(first.trm.clone())
            if (first.characterSpacing != 0f && first.fontSizeParameter != 0f) {
                stream.setCharacterSpacing(first.characterSpacing / first.fontSizeParameter)
            }
            applyColor(stream, first.colorRgb, first.renderingMode, first.sizePt)
            content.lines().forEachIndexed { line, text ->
                if (line > 0) stream.newLineAtOffset(0f, -LINE_HEIGHT)
                if (text.isNotEmpty()) stream.showText(text)
            }
            stream.endText()
        }
        return TextEditOutcome(resolved.label, resolved.exact)
    }

    /** Adds new upright text with its top-left corner at [origin]. */
    fun addText(
        document: PDDocument,
        pageIndex: Int,
        origin: NormPoint,
        text: String,
        style: TextStyleHint
    ): TextEditOutcome {
        val content = text.trimEnd()
        if (content.isBlank()) throw TextEditException("Type some text first.")
        val resolved = FontResolver(document).resolve(null, content, style)
        val page = document.getPage(pageIndex)
        val matrix = page.frame().uprightMatrix(origin)
        PDPageContentStream(document, page, AppendMode.APPEND, true, true).use { stream ->
            stream.saveGraphicsState()
            stream.transform(Matrix(matrix[0], matrix[1], matrix[2], matrix[3], matrix[4], matrix[5]))
            stream.beginText()
            stream.setFont(resolved.font, style.sizePt)
            applyColor(stream, style.colorArgb, RenderingMode.FILL, style.sizePt)
            stream.newLineAtOffset(0f, -style.sizePt * 0.92f)
            content.lines().forEachIndexed { line, value ->
                if (line > 0) stream.newLineAtOffset(0f, -style.sizePt * LINE_HEIGHT)
                if (value.isNotEmpty()) stream.showText(value)
            }
            stream.endText()
            stream.restoreGraphicsState()
        }
        return TextEditOutcome(resolved.label, resolved.exact)
    }

    private fun applyColor(stream: PDPageContentStream, rgb: Int, mode: RenderingMode, sizePt: Float) {
        val red = (rgb shr 16 and 0xFF) / 255f
        val green = (rgb shr 8 and 0xFF) / 255f
        val blue = (rgb and 0xFF) / 255f
        stream.setNonStrokingColor(red, green, blue)
        if (mode != RenderingMode.FILL) {
            stream.setStrokingColor(red, green, blue)
            stream.setLineWidth(sizePt * 0.03f)
            stream.setRenderingMode(mode)
        }
    }

    private const val LINE_HEIGHT = 1.2f
}

internal class ResolvedFont(val font: PDFont, val label: String, val exact: Boolean)

/** Picks the embedded font when it can draw the text, otherwise the closest standard or system font. */
internal class FontResolver(private val document: PDDocument) {
    fun resolve(original: PDFont?, text: String, style: TextStyleHint): ResolvedFont {
        if (original != null && canRender(original, text)) {
            return ResolvedFont(original, cleanName(original), true)
        }
        val standard = standardFont(style)
        if (canRender(standard, text)) {
            return ResolvedFont(standard, standard.name.replace('-', ' '), original == null)
        }
        for (name in systemCandidates(style)) {
            val file = File(SYSTEM_FONTS, name)
            if (!file.isFile) continue
            val font = runCatching { PDType0Font.load(document, file) }.getOrNull() ?: continue
            if (canRender(font, text)) {
                return ResolvedFont(font, name.substringBeforeLast('.').replace('-', ' '), false)
            }
        }
        throw TextEditException("No available font can display some of these characters.")
    }

    private fun standardFont(style: TextStyleHint): PDType1Font = when (style.family) {
        FontFamilyHint.SERIF -> when {
            style.bold && style.italic -> PDType1Font.TIMES_BOLD_ITALIC
            style.bold -> PDType1Font.TIMES_BOLD
            style.italic -> PDType1Font.TIMES_ITALIC
            else -> PDType1Font.TIMES_ROMAN
        }
        FontFamilyHint.MONO -> when {
            style.bold && style.italic -> PDType1Font.COURIER_BOLD_OBLIQUE
            style.bold -> PDType1Font.COURIER_BOLD
            style.italic -> PDType1Font.COURIER_OBLIQUE
            else -> PDType1Font.COURIER
        }
        FontFamilyHint.SANS -> when {
            style.bold && style.italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            style.bold -> PDType1Font.HELVETICA_BOLD
            style.italic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }
    }

    private fun systemCandidates(style: TextStyleHint): List<String> {
        val suffix = when {
            style.bold && style.italic -> "BoldItalic"
            style.bold -> "Bold"
            style.italic -> "Italic"
            else -> "Regular"
        }
        val primary = when (style.family) {
            FontFamilyHint.SERIF -> listOf("NotoSerif-$suffix.ttf", "NotoSerif-Regular.ttf", "DroidSerif-$suffix.ttf", "DroidSerif-Regular.ttf")
            FontFamilyHint.MONO -> listOf("DroidSansMono.ttf", "CutiveMono.ttf")
            FontFamilyHint.SANS -> emptyList()
        }
        return primary + listOf(
            "Roboto-$suffix.ttf",
            "Roboto-Regular.ttf",
            "NotoSans-$suffix.ttf",
            "NotoSans-Regular.ttf",
            "DroidSans.ttf",
            "NotoNaskhArabic-Regular.ttf",
            "NotoSansDevanagari-Regular.ttf",
            "NotoSansHebrew-Regular.ttf",
            "NotoSansThai-Regular.ttf"
        )
    }

    companion object {
        private val SYSTEM_FONTS = File("/system/fonts")
        private val SUBSET_PREFIX = Regex("^[A-Z]{6}\\+")

        fun cleanName(font: PDFont): String =
            (font.name ?: "Embedded font").replace(SUBSET_PREFIX, "").replace(Regex("[-_,]"), " ").trim()

        fun describe(font: PDFont, rgb: Int, sizePt: Float): TextStyleHint {
            val name = cleanName(font)
            val lower = name.lowercase()
            val descriptor = font.fontDescriptor
            val bold = descriptor?.isForceBold == true || (descriptor?.fontWeight ?: 0f) >= 600f ||
                BOLD_WORDS.any(lower::contains)
            val italic = descriptor?.isItalic == true || (descriptor?.italicAngle ?: 0f) != 0f ||
                lower.contains("italic") || lower.contains("oblique")
            val family = when {
                descriptor?.isFixedPitch == true || MONO_WORDS.any(lower::contains) -> FontFamilyHint.MONO
                SERIF_WORDS.any(lower::contains) && !lower.contains("sans") -> FontFamilyHint.SERIF
                descriptor?.isSerif == true && !lower.contains("sans") -> FontFamilyHint.SERIF
                else -> FontFamilyHint.SANS
            }
            return TextStyleHint(name, family, bold, italic, 0xFF000000.toInt() or (rgb and 0xFFFFFF), sizePt)
        }

        fun canRender(font: PDFont, text: String): Boolean {
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                index += Character.charCount(codePoint)
                if (codePoint == '\n'.code || codePoint == '\r'.code) continue
                if (!canRenderCodePoint(font, String(Character.toChars(codePoint)), Character.isWhitespace(codePoint))) {
                    return false
                }
            }
            return true
        }

        private fun canRenderCodePoint(font: PDFont, value: String, whitespace: Boolean): Boolean = try {
            val bytes = font.encode(value)
            when {
                bytes.isEmpty() -> false
                whitespace -> true
                font.isStandard14 -> true
                font is PDVectorFont -> {
                    val code = font.readCode(ByteArrayInputStream(bytes))
                    font.hasGlyph(code) && !font.getPath(code).isEmpty
                }
                font is PDSimpleFont -> {
                    val name = font.encoding?.getName(bytes[0].toInt() and 0xFF)
                    name != null && font.hasGlyph(name) && !font.getPath(name).isEmpty
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }

        private val BOLD_WORDS = listOf("bold", "black", "heavy", "semibold", "demi", "extrabold")
        private val MONO_WORDS = listOf("courier", "mono", "consol", "menlo", "typewriter")
        private val SERIF_WORDS = listOf("times", "serif", "georgia", "garamond", "roman", "cambria", "book", "palatino", "minion")
    }
}
