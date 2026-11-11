package com.example.pdfmate

import com.example.pdfmate.core.pdf.NormPoint
import com.example.pdfmate.core.pdf.PageFrame
import com.example.pdfmate.core.pdf.chunkPages
import com.example.pdfmate.core.pdf.normalizeRotation
import com.example.pdfmate.core.pdf.parsePageList
import com.example.pdfmate.core.pdf.parsePageRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageListParserTest {
    @Test
    fun parsesNumbersAndRangesInOrder() {
        assertEquals(listOf(1, 2, 3, 5, 8, 9), parsePageList("1-3, 5, 8-9"))
    }

    @Test
    fun preservesRangeBoundariesForSplit() {
        assertEquals(listOf(listOf(1, 2, 3), listOf(5), listOf(8, 9)), parsePageRanges("1-3, 5, 8-9"))
    }

    @Test
    fun rejectsDescendingRanges() {
        assertThrows(IllegalArgumentException::class.java) { parsePageList("5-2") }
    }

    @Test
    fun rejectsEmptyItems() {
        assertThrows(IllegalArgumentException::class.java) { parsePageList("1,,3") }
    }

    @Test
    fun rejectsPagesBeyondDocument() {
        assertThrows(IllegalArgumentException::class.java) { parsePageList("1-12", pageCount = 10) }
    }

    @Test
    fun chunksPagesEvenly() {
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), chunkPages(5, 2))
    }

    @Test
    fun normalizesRotation() {
        assertEquals(270, normalizeRotation(-90))
        assertEquals(90, normalizeRotation(450))
        assertEquals(0, normalizeRotation(360))
    }

    @Test
    fun pageFrameRoundTripsForAllRotations() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val frame = PageFrame(cropLeft = 10f, cropBottom = 20f, cropWidth = 600f, cropHeight = 800f, rotation = rotation)
            val display = frame.toDisplay(130f, 520f)
            val user = frame.toUser(NormPoint(display.x, display.y))
            assertEquals("x at $rotation", 130f, user.x, 0.01f)
            assertEquals("y at $rotation", 520f, user.y, 0.01f)
        }
    }

    @Test
    fun pageFrameMapsTopLeftOfUnrotatedPage() {
        val frame = PageFrame(0f, 0f, 600f, 800f, 0)
        val p = frame.toDisplay(0f, 800f)
        assertEquals(0f, p.x, 0.0001f)
        assertEquals(0f, p.y, 0.0001f)
    }
}
