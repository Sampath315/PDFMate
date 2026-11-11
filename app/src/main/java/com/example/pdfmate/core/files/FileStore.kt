package com.example.pdfmate.core.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream

object FileStore {
    fun sessionsRoot(context: Context) = File(context.cacheDir, "sessions")
    fun shareDir(context: Context) = File(context.cacheDir, "share").apply { mkdirs() }
    fun exportDir(context: Context) = File(context.cacheDir, "export").apply { mkdirs() }
    fun thumbsDir(context: Context) = File(context.cacheDir, "thumbs").apply { mkdirs() }

    /** Removes leftovers (including decrypted copies) from previous runs. */
    fun cleanTransientFiles(context: Context) {
        listOf(sessionsRoot(context), File(context.cacheDir, "share"), File(context.cacheDir, "export"))
            .forEach { it.deleteRecursively() }
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("pdfmate_") }
            ?.forEach(File::delete)
    }

    fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) cursor.getString(column)?.takeIf(String::isNotBlank)?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "Document.pdf"
    }

    fun copyIn(context: Context, uri: Uri, target: File) {
        target.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("This file can't be opened.")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        if (target.length() == 0L) {
            target.delete()
            throw IOException("This file is empty.")
        }
    }

    /**
     * Copies a fully prepared file into a document the user just created. If anything fails the
     * half-written document is deleted so no empty or broken file is left behind.
     */
    fun copyOut(context: Context, source: File, target: Uri) {
        check(source.isFile && source.length() > 0) { "Nothing to save." }
        val resolver = context.contentResolver
        try {
            val stream: OutputStream = runCatching { resolver.openOutputStream(target, "wt") }.getOrNull()
                ?: resolver.openOutputStream(target, "w")
                ?: throw IOException("The selected location can't be written to.")
            stream.use { output -> source.inputStream().use { it.copyTo(output) } }
        } catch (exception: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw IOException("Couldn't save the file. Nothing was left behind.", exception)
        }
    }

    fun deleteDocument(context: Context, target: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
    }

    fun shareUri(context: Context, source: File, name: String): Uri {
        val target = File(shareDir(context), name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        source.copyTo(target, overwrite = true)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }

    fun formatSize(sizeBytes: Long): String = when {
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "%.1f KB".format(sizeBytes / 1024.0)
        else -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
    }

    fun baseName(name: String): String = name.substringBeforeLast('.', name).ifBlank { "Document" }
}
