package com.example.pdfmate.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.DynamicForm
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.pdfmate.ui.ToolId
import com.example.pdfmate.ui.theme.ConvertGreen
import com.example.pdfmate.ui.theme.EditViolet
import com.example.pdfmate.ui.theme.EnhancePink
import com.example.pdfmate.ui.theme.OrganizeBlue
import com.example.pdfmate.ui.theme.SecurityAmber

enum class ToolCategory(val title: String, val accent: Color) {
    ORGANIZE("Organize", OrganizeBlue),
    EDIT("Edit & sign", EditViolet),
    ENHANCE("Optimize & enhance", EnhancePink),
    CONVERT("Convert", ConvertGreen),
    SECURITY("Security", SecurityAmber)
}

data class ToolInfo(
    val id: ToolId,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val category: ToolCategory
) {
    val accent: Color get() = category.accent
}

object ToolCatalog {
    val all: List<ToolInfo> = listOf(
        ToolInfo(ToolId.MERGE, "Merge", "Combine PDFs in any order", Icons.AutoMirrored.Rounded.CallMerge, ToolCategory.ORGANIZE),
        ToolInfo(ToolId.SPLIT, "Split", "Break into separate files", Icons.AutoMirrored.Rounded.CallSplit, ToolCategory.ORGANIZE),
        ToolInfo(ToolId.ORGANIZE, "Organize pages", "Reorder, duplicate, rotate", Icons.Rounded.GridView, ToolCategory.ORGANIZE),
        ToolInfo(ToolId.EXTRACT, "Extract pages", "Save selected pages", Icons.Rounded.FileCopy, ToolCategory.ORGANIZE),
        ToolInfo(ToolId.ROTATE, "Rotate", "Turn pages the right way", Icons.AutoMirrored.Rounded.RotateRight, ToolCategory.ORGANIZE),
        ToolInfo(ToolId.REMOVE_PAGES, "Remove pages", "Delete pages you don't need", Icons.Rounded.DeleteSweep, ToolCategory.ORGANIZE),
        ToolInfo(ToolId.EDIT_TEXT, "Edit text", "Change existing text in place", Icons.Rounded.EditNote, ToolCategory.EDIT),
        ToolInfo(ToolId.ADD_TEXT, "Add text", "Type anywhere on a page", Icons.Rounded.TextFields, ToolCategory.EDIT),
        ToolInfo(ToolId.DRAW, "Draw & highlight", "Pen, marker and highlights", Icons.Rounded.Draw, ToolCategory.EDIT),
        ToolInfo(ToolId.SIGN, "Sign", "Draw or import a signature", Icons.Rounded.HistoryEdu, ToolCategory.EDIT),
        ToolInfo(ToolId.FORMS, "Fill forms", "Complete fillable fields", Icons.Rounded.DynamicForm, ToolCategory.EDIT),
        ToolInfo(ToolId.COMPRESS, "Compress", "Shrink file size", Icons.Rounded.Compress, ToolCategory.ENHANCE),
        ToolInfo(ToolId.OCR, "Make searchable", "Recognize text in scans", Icons.Rounded.DocumentScanner, ToolCategory.ENHANCE),
        ToolInfo(ToolId.WATERMARK, "Watermark", "Stamp text over pages", Icons.Rounded.WaterDrop, ToolCategory.ENHANCE),
        ToolInfo(ToolId.PAGE_NUMBERS, "Page numbers", "Number every page", Icons.Rounded.FormatListNumbered, ToolCategory.ENHANCE),
        ToolInfo(ToolId.CROP, "Crop", "Trim page margins", Icons.Rounded.Crop, ToolCategory.ENHANCE),
        ToolInfo(ToolId.PROPERTIES, "Properties", "Title, author and details", Icons.Rounded.Info, ToolCategory.ENHANCE),
        ToolInfo(ToolId.IMAGES_TO_PDF, "Images to PDF", "Photos and scans to PDF", Icons.Rounded.PhotoLibrary, ToolCategory.CONVERT),
        ToolInfo(ToolId.PDF_TO_JPG, "PDF to JPG", "Export pages as images", Icons.Rounded.Photo, ToolCategory.CONVERT),
        ToolInfo(ToolId.PDF_TO_TEXT, "PDF to text", "Export all text", Icons.AutoMirrored.Rounded.Article, ToolCategory.CONVERT),
        ToolInfo(ToolId.PROTECT, "Protect", "Add a password", Icons.Rounded.Lock, ToolCategory.SECURITY),
        ToolInfo(ToolId.UNLOCK, "Unlock", "Remove the password", Icons.Rounded.LockOpen, ToolCategory.SECURITY)
    )

    private val byId = all.associateBy(ToolInfo::id)

    operator fun get(id: ToolId): ToolInfo = byId.getValue(id)

    fun search(query: String): List<ToolInfo> {
        val needle = query.trim()
        if (needle.isEmpty()) return all
        return all.filter {
            it.title.contains(needle, ignoreCase = true) ||
                it.subtitle.contains(needle, ignoreCase = true) ||
                it.category.title.contains(needle, ignoreCase = true)
        }
    }
}
