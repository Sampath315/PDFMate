package com.example.pdfmate.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTextLayoutTest {
    private val layout = textLayout("Hello world")

    @Test
    fun longPressSelectsTheContainingWordAndItsBounds() {
        val selection = layout.selectWordAt(NormPoint(0.17f, 0.11f))

        assertEquals("Hello", selection?.text)
        assertEquals(0, selection?.pageIndex)
        assertEquals(0, selection?.startIndex)
        assertEquals(5, selection?.endIndex)
        assertEquals(1, selection?.rects?.size)
        assertEquals(0.1f, selection?.rects?.single()?.left ?: -1f, 0.0001f)
    }

    @Test
    fun whitespaceIsNotTreatedAsASelectableWord() {
        assertNull(layout.selectWordAt(NormPoint(0.345f, 0.11f)))
    }

    @Test
    fun selectAllReturnsThePageTextAndLineBounds() {
        val selection = layout.selectAll()

        assertEquals("Hello world", selection?.text)
        assertTrue(selection?.rects?.isNotEmpty() == true)
        assertEquals(1, selection?.rects?.size)
    }

    @Test
    fun selectAllReturnsNullForPagesWithoutText() {
        val empty = PageTextLayout(0, emptyList(), "", intArrayOf(), emptyList())

        assertNull(empty.selectAll())
    }

    @Test
    fun selectRangeReturnsTheRequestedCharactersAndOffsets() {
        val selection = layout.selectRange(6, 11)

        assertEquals("world", selection?.text)
        assertEquals(6, selection?.startIndex)
        assertEquals(11, selection?.endIndex)
    }

    @Test
    fun characterHitTestingMapsGlyphsBackToTextOffsets() {
        assertEquals(6, layout.characterIndexAt(NormPoint(0.37f, 0.11f)))
        assertNull(layout.characterIndexAt(NormPoint(0.9f, 0.9f)))
    }

    @Test
    fun textPositionsOrderByPageThenCharacter() {
        assertTrue(PageTextPosition(0, 100) < PageTextPosition(1, 0))
        assertTrue(PageTextPosition(2, 4) < PageTextPosition(2, 5))
    }

    private fun textLayout(text: String): PageTextLayout {
        val charGlyphs = IntArray(text.length)
        val glyphRects = mutableListOf<NormRect>()
        var glyphIndex = 0
        text.forEachIndexed { index, character ->
            if (character == ' ') {
                charGlyphs[index] = -1
            } else {
                charGlyphs[index] = glyphIndex
                val left = 0.1f + glyphIndex * 0.05f
                glyphRects += NormRect(left, 0.1f, left + 0.04f, 0.12f)
                glyphIndex++
            }
        }
        return PageTextLayout(0, emptyList(), text, charGlyphs, glyphRects)
    }
}