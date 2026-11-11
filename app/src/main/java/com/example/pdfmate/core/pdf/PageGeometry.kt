package com.example.pdfmate.core.pdf

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A point in normalized page display space: (0,0) is the top-left of the page as shown on screen. */
data class NormPoint(val x: Float, val y: Float)

/** A rectangle in normalized page display space. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun union(other: NormRect): NormRect = NormRect(
        min(left, other.left),
        min(top, other.top),
        max(right, other.right),
        max(bottom, other.bottom)
    )

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    fun inflate(dx: Float, dy: Float): NormRect = NormRect(left - dx, top - dy, right + dx, bottom + dy)

    companion object {
        fun bounding(points: List<NormPoint>): NormRect = NormRect(
            points.minOf { it.x },
            points.minOf { it.y },
            points.maxOf { it.x },
            points.maxOf { it.y }
        )
    }
}

/** A point in PDF user space (origin bottom-left, y up). */
data class UserPoint(val x: Float, val y: Float)

/**
 * The visible area (crop box) of a page plus its /Rotate value. Converts between PDF user space and
 * the normalized display space used by the viewer, so overlays line up on rotated or cropped pages.
 */
data class PageFrame(
    val cropLeft: Float,
    val cropBottom: Float,
    val cropWidth: Float,
    val cropHeight: Float,
    val rotation: Int
) {
    val displayWidth: Float get() = if (rotation % 180 == 0) cropWidth else cropHeight
    val displayHeight: Float get() = if (rotation % 180 == 0) cropHeight else cropWidth
    val aspectRatio: Float get() = (displayWidth / displayHeight).takeIf { it.isFinite() && it > 0f } ?: 0.7071f

    fun toDisplay(x: Float, y: Float): NormPoint {
        val px = x - cropLeft
        val py = y - cropBottom
        val w = cropWidth
        val h = cropHeight
        return when (rotation) {
            90 -> NormPoint(py / h, px / w)
            180 -> NormPoint((w - px) / w, py / h)
            270 -> NormPoint((h - py) / h, (w - px) / w)
            else -> NormPoint(px / w, (h - py) / h)
        }
    }

    fun toUser(point: NormPoint): UserPoint {
        val w = cropWidth
        val h = cropHeight
        val (px, py) = when (rotation) {
            90 -> point.y * w to point.x * h
            180 -> w - point.x * w to point.y * h
            270 -> w - point.y * w to h - point.x * h
            else -> point.x * w to h - point.y * h
        }
        return UserPoint(px + cropLeft, py + cropBottom)
    }

    fun userRectToDisplay(x0: Float, y0: Float, x1: Float, y1: Float): NormRect = NormRect.bounding(
        listOf(toDisplay(x0, y0), toDisplay(x1, y0), toDisplay(x1, y1), toDisplay(x0, y1))
    )

    /** Returns [llx, lly, urx, ury] in user space. */
    fun displayRectToUser(rect: NormRect): FloatArray {
        val corners = listOf(
            toUser(NormPoint(rect.left, rect.top)),
            toUser(NormPoint(rect.right, rect.bottom))
        )
        return floatArrayOf(
            corners.minOf { it.x },
            corners.minOf { it.y },
            corners.maxOf { it.x },
            corners.maxOf { it.y }
        )
    }

    /**
     * Matrix values [a b c d e f] whose x axis points right and y axis points up *on screen*, with
     * origin at [origin] and one unit equal to [scale] points. Used to draw upright content on
     * pages with any /Rotate value.
     */
    fun uprightMatrix(origin: NormPoint, scale: Float = 1f): FloatArray {
        val o = toUser(origin)
        val right = toUser(NormPoint(origin.x + STEP, origin.y))
        val up = toUser(NormPoint(origin.x, origin.y - STEP))
        val rx = right.x - o.x
        val ry = right.y - o.y
        val ux = up.x - o.x
        val uy = up.y - o.y
        val rLen = hypot(rx, ry).takeIf { it > 0f } ?: 1f
        val uLen = hypot(ux, uy).takeIf { it > 0f } ?: 1f
        return floatArrayOf(
            rx / rLen * scale,
            ry / rLen * scale,
            ux / uLen * scale,
            uy / uLen * scale,
            o.x,
            o.y
        )
    }

    private companion object {
        const val STEP = 0.01f
    }
}
