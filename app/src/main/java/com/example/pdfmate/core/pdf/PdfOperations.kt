package com.example.pdfmate.core.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.IdentityHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** One page in a reorganized document: which original page it comes from and its final /Rotate. */
/** A page in the reorganized document: which source page it comes from and how much extra rotation to apply. */
data class PageSlot(val sourceIndex: Int, val rotation: Int)

data class InkStroke(
    val points: List<NormPoint>,
    val colorArgb: Int,
    /** Stroke width as a fraction of the page's displayed width. */
    val width: Float,
    val highlighter: Boolean
)

enum class NumberPosition { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }

enum class NumberFormat(val label: String) {
    PLAIN("1"),
    PAGE("Page 1"),
    OF_TOTAL("1 / 12"),
    PAGE_OF_TOTAL("Page 1 of 12");

    fun format(number: Int, total: Int): String = when (this) {
        PLAIN -> "$number"
        PAGE -> "Page $number"
        OF_TOTAL -> "$number / $total"
        PAGE_OF_TOTAL -> "Page $number of $total"
    }
}

data class PageNumberOptions(
    val position: NumberPosition,
    val format: NumberFormat,
    val startAt: Int,
    val sizePt: Float,
    val skipFirst: Boolean,
    val colorArgb: Int
)

data class WatermarkOptions(
    val text: String,
    val opacity: Float,
    val sizePt: Float,
    val diagonal: Boolean,
    val colorArgb: Int,
    val pages: List<Int>?
)

enum class CompressionLevel(val maxDimension: Int, val jpegQuality: Int) {
    LOSSLESS(0, 0),
    BALANCED(1600, 75),
    STRONG(1100, 55)
}

enum class FormFieldKind { TEXT, CHECKBOX, RADIO, CHOICE }

data class FormFieldInfo(
    val name: String,
    val label: String,
    val kind: FormFieldKind,
    val value: String,
    val options: List<String>,
    val readOnly: Boolean,
    val multiline: Boolean
)

private fun Int.red() = (this shr 16 and 0xFF) / 255f
private fun Int.green() = (this shr 8 and 0xFF) / 255f
private fun Int.blue() = (this and 0xFF) / 255f

private fun PDPageContentStream.fillColor(argb: Int) = setNonStrokingColor(argb.red(), argb.green(), argb.blue())
private fun PDPageContentStream.strokeColor(argb: Int) = setStrokingColor(argb.red(), argb.green(), argb.blue())
private fun FloatArray.toMatrix() = Matrix(this[0], this[1], this[2], this[3], this[4], this[5])

private fun PDDocument.appendTo(page: PDPage, block: (PDPageContentStream) -> Unit) {
    PDPageContentStream(this, page, AppendMode.APPEND, true, true).use(block)
}

/** Copies inherited attributes onto the page so it can be moved safely within the page tree. */
private fun PDPage.materializeInheritedAttributes() {
    resources = resources ?: PDResources()
    val media = mediaBox
    val crop = cropBox
    val rotate = rotation
    mediaBox = media
    cropBox = crop
    rotation = rotate
}

fun normalizeRotation(value: Int): Int = ((value % 360) + 360) % 360

/** Reorders, duplicates, removes and rotates pages in place according to [slots]. */
fun PDDocument.reorganize(slots: List<PageSlot>) {
    require(slots.isNotEmpty()) { "A PDF needs at least one page." }
    val original = (0 until numberOfPages).map(::getPage)
    require(slots.all { it.sourceIndex in original.indices }) { "Page does not exist." }
    original.forEach { it.materializeInheritedAttributes() }
    val used = HashSet<Int>()
    val ordered = slots.map { slot ->
        val source = original[slot.sourceIndex]
        val page = if (used.add(slot.sourceIndex)) {
            source
        } else {
            PDPage(COSDictionary(source.cosObject)).also { it.cosObject.removeItem(COSName.ANNOTS) }
        }
        page to normalizeRotation(page.rotation + slot.rotation)
    }
    while (numberOfPages > 0) removePage(numberOfPages - 1)
    ordered.forEach { (page, rotation) ->
        page.rotation = rotation
        addPage(page)
    }
}

fun PDDocument.rotatePages(pageIndexes: Collection<Int>, degrees: Int) {
    pageIndexes.forEach { index ->
        val page = getPage(index)
        page.rotation = normalizeRotation(page.rotation + degrees)
    }
}

/** Crops each page to [rect], given in that page's normalized display coordinates. */
fun PDDocument.cropPages(pageIndexes: Collection<Int>, rect: NormRect) {
    require(rect.width > 0.02f && rect.height > 0.02f) { "The crop area is too small." }
    pageIndexes.forEach { index ->
        val page = getPage(index)
        val box = page.frame().displayRectToUser(rect)
        page.cropBox = PDRectangle(box[0], box[1], box[2] - box[0], box[3] - box[1])
    }
}

fun PDDocument.setMetadata(title: String, author: String, subject: String, keywords: String) {
    documentInformation.title = title.trim().ifEmpty { null }
    documentInformation.author = author.trim().ifEmpty { null }
    documentInformation.subject = subject.trim().ifEmpty { null }
    documentInformation.keywords = keywords.trim().ifEmpty { null }
}

fun PDDocument.addWatermark(options: WatermarkOptions) {
    require(options.text.isNotBlank()) { "Enter watermark text." }
    val text = options.text.trim()
    val style = TextStyleHint("Helvetica", FontFamilyHint.SANS, true, false, options.colorArgb, options.sizePt)
    val font = FontResolver(this).resolve(null, text, style).font
    val width = font.getStringWidth(text) / 1000f * options.sizePt
    val state = PDExtendedGraphicsState().apply {
        nonStrokingAlphaConstant = options.opacity.coerceIn(0.05f, 1f)
        strokingAlphaConstant = options.opacity.coerceIn(0.05f, 1f)
    }
    val targets = options.pages?.map { it - 1 } ?: (0 until numberOfPages).toList()
    targets.forEach { index ->
        val page = getPage(index)
        val frame = page.frame()
        appendTo(page) { stream ->
            stream.saveGraphicsState()
            stream.setGraphicsStateParameters(state)
            stream.transform(frame.uprightMatrix(NormPoint(0.5f, 0.5f)).toMatrix())
            if (options.diagonal) {
                val angle = kotlin.math.atan2(frame.displayHeight, frame.displayWidth).toDouble()
                stream.transform(Matrix.getRotateInstance(angle, 0f, 0f))
            }
            stream.beginText()
            stream.setFont(font, options.sizePt)
            stream.fillColor(options.colorArgb)
            stream.newLineAtOffset(-width / 2f, -options.sizePt * 0.35f)
            stream.showText(text)
            stream.endText()
            stream.restoreGraphicsState()
        }
    }
}

fun PDDocument.addPageNumbers(options: PageNumberOptions) {
    require(options.startAt >= 0) { "Numbering must start at 0 or higher." }
    val total = numberOfPages
    val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
    val margin = 28f
    val firstNumbered = if (options.skipFirst) 1 else 0
    val lastNumber = options.startAt + (total - 1 - firstNumbered)
    for (index in firstNumbered until total) {
        val label = options.format.format(options.startAt + index - firstNumbered, lastNumber)
        val page = getPage(index)
        val frame = page.frame()
        val width = font.getStringWidth(label) / 1000f * options.sizePt
        val x = when (options.position) {
            NumberPosition.TOP_LEFT, NumberPosition.BOTTOM_LEFT -> margin
            NumberPosition.TOP_CENTER, NumberPosition.BOTTOM_CENTER -> (frame.displayWidth - width) / 2f
            else -> frame.displayWidth - margin - width
        }
        val y = when (options.position) {
            NumberPosition.TOP_LEFT, NumberPosition.TOP_CENTER, NumberPosition.TOP_RIGHT -> -(margin * 0.8f + options.sizePt)
            else -> -(frame.displayHeight - margin * 0.8f)
        }
        appendTo(page) { stream ->
            stream.saveGraphicsState()
            stream.transform(frame.uprightMatrix(NormPoint(0f, 0f)).toMatrix())
            stream.beginText()
            stream.setFont(font, options.sizePt)
            stream.fillColor(options.colorArgb)
            stream.newLineAtOffset(x, y)
            stream.showText(label)
            stream.endText()
            stream.restoreGraphicsState()
        }
    }
}

/** Draws freehand strokes permanently onto the page. Highlighter strokes multiply like a real marker. */
fun PDDocument.drawInk(pageIndex: Int, strokes: List<InkStroke>) {
    if (strokes.isEmpty()) return
    val page = getPage(pageIndex)
    val frame = page.frame()
    appendTo(page) { stream ->
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            stream.saveGraphicsState()
            if (stroke.highlighter) {
                stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply {
                    strokingAlphaConstant = 0.4f
                    blendMode = BlendMode.MULTIPLY
                })
            }
            stream.strokeColor(stroke.colorArgb)
            stream.setLineWidth(stroke.width * frame.displayWidth)
            stream.setLineCapStyle(if (stroke.highlighter) 2 else 1)
            stream.setLineJoinStyle(1)
            val points = stroke.points.map(frame::toUser)
            stream.moveTo(points.first().x, points.first().y)
            if (points.size == 1) {
                stream.lineTo(points.first().x + 0.01f, points.first().y)
            } else {
                for (index in 1 until points.size) {
                    val previous = points[index - 1]
                    val current = points[index]
                    if (index == points.lastIndex) {
                        stream.lineTo(current.x, current.y)
                    } else {
                        val midX = (previous.x + current.x) / 2f
                        val midY = (previous.y + current.y) / 2f
                        stream.curveTo(previous.x, previous.y, previous.x, previous.y, midX, midY)
                    }
                }
            }
            stream.stroke()
            stream.restoreGraphicsState()
        }
    }
}

fun PDDocument.highlightAreas(pageIndex: Int, rects: List<NormRect>, colorArgb: Int) {
    if (rects.isEmpty()) return
    val page = getPage(pageIndex)
    val frame = page.frame()
    appendTo(page) { stream ->
        stream.saveGraphicsState()
        stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply {
            nonStrokingAlphaConstant = 0.45f
            blendMode = BlendMode.MULTIPLY
        })
        stream.fillColor(colorArgb)
        rects.forEach { rect ->
            val box = frame.displayRectToUser(rect)
            stream.addRect(box[0], box[1], box[2] - box[0], box[3] - box[1])
        }
        stream.fill()
        stream.restoreGraphicsState()
    }
}

/** Places a bitmap (e.g. a signature) in the given normalized display rectangle, upright. */
fun PDDocument.placeImage(pageIndex: Int, bitmap: Bitmap, rect: NormRect) {
    val page = getPage(pageIndex)
    val frame = page.frame()
    val image = LosslessFactory.createFromImage(this, bitmap)
    val width = rect.width * frame.displayWidth
    val height = rect.height * frame.displayHeight
    appendTo(page) { stream ->
        stream.saveGraphicsState()
        stream.transform(frame.uprightMatrix(NormPoint(rect.left, rect.bottom)).toMatrix())
        stream.drawImage(image, 0f, 0f, width, height)
        stream.restoreGraphicsState()
    }
}

fun PDDocument.formFields(): List<FormFieldInfo> {
    val form = documentCatalog.acroForm ?: return emptyList()
    return form.fieldTree.mapNotNull { field ->
        val label = field.alternateFieldName?.takeIf(String::isNotBlank) ?: field.partialName ?: field.fullyQualifiedName
        when (field) {
            is PDTextField -> FormFieldInfo(field.fullyQualifiedName, label, FormFieldKind.TEXT, field.value.orEmpty(), emptyList(), field.isReadOnly, field.isMultiline)
            is PDCheckBox -> FormFieldInfo(field.fullyQualifiedName, label, FormFieldKind.CHECKBOX, if (field.isChecked) "true" else "false", emptyList(), field.isReadOnly, false)
            is PDRadioButton -> FormFieldInfo(field.fullyQualifiedName, label, FormFieldKind.RADIO, field.value.orEmpty(), field.onValues.toList().filter { it != "Off" }, field.isReadOnly, false)
            is PDChoice -> FormFieldInfo(field.fullyQualifiedName, label, FormFieldKind.CHOICE, field.value.firstOrNull().orEmpty(), field.optionsDisplayValues, field.isReadOnly, false)
            else -> null
        }
    }
}

fun PDDocument.fillForm(values: Map<String, String>, flatten: Boolean) {
    val form = documentCatalog.acroForm ?: throw IllegalArgumentException("This PDF has no fillable form.")
    require(!form.hasXFA()) { "This PDF uses an XFA form, which isn't supported." }
    values.forEach { (name, value) ->
        val field = form.getField(name) ?: return@forEach
        if (field.isReadOnly) return@forEach
        when (field) {
            is PDCheckBox -> if (value == "true") field.check() else field.unCheck()
            is PDRadioButton -> if (value.isNotEmpty()) field.setValue(value)
            is PDChoice -> {
                val index = field.optionsDisplayValues.indexOf(value)
                val export = field.optionsExportValues.getOrNull(index) ?: value
                if (value.isNotEmpty()) field.setValue(export)
            }
            is PDTextField -> field.setValue(value)
        }
    }
    form.refreshAppearances()
    if (flatten) form.flatten()
}

/** Re-encodes uncompressed streams and, for lossy levels, recompresses large photos. Returns images changed. */
fun PDDocument.compress(level: CompressionLevel): Int {
    document.objects.forEach { cosObject ->
        val stream = cosObject.`object` as? COSStream ?: return@forEach
        if (stream.filters != null || stream.length < 1024) return@forEach
        if (stream.getCOSName(COSName.TYPE) == COSName.METADATA) return@forEach
        runCatching {
            val data = stream.createInputStream().use { it.readBytes() }
            stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(data) }
        }
    }
    if (level == CompressionLevel.LOSSLESS) return 0
    val visited = IdentityHashMap<COSStream, Boolean>()
    var changed = 0
    pages.forEach { page ->
        page.resources?.let { changed += recompressImages(it, level, visited, 0) }
    }
    return changed
}

private fun PDDocument.recompressImages(
    resources: PDResources,
    level: CompressionLevel,
    visited: IdentityHashMap<COSStream, Boolean>,
    depth: Int
): Int {
    if (depth > 8) return 0
    var changed = 0
    resources.xObjectNames.forEach { name ->
        val xObject = runCatching { resources.getXObject(name) }.getOrNull() ?: return@forEach
        val stream = xObject.cosObject
        if (visited.put(stream, true) != null) return@forEach
        when (xObject) {
            is PDImageXObject -> if (runCatching { recompress(xObject, level) }.getOrDefault(false)) changed++
            is PDFormXObject -> xObject.resources?.let { changed += recompressImages(it, level, visited, depth + 1) }
        }
    }
    return changed
}

private fun recompress(image: PDImageXObject, level: CompressionLevel): Boolean {
    val stream = image.cosObject
    if (image.isStencil || image.bitsPerComponent == 1) return false
    if (stream.containsKey(COSName.SMASK) || stream.containsKey(COSName.MASK)) return false
    val filters = image.cosObject.filters?.toString().orEmpty()
    if ("JBIG2" in filters || "CCITT" in filters) return false
    val originalLength = stream.length
    if (originalLength < 40 * 1024) return false
    val largest = max(image.width, image.height)
    val subsampling = max(1, largest / (level.maxDimension * 2))
    var bitmap = image.getImage(Rect(0, 0, image.width, image.height), subsampling) ?: return false
    val scale = level.maxDimension.toFloat() / max(bitmap.width, bitmap.height)
    if (scale < 1f) {
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        bitmap = scaled
    }
    val newWidth = bitmap.width
    val newHeight = bitmap.height
    val encoded = ByteArrayOutputStream()
    try {
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, encoded)) return false
    } finally {
        bitmap.recycle()
    }
    if (encoded.size() >= originalLength * 0.9) return false
    stream.createRawOutputStream().use { it.write(encoded.toByteArray()) }
    stream.setItem(COSName.FILTER, COSName.DCT_DECODE)
    stream.removeItem(COSName.DECODE_PARMS)
    stream.removeItem(COSName.DECODE)
    stream.setItem(COSName.COLORSPACE, COSName.DEVICERGB)
    stream.setInt(COSName.WIDTH, newWidth)
    stream.setInt(COSName.HEIGHT, newHeight)
    stream.setInt(COSName.BITS_PER_COMPONENT, 8)
    return true
}

object PdfFiles {
    /** Merges [sources] (in order) into [output]. */
    fun merge(sources: List<File>, output: File) {
        require(sources.size >= 2) { "Choose at least two PDFs to merge." }
        PDFMergerUtility().apply {
            destinationFileName = output.absolutePath
            sources.forEach(::addSource)
        }.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())
        val expected = sources.sumOf { file -> PdfIo.load(file).use { it.numberOfPages } }
        PdfIo.verify(output, expected)
    }

    /** Writes one PDF containing [pages] (1-based) of [source]. */
    fun extract(source: File, pages: List<Int>, output: File) {
        require(pages.isNotEmpty()) { "Choose at least one page." }
        PdfIo.load(source).use { document ->
            PDDocument().use { result ->
                pages.forEach { number -> result.importPage(document.getPage(number - 1)) }
                PdfIo.save(result, output)
            }
        }
    }

    /** Splits [source] into the given groups of 1-based pages and zips the parts. */
    fun splitToZip(source: File, baseName: String, groups: List<List<Int>>, workDir: File, output: File) {
        require(groups.isNotEmpty()) { "Enter at least one page range." }
        ZipOutputStream(output.outputStream().buffered()).use { archive ->
            groups.forEachIndexed { index, pages ->
                val part = File(workDir, "part_$index.pdf")
                try {
                    extract(source, pages, part)
                    val label = if (pages.size == 1) "${pages.first()}" else "${pages.first()}-${pages.last()}"
                    archive.putNextEntry(ZipEntry("${baseName}_${index + 1}_p$label.pdf"))
                    part.inputStream().use { it.copyTo(archive) }
                    archive.closeEntry()
                } finally {
                    part.delete()
                }
            }
        }
        check(output.length() > 0) { "The ZIP file could not be written." }
    }

    /** Appends all pages from [other] to the end of [target]. */
    fun appendInto(target: File, other: File, output: File) = merge(listOf(target, other), output)
}
