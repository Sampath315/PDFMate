package com.example.pdfmate.core.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.pdfmate.core.pdf.PdfIo
import com.example.pdfmate.core.pdf.frame
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Page size in points, as displayed (crop box and rotation applied). */
data class PageSize(val width: Float, val height: Float) {
    val aspectRatio: Float get() = (width / height).takeIf { it.isFinite() && it > 0f } ?: 0.7071f
}

/**
 * Renders pages of one immutable PDF file. Uses the platform renderer (fast, PDFium) and falls back to
 * PDFBox for files PDFium refuses to open.
 */
class PageRenderer private constructor(
    private val descriptor: ParcelFileDescriptor?,
    private val platform: PdfRenderer?,
    private val fallback: PDDocument?,
    val pageSizes: List<PageSize>
) {
    private val mutex = Mutex()
    private var closed = false
    val pageCount: Int get() = pageSizes.size

    suspend fun render(index: Int, targetWidthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Renderer closed" }
            val size = pageSizes[index]
            var width = targetWidthPx.coerceIn(16, MAX_SIDE)
            var height = (width / size.aspectRatio).roundToInt().coerceAtLeast(16)
            if (height > MAX_SIDE) {
                height = MAX_SIDE
                width = (height * size.aspectRatio).roundToInt().coerceAtLeast(16)
            }
            val pixels = width.toLong() * height
            if (pixels > MAX_PIXELS) {
                val shrink = sqrt(MAX_PIXELS.toDouble() / pixels)
                width = (width * shrink).roundToInt()
                height = (height * shrink).roundToInt()
            }
            if (platform != null) {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                platform.openPage(index).use { page ->
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
                bitmap
            } else {
                val document = checkNotNull(fallback)
                PDFRenderer(document).renderImage(index, width / max(size.width, 1f), ImageType.RGB)
            }
        }
    }

    suspend fun close() {
        mutex.withLock {
            if (closed) return
            closed = true
            runCatching { platform?.close() }
            runCatching { descriptor?.close() }
            runCatching { fallback?.close() }
        }
    }

    companion object {
        private const val MAX_SIDE = 4096
        private const val MAX_PIXELS = 12_000_000L

        fun open(file: File): PageRenderer {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val platform = try {
                PdfRenderer(descriptor)
            } catch (_: Exception) {
                null
            }
            if (platform != null) {
                val sizes = (0 until platform.pageCount).map { index ->
                    platform.openPage(index).use { PageSize(it.width.toFloat(), it.height.toFloat()) }
                }
                return PageRenderer(descriptor, platform, null, sizes)
            }
            descriptor.close()
            val document = PdfIo.load(file)
            val sizes = document.pages.map { page ->
                val frame = page.frame()
                PageSize(frame.displayWidth, frame.displayHeight)
            }
            return PageRenderer(null, null, document, sizes)
        }
    }
}
