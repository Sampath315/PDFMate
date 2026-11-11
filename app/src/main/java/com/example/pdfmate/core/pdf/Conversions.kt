package com.example.pdfmate.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.example.pdfmate.core.files.FileStore
import com.example.pdfmate.core.render.PageRenderer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ImagePageSize(val label: String) { FIT("Fit image"), A4("A4"), LETTER("Letter") }

data class ImageSource(val uri: Uri, val rotationDegrees: Int = 0)

private data class OcrLine(val text: String, val bounds: NormRect)

object Conversions {
    /** Adds an invisible, searchable text layer to pages that have no text. Returns pages processed. */
    suspend fun makeSearchable(
        working: File,
        output: File,
        onProgress: (done: Int, total: Int) -> Unit
    ): Int {
        val targets = PdfIo.load(working).use { document ->
            val stripper = PDFTextStripper()
            (0 until document.numberOfPages).filter { index ->
                stripper.startPage = index + 1
                stripper.endPage = index + 1
                stripper.getText(document).isBlank()
            }
        }
        if (targets.isEmpty()) throw IllegalStateException("This PDF is already searchable.")
        val recognized = HashMap<Int, List<OcrLine>>()
        val renderer = PageRenderer.open(working)
        try {
            targets.forEachIndexed { position, index ->
                coroutineContext.ensureActive()
                val size = renderer.pageSizes[index]
                val width = (2200 * size.aspectRatio.coerceAtMost(1f)).roundToInt()
                val bitmap = renderer.render(index, width)
                try {
                    recognized[index] = recognize(bitmap)
                } finally {
                    bitmap.recycle()
                }
                onProgress(position + 1, targets.size)
            }
        } finally {
            renderer.close()
        }
        PdfIo.load(working).use { document ->
            val font = PDType1Font.HELVETICA
            val encodable = HashMap<Char, Boolean>()
            recognized.forEach { (index, lines) ->
                if (lines.isEmpty()) return@forEach
                val page = document.getPage(index)
                val frame = page.frame()
                PDPageContentStream(document, page, AppendMode.APPEND, true, true).use { stream ->
                    lines.forEach { line ->
                        val text = line.text.map { char ->
                            if (encodable.getOrPut(char) { FontResolver.canRender(font, char.toString()) }) char else '?'
                        }.joinToString("")
                        val heightPt = line.bounds.height * frame.displayHeight
                        val widthPt = line.bounds.width * frame.displayWidth
                        val size = (heightPt / 0.93f).coerceAtLeast(1f)
                        val textWidth = font.getStringWidth(text) / 1000f * size
                        if (textWidth <= 0f || widthPt <= 0f) return@forEach
                        val baseline = NormPoint(line.bounds.left, line.bounds.bottom - 0.2f * size / frame.displayHeight)
                        val m = frame.uprightMatrix(baseline)
                        stream.saveGraphicsState()
                        stream.transform(Matrix(m[0], m[1], m[2], m[3], m[4], m[5]))
                        stream.beginText()
                        stream.setFont(font, size)
                        stream.setRenderingMode(RenderingMode.NEITHER)
                        stream.setHorizontalScaling(widthPt / textWidth * 100f)
                        stream.showText(text)
                        stream.endText()
                        stream.restoreGraphicsState()
                    }
                }
            }
            PdfIo.save(document, output)
        }
        return targets.size
    }

    private suspend fun recognize(bitmap: Bitmap): List<OcrLine> = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
            recognizer.close()
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                val width = bitmap.width.toFloat()
                val height = bitmap.height.toFloat()
                val lines = task.result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    val text = line.text.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
                    OcrLine(text, NormRect(box.left / width, box.top / height, box.right / width, box.bottom / height))
                }
                continuation.resume(lines)
            } else {
                continuation.resumeWithException(task.exception ?: IOException("Text recognition failed."))
            }
        }
    }

    /** Builds a PDF from images, honoring EXIF orientation plus any extra rotation chosen by the user. */
    suspend fun imagesToPdf(
        context: Context,
        images: List<ImageSource>,
        pageSize: ImagePageSize,
        margins: Boolean,
        workDir: File,
        output: File
    ) {
        require(images.isNotEmpty()) { "Choose at least one image." }
        PDDocument().use { document ->
            images.forEachIndexed { position, source ->
                coroutineContext.ensureActive()
                val file = File(workDir, "image_$position")
                try {
                    FileStore.copyIn(context, source.uri, file)
                    val rotation = normalizeRotation(exifRotation(file) + source.rotationDegrees)
                    val image = loadImage(document, file)
                    addImagePage(document, image, rotation, pageSize, margins)
                } finally {
                    file.delete()
                }
            }
            PdfIo.save(document, output)
        }
    }

    private fun loadImage(document: PDDocument, file: File): PDImageXObject {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("One of the images can't be read.")
        if (bounds.outMimeType == "image/jpeg") {
            return file.inputStream().use { JPEGFactory.createFromStream(document, it) }
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("One of the images can't be read.")
        return try {
            if (bitmap.hasAlpha()) LosslessFactory.createFromImage(document, bitmap)
            else JPEGFactory.createFromImage(document, bitmap, 0.9f)
        } finally {
            bitmap.recycle()
        }
    }

    private fun exifRotation(file: File): Int = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun addImagePage(
        document: PDDocument,
        image: PDImageXObject,
        rotation: Int,
        pageSize: ImagePageSize,
        margins: Boolean
    ) {
        val sideways = rotation % 180 != 0
        val imageWidth = (if (sideways) image.height else image.width).toFloat()
        val imageHeight = (if (sideways) image.width else image.height).toFloat()
        val landscape = imageWidth > imageHeight
        val (pageWidth, pageHeight) = when (pageSize) {
            ImagePageSize.FIT -> {
                val scale = FIT_LONG_SIDE / max(imageWidth, imageHeight)
                imageWidth * scale to imageHeight * scale
            }
            ImagePageSize.A4 -> if (landscape) 842f to 595f else 595f to 842f
            ImagePageSize.LETTER -> if (landscape) 792f to 612f else 612f to 792f
        }
        val margin = if (margins) 28f else 0f
        val boxWidth = pageWidth - margin * 2
        val boxHeight = pageHeight - margin * 2
        val scale = min(boxWidth / imageWidth, boxHeight / imageHeight)
        val drawWidth = imageWidth * scale
        val drawHeight = imageHeight * scale
        val x = (pageWidth - drawWidth) / 2f
        val y = (pageHeight - drawHeight) / 2f
        val page = PDPage(PDRectangle(pageWidth, pageHeight))
        document.addPage(page)
        val matrix = when (rotation) {
            90 -> Matrix(0f, -drawHeight, drawWidth, 0f, x, y + drawHeight)
            180 -> Matrix(-drawWidth, 0f, 0f, -drawHeight, x + drawWidth, y + drawHeight)
            270 -> Matrix(0f, drawHeight, -drawWidth, 0f, x + drawWidth, y)
            else -> Matrix(drawWidth, 0f, 0f, drawHeight, x, y)
        }
        PDPageContentStream(document, page).use { stream -> stream.drawImage(image, matrix) }
    }

    /** Renders the selected pages (0-based) as JPEGs into a ZIP archive. */
    suspend fun pagesToImagesZip(
        working: File,
        baseName: String,
        pages: List<Int>,
        dpi: Int,
        output: File,
        onProgress: (done: Int, total: Int) -> Unit
    ) {
        require(pages.isNotEmpty()) { "Choose at least one page." }
        val renderer = PageRenderer.open(working)
        try {
            ZipOutputStream(output.outputStream().buffered()).use { archive ->
                pages.forEachIndexed { position, index ->
                    coroutineContext.ensureActive()
                    val width = (renderer.pageSizes[index].width / 72f * dpi).roundToInt()
                    val bitmap = renderer.render(index, width)
                    try {
                        archive.putNextEntry(ZipEntry("%s_page_%03d.jpg".format(baseName, index + 1)))
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, archive)) { "Couldn't encode page ${index + 1}." }
                        archive.closeEntry()
                    } finally {
                        bitmap.recycle()
                    }
                    onProgress(position + 1, pages.size)
                }
            }
        } finally {
            renderer.close()
        }
        check(output.length() > 0) { "The ZIP file could not be written." }
    }

    private const val MAX_IMAGE_SIDE = 4000
    private const val FIT_LONG_SIDE = 842f
}
