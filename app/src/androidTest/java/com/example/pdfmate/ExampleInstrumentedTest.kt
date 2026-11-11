package com.example.pdfmate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pdfmate.core.pdf.PdfIo
import com.example.pdfmate.core.pdf.ProtectionSettings
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.pdfmate", appContext.packageName)
    }

    @Test
    fun protectThenUnlockPreservesTextAndPageCount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PdfIo.init(context)
        val plain = File(context.cacheDir, "plain-test.pdf")
        val encrypted = File(context.cacheDir, "encrypted-test.pdf")
        val unlocked = File(context.cacheDir, "unlocked-test.pdf")
        try {
            PDDocument().use { source ->
                val page = PDPage()
                source.addPage(page)
                PDPageContentStream(source, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(48f, 720f)
                    stream.showText("Searchable PDFMate fixture")
                    stream.endText()
                }
                source.save(plain)
            }

            PdfIo.writeExport(plain, encrypted, ProtectionSettings("user-secret"))
            assertTrue(encrypted.length() > 0)

            val opened = PdfIo.openAsWorkingCopy(encrypted, "user-secret", unlocked)
            assertTrue(opened.wasEncrypted)
            assertEquals(1, opened.pageCount)

            PDDocument.load(unlocked).use { output ->
                assertFalse(output.isEncrypted)
                assertEquals(1, output.numberOfPages)
                assertTrue(PDFTextStripper().getText(output).contains("Searchable PDFMate fixture"))
            }
        } finally {
            plain.delete()
            encrypted.delete()
            unlocked.delete()
        }
    }
}
