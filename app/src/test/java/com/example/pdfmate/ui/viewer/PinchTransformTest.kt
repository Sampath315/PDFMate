package com.example.pdfmate.ui.viewer

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class PinchTransformTest {
    @Test
    fun panMovesContentInTheDirectionOfTheFingers() {
        val translation = accumulatePinchTranslation(
            origin = Offset(120f, 240f),
            translation = Offset.Zero,
            centroid = Offset(120f, 240f),
            pan = Offset(18f, -12f),
            scaleChange = 1.4f
        )

        assertOffsetEquals(Offset(18f, -12f), translation)
    }

    @Test
    fun zoomAtTheOriginalCentroidDoesNotShiftItsFocalPoint() {
        val origin = Offset(300f, 180f)
        val translation = accumulatePinchTranslation(
            origin = origin,
            translation = Offset.Zero,
            centroid = origin,
            pan = Offset.Zero,
            scaleChange = 2.2f
        )

        assertOffsetEquals(Offset.Zero, translation)
    }

    private fun assertOffsetEquals(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
}