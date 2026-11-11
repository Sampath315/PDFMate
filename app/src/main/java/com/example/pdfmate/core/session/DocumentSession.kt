package com.example.pdfmate.core.session

import android.net.Uri
import com.example.pdfmate.core.pdf.ProtectionSettings
import java.io.File

/**
 * A document being viewed/edited. Every edit writes a new immutable version file inside [dir], which
 * gives cheap undo/redo and means the user's original file is never modified in place.
 */
class DocumentSession(
    val dir: File,
    val sourceUri: Uri?,
    var name: String,
    val wasEncrypted: Boolean,
    /** Password the user typed to open the file. Kept in memory only. */
    val openPassword: String,
    val originalPermissions: Int?,
    val signed: Boolean,
    initial: File,
    startsDirty: Boolean
) {
    private val versions = mutableListOf(initial)
    private var index = 0
    private var counter = 1
    private var savedFile: File? = if (startsDirty) null else initial
    private var savedProtection: ProtectionSettings?

    /** Protection applied when the document is saved; null saves an unprotected PDF. */
    var protection: ProtectionSettings? =
        if (wasEncrypted) ProtectionSettings(openPassword, originalPermissions) else null

    init {
        savedProtection = protection
    }

    val current: File get() = versions[index]
    val versionId: String get() = current.name
    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index < versions.lastIndex
    val isDirty: Boolean get() = savedFile != current || savedProtection != protection
    val canRemovePassword: Boolean get() = protection != null && (!wasEncrypted || openPassword.isNotEmpty() || protection != savedProtection)

    fun newVersionFile(): File = File(dir, "v${counter++}.pdf")

    fun commit(file: File) {
        while (versions.lastIndex > index) {
            versions.removeAt(versions.lastIndex).takeIf { it != savedFile }?.delete()
        }
        versions += file
        index = versions.lastIndex
        while (versions.size > MAX_VERSIONS) {
            val dropped = versions.removeAt(0)
            index--
            if (dropped != savedFile) dropped.delete()
        }
    }

    fun undo(): Boolean = canUndo.also { if (it) index-- }

    fun redo(): Boolean = canRedo.also { if (it) index++ }

    fun markSaved() {
        savedFile = current
        savedProtection = protection
    }

    fun close() {
        dir.deleteRecursively()
    }

    private companion object {
        const val MAX_VERSIONS = 15
    }
}
