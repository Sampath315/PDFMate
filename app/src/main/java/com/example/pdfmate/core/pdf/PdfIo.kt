package com.example.pdfmate.core.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException
import java.security.SecureRandom

class PdfPasswordException(val passwordWasSupplied: Boolean) : IOException(
    if (passwordWasSupplied) "That password is incorrect." else "This PDF is password protected."
)

/** Password to re-apply on export. [permissions] keeps the original owner restrictions when known. */
data class ProtectionSettings(val userPassword: String, val permissions: Int? = null)

data class OpenedPdf(
    val pageCount: Int,
    val wasEncrypted: Boolean,
    val permissions: Int?,
    val signed: Boolean
)

data class PdfDetails(
    val pageCount: Int,
    val title: String,
    val author: String,
    val subject: String,
    val keywords: String,
    val producer: String?,
    val creator: String?,
    val version: Float,
    val hasForm: Boolean,
    val isXfa: Boolean,
    val signed: Boolean
)

object PdfIo {
    private const val MAX_MAIN_MEMORY = 48L * 1024 * 1024

    fun init(context: Context) = PDFBoxResourceLoader.init(context.applicationContext)

    fun load(file: File, password: String = ""): PDDocument = try {
        PDDocument.load(file, password, MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY))
    } catch (exception: InvalidPasswordException) {
        throw PdfPasswordException(password.isNotEmpty()).apply { initCause(exception) }
    }

    /** Saves and re-opens the result so a broken or empty file is never handed to the user. */
    fun save(document: PDDocument, output: File, verifyPassword: String = "") {
        val expectedPages = document.numberOfPages
        document.save(output)
        verify(output, expectedPages, verifyPassword)
    }

    fun verify(file: File, expectedPages: Int, password: String = "") {
        check(file.isFile && file.length() > 0) { "The PDF could not be written." }
        PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly()).use { copy ->
            check(copy.numberOfPages == expectedPages) { "The saved PDF failed page verification." }
        }
    }

    /** Opens [source] and writes an unencrypted working copy to [output]. */
    fun openAsWorkingCopy(source: File, password: String, output: File): OpenedPdf {
        val opened = load(source, password).use { document ->
            val info = OpenedPdf(
                pageCount = document.numberOfPages,
                wasEncrypted = document.isEncrypted,
                permissions = if (document.isEncrypted) document.encryption?.permissions else null,
                signed = document.isSigned()
            )
            if (info.wasEncrypted) {
                document.setAllSecurityToBeRemoved(true)
                save(document, output)
            }
            info
        }
        if (!opened.wasEncrypted && !source.renameTo(output)) {
            source.copyTo(output, overwrite = true)
        }
        return opened
    }

    /** Produces the file that will be handed to the user: the working copy, protected if requested. */
    fun writeExport(working: File, output: File, protection: ProtectionSettings?) {
        if (protection == null) {
            working.copyTo(output, overwrite = true)
            val pages = load(working).use { it.numberOfPages }
            verify(output, pages)
            return
        }
        load(working).use { document ->
            document.applyProtection(protection)
            save(document, output, protection.userPassword)
        }
    }

    fun details(file: File): PdfDetails = load(file).use { document ->
        val info = document.documentInformation
        val form = document.documentCatalog.acroForm
        PdfDetails(
            pageCount = document.numberOfPages,
            title = info.title.orEmpty(),
            author = info.author.orEmpty(),
            subject = info.subject.orEmpty(),
            keywords = info.keywords.orEmpty(),
            producer = info.producer?.takeIf(String::isNotBlank),
            creator = info.creator?.takeIf(String::isNotBlank),
            version = document.version,
            hasForm = form != null && form.fieldTree.any(),
            isXfa = form?.hasXFA() == true,
            signed = document.isSigned()
        )
    }

    fun hasExtractableText(file: File, maxPages: Int = 5): Boolean = load(file).use { document ->
        PDFTextStripper().apply {
            startPage = 1
            endPage = maxPages.coerceAtMost(document.numberOfPages)
        }.getText(document).isNotBlank()
    }

    fun extractText(input: File, output: File) {
        load(input).use { document ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            output.bufferedWriter().use { writer ->
                for (page in 1..document.numberOfPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    if (page > 1) writer.write("\n\n")
                    writer.write("--- Page $page ---\n")
                    writer.write(stripper.getText(document).trim())
                }
            }
        }
        check(output.length() > 0) { "No text could be exported." }
    }
}

fun PDDocument.applyProtection(settings: ProtectionSettings) {
    val permission = settings.permissions?.let(::AccessPermission) ?: AccessPermission()
    val ownerPassword = ByteArray(32).also(SecureRandom()::nextBytes)
        .joinToString("") { byte -> "%02x".format(byte) }
    protect(
        StandardProtectionPolicy(ownerPassword, settings.userPassword, permission).apply {
            encryptionKeyLength = 256
        }
    )
}

fun PDDocument.isSigned(): Boolean = runCatching { signatureDictionaries.isNotEmpty() }.getOrDefault(false)

fun PDPage.frame(): PageFrame {
    val box = cropBox
    val normalizedRotation = ((rotation % 360 + 360) % 360 / 90) * 90
    return PageFrame(box.lowerLeftX, box.lowerLeftY, box.width, box.height, normalizedRotation)
}
