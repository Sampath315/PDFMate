package com.example.pdfmate.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.pdfmate.core.files.FileStore
import com.example.pdfmate.core.pdf.CompressionLevel
import com.example.pdfmate.core.pdf.FormFieldInfo
import com.example.pdfmate.core.pdf.FormFieldKind
import com.example.pdfmate.core.pdf.NumberFormat
import com.example.pdfmate.core.pdf.NumberPosition
import com.example.pdfmate.core.pdf.PageNumberOptions
import com.example.pdfmate.core.pdf.PdfDetails
import com.example.pdfmate.core.pdf.WatermarkOptions
import com.example.pdfmate.core.pdf.chunkPages
import com.example.pdfmate.core.pdf.parsePageList
import com.example.pdfmate.core.pdf.parsePageRanges
import com.example.pdfmate.ui.AppViewModel
import com.example.pdfmate.ui.DocumentUi
import com.example.pdfmate.ui.ToolId
import com.example.pdfmate.ui.components.ChoicePill
import com.example.pdfmate.ui.components.EmptyState
import com.example.pdfmate.ui.components.GradientButton
import com.example.pdfmate.ui.components.InfoRow
import com.example.pdfmate.ui.components.PasswordField
import com.example.pdfmate.ui.components.ToolCatalog
import com.example.pdfmate.ui.components.ToolSheetLayout
import kotlin.math.roundToInt

/** Everything a tool sheet may need from the viewer besides the view model. */
internal class SheetContext(
    val vm: AppViewModel,
    val document: DocumentUi,
    /** Zero-based page the user is looking at. */
    val currentPage: Int,
    val close: () -> Unit,
    val pickPdfToAppend: () -> Unit,
    val pickSignatureImage: () -> Unit,
    val onSignature: (android.graphics.Bitmap) -> Unit
)

@Composable
internal fun ToolSheetContent(tool: ToolId, context: SheetContext) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        when (tool) {
            ToolId.SPLIT -> SplitSheet(context)
            ToolId.EXTRACT -> ExtractSheet(context)
            ToolId.ROTATE -> RotateSheet(context)
            ToolId.REMOVE_PAGES -> RemovePagesSheet(context)
            ToolId.FORMS -> FormsSheet(context)
            ToolId.COMPRESS -> CompressSheet(context)
            ToolId.OCR -> OcrSheet(context)
            ToolId.WATERMARK -> WatermarkSheet(context)
            ToolId.PAGE_NUMBERS -> PageNumbersSheet(context)
            ToolId.PROPERTIES -> PropertiesSheet(context)
            ToolId.PDF_TO_JPG -> ToJpgSheet(context)
            ToolId.PDF_TO_TEXT -> ToTextSheet(context)
            ToolId.PROTECT -> ProtectSheet(context)
            ToolId.UNLOCK -> UnlockSheet(context)
            ToolId.MERGE -> AppendSheet(context)
            ToolId.SIGN -> SignatureSheet(context.onSignature, context.pickSignatureImage)
            else -> Unit
        }
    }
}

// region Shared pieces

private enum class PageScope(val label: String) { ALL("All pages"), CURRENT("This page"), CUSTOM("Choose pages") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pills(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Lets the user pick which pages a tool applies to; returns 1-based page numbers or an error. */
private class PageScopeState(initial: PageScope) {
    var scope by mutableStateOf(initial)
    var custom by mutableStateOf("")
    var error by mutableStateOf<String?>(null)

    fun resolve(currentPage: Int, pageCount: Int): List<Int>? = try {
        when (scope) {
            PageScope.ALL -> (1..pageCount).toList()
            PageScope.CURRENT -> listOf(currentPage + 1)
            PageScope.CUSTOM -> parsePageList(custom, pageCount).distinct()
        }.also { error = null }
    } catch (exception: IllegalArgumentException) {
        error = exception.message
        null
    }
}

@Composable
private fun rememberPageScope(initial: PageScope = PageScope.ALL) = remember { PageScopeState(initial) }

@Composable
private fun PageScopePicker(state: PageScopeState, pageCount: Int, currentPage: Int, options: List<PageScope> = PageScope.entries) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("Pages")
        Pills {
            options.forEach { option ->
                val label = if (option == PageScope.CURRENT) "Page ${currentPage + 1}" else option.label
                ChoicePill(label, state.scope == option, { state.scope = option; state.error = null })
            }
        }
        if (state.scope == PageScope.CUSTOM) {
            OutlinedTextField(
                value = state.custom,
                onValueChange = { state.custom = it; state.error = null },
                label = { Text("e.g. 1-3, 5, 8") },
                supportingText = { Text(state.error ?: "This PDF has $pageCount pages") },
                isError = state.error != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        } else if (state.error != null) {
            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ColorSwatches(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { color ->
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(if (color == selected) 3.dp else 1.dp, if (color == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onSelect(color) }
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Label(label)
            Box(Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun OptionCard(icon: ImageVector?, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primary.copy(alpha = 0.08f) else colors.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            icon?.let { Icon(it, null, tint = if (selected) colors.primary else colors.onSurfaceVariant) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Rounded.Check, null, tint = colors.primary)
        }
    }
}

// endregion

// region Organize tools

@Composable
private fun SplitSheet(c: SheetContext) {
    var byRanges by remember { mutableStateOf(false) }
    var every by remember { mutableStateOf("1") }
    var ranges by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val count = c.document.pageCount
    ToolSheetLayout(ToolCatalog[ToolId.SPLIT], "Each part becomes its own PDF, saved together in a .zip.") {
        Pills {
            ChoicePill("Every N pages", !byRanges, { byRanges = false; error = null })
            ChoicePill("Custom ranges", byRanges, { byRanges = true; error = null })
        }
        if (byRanges) {
            OutlinedTextField(
                value = ranges,
                onValueChange = { ranges = it; error = null },
                label = { Text("Ranges, e.g. 1-3, 4-6, 7") },
                supportingText = { Text(error ?: "Each range becomes one file") },
                isError = error != null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = every,
                onValueChange = { every = it.filter(Char::isDigit).take(5); error = null },
                label = { Text("Pages per file") },
                supportingText = {
                    val n = every.toIntOrNull()
                    Text(error ?: if (n != null && n > 0) "Creates ${(count + n - 1) / n} files" else "Enter a number")
                },
                isError = error != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        GradientButton("Split & save", icon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth(), onClick = {
            try {
                val groups = if (byRanges) parsePageRanges(ranges, count) else chunkPages(count, every.toIntOrNull() ?: 0)
                require(groups.size > 1 || byRanges) { "That would create a single file. Use a smaller number." }
                c.vm.exportSplit(groups)
                c.close()
            } catch (exception: IllegalArgumentException) {
                error = exception.message
            }
        })
    }
}

@Composable
private fun ExtractSheet(c: SheetContext) {
    val pages = rememberPageScope(PageScope.CUSTOM)
    ToolSheetLayout(ToolCatalog[ToolId.EXTRACT], "Save the pages you pick as a new PDF.") {
        PageScopePicker(pages, c.document.pageCount, c.currentPage, listOf(PageScope.CURRENT, PageScope.CUSTOM))
        GradientButton("Extract & save", icon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth(), onClick = {
            pages.resolve(c.currentPage, c.document.pageCount)?.let {
                c.vm.exportExtract(it)
                c.close()
            }
        })
    }
}

@Composable
private fun RotateSheet(c: SheetContext) {
    val pages = rememberPageScope(PageScope.ALL)
    var degrees by remember { mutableIntStateOf(90) }
    ToolSheetLayout(ToolCatalog[ToolId.ROTATE], null) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple(-90, "Left", Icons.AutoMirrored.Rounded.RotateLeft), Triple(90, "Right", Icons.AutoMirrored.Rounded.RotateRight), Triple(180, "180°", Icons.AutoMirrored.Rounded.RotateRight)).forEach { (value, label, icon) ->
                Box(Modifier.weight(1f)) {
                    OptionCard(icon, label, if (value == 180) "Upside down" else "90°", degrees == value) { degrees = value }
                }
            }
        }
        PageScopePicker(pages, c.document.pageCount, c.currentPage)
        GradientButton("Rotate", modifier = Modifier.fillMaxWidth(), onClick = {
            pages.resolve(c.currentPage, c.document.pageCount)?.let { list ->
                c.vm.rotatePages(list.map { it - 1 }, degrees)
                c.close()
            }
        })
    }
}

@Composable
private fun RemovePagesSheet(c: SheetContext) {
    val pages = rememberPageScope(PageScope.CURRENT)
    ToolSheetLayout(ToolCatalog[ToolId.REMOVE_PAGES], "You can undo this from the top bar.") {
        PageScopePicker(pages, c.document.pageCount, c.currentPage, listOf(PageScope.CURRENT, PageScope.CUSTOM))
        GradientButton("Remove pages", icon = Icons.Rounded.Delete, modifier = Modifier.fillMaxWidth(), onClick = {
            pages.resolve(c.currentPage, c.document.pageCount)?.let { list ->
                c.vm.deletePages(list.map { it - 1 }.toSet())
                c.close()
            }
        })
    }
}

@Composable
private fun AppendSheet(c: SheetContext) {
    ToolSheetLayout(ToolCatalog[ToolId.MERGE], "Add another PDF to the end of this one. Use Organize afterwards to change the order.") {
        GradientButton("Choose a PDF to add", icon = Icons.Rounded.FolderOpen, modifier = Modifier.fillMaxWidth(), onClick = {
            c.close()
            c.pickPdfToAppend()
        })
    }
}

// endregion

// region Edit & enhance tools

@Composable
private fun FormsSheet(c: SheetContext) {
    val fields by produceState<List<FormFieldInfo>?>(null, c.document.versionKey) { value = c.vm.formFields() }
    val values = remember { mutableStateMapOf<String, String>() }
    var flatten by remember { mutableStateOf(false) }
    ToolSheetLayout(ToolCatalog[ToolId.FORMS], null) {
        val loaded = fields
        when {
            loaded == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            loaded.isEmpty() -> EmptyState(
                ToolCatalog[ToolId.FORMS].icon,
                "No fillable fields",
                "This PDF has no form fields. Use Add text to type on the page instead."
            )
            else -> {
                loaded.forEach { field ->
                    val current = values[field.name] ?: field.value
                    when (field.kind) {
                        FormFieldKind.TEXT -> OutlinedTextField(
                            value = current,
                            onValueChange = { values[field.name] = it },
                            label = { Text(field.label) },
                            enabled = !field.readOnly,
                            singleLine = !field.multiline,
                            minLines = if (field.multiline) 3 else 1,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        FormFieldKind.CHECKBOX -> SwitchRow(field.label, null, current == "true") {
                            if (!field.readOnly) values[field.name] = it.toString()
                        }
                        FormFieldKind.RADIO, FormFieldKind.CHOICE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label(field.label)
                            Pills {
                                field.options.forEach { option ->
                                    ChoicePill(option, current == option, { if (!field.readOnly) values[field.name] = option })
                                }
                            }
                        }
                    }
                }
                SwitchRow("Flatten", "Lock the answers so they can't be changed", flatten) { flatten = it }
                GradientButton("Apply", icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), onClick = {
                    c.vm.fillForm(values.toMap(), flatten)
                    c.close()
                })
            }
        }
    }
}

@Composable
private fun CompressSheet(c: SheetContext) {
    var level by remember { mutableStateOf(CompressionLevel.BALANCED) }
    ToolSheetLayout(ToolCatalog[ToolId.COMPRESS], "Current size: ${FileStore.formatSize(c.document.sizeBytes)}") {
        OptionCard(Icons.Rounded.Compress, "Lossless", "Clean up and repack. Images untouched.", level == CompressionLevel.LOSSLESS) { level = CompressionLevel.LOSSLESS }
        OptionCard(Icons.Rounded.Compress, "Recommended", "Good quality, much smaller images.", level == CompressionLevel.BALANCED) { level = CompressionLevel.BALANCED }
        OptionCard(Icons.Rounded.Compress, "Extreme", "Smallest file, lower image quality.", level == CompressionLevel.STRONG) { level = CompressionLevel.STRONG }
        GradientButton("Compress", modifier = Modifier.fillMaxWidth(), onClick = {
            c.vm.compress(level)
            c.close()
        })
    }
}

@Composable
private fun OcrSheet(c: SheetContext) {
    ToolSheetLayout(
        ToolCatalog[ToolId.OCR],
        "Recognizes text in scanned pages so you can search and copy it. Runs on your device; pages that already have text are left alone."
    ) {
        GradientButton("Recognize text", icon = Icons.Rounded.DocumentScanner, modifier = Modifier.fillMaxWidth(), onClick = {
            c.vm.makeSearchable()
            c.close()
        })
    }
}

private val StampColors = listOf(0xFF6B7280.toInt(), 0xFFE5394B.toInt(), 0xFF2563EB.toInt(), 0xFF111827.toInt(), 0xFF16A34A.toInt())

@Composable
private fun WatermarkSheet(c: SheetContext) {
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var opacity by remember { mutableFloatStateOf(0.25f) }
    var size by remember { mutableFloatStateOf(54f) }
    var diagonal by remember { mutableStateOf(true) }
    var color by remember { mutableIntStateOf(StampColors.first()) }
    val pages = rememberPageScope(PageScope.ALL)
    ToolSheetLayout(ToolCatalog[ToolId.WATERMARK], null) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(80) },
            label = { Text("Watermark text") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Label("Color")
        ColorSwatches(StampColors, color) { color = it }
        LabeledSlider("Opacity", "${(opacity * 100).roundToInt()}%", opacity, 0.05f..1f) { opacity = it }
        LabeledSlider("Size", "${size.roundToInt()} pt", size, 12f..120f) { size = it }
        SwitchRow("Diagonal", "Run across the page from corner to corner", diagonal) { diagonal = it }
        PageScopePicker(pages, c.document.pageCount, c.currentPage)
        GradientButton("Add watermark", enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
            pages.resolve(c.currentPage, c.document.pageCount)?.let { list ->
                val all = pages.scope == PageScope.ALL
                c.vm.watermark(WatermarkOptions(text.trim(), opacity, size, diagonal, color, if (all) null else list))
                c.close()
            }
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageNumbersSheet(c: SheetContext) {
    var position by remember { mutableStateOf(NumberPosition.BOTTOM_CENTER) }
    var format by remember { mutableStateOf(NumberFormat.PAGE_OF_TOTAL) }
    var startAt by remember { mutableStateOf("1") }
    var size by remember { mutableFloatStateOf(10f) }
    var skipFirst by remember { mutableStateOf(false) }
    ToolSheetLayout(ToolCatalog[ToolId.PAGE_NUMBERS], null) {
        Label("Position")
        // A miniature page showing the six possible spots.
        Box(
            Modifier
                .fillMaxWidth(0.5f)
                .height(150.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            NumberPosition.entries.forEach { spot ->
                val alignment = when (spot) {
                    NumberPosition.TOP_LEFT -> Alignment.TopStart
                    NumberPosition.TOP_CENTER -> Alignment.TopCenter
                    NumberPosition.TOP_RIGHT -> Alignment.TopEnd
                    NumberPosition.BOTTOM_LEFT -> Alignment.BottomStart
                    NumberPosition.BOTTOM_CENTER -> Alignment.BottomCenter
                    NumberPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                }
                val selected = spot == position
                Box(
                    Modifier
                        .align(alignment)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xFFE3E7F0))
                        .clickable { position = spot },
                    contentAlignment = Alignment.Center
                ) {
                    Text("#", color = if (selected) Color.White else Color(0xFF5E6678), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Label("Format")
        Pills { NumberFormat.entries.forEach { ChoicePill(it.label, format == it, { format = it }) } }
        OutlinedTextField(
            value = startAt,
            onValueChange = { startAt = it.filter(Char::isDigit).take(5) },
            label = { Text("Start numbering at") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        LabeledSlider("Size", "${size.roundToInt()} pt", size, 6f..24f) { size = it }
        SwitchRow("Skip first page", "Leave the cover unnumbered", skipFirst) { skipFirst = it }
        GradientButton("Add page numbers", modifier = Modifier.fillMaxWidth(), onClick = {
            c.vm.pageNumbers(PageNumberOptions(position, format, startAt.toIntOrNull()?.coerceAtLeast(1) ?: 1, size, skipFirst, 0xFF111827.toInt()))
            c.close()
        })
    }
}

@Composable
private fun PropertiesSheet(c: SheetContext) {
    val details by produceState<PdfDetails?>(null, c.document.versionKey) { value = c.vm.details() }
    var title by remember { mutableStateOf<String?>(null) }
    var author by remember { mutableStateOf<String?>(null) }
    var subject by remember { mutableStateOf<String?>(null) }
    var keywords by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(details) {
        details?.let {
            if (title == null) title = it.title
            if (author == null) author = it.author
            if (subject == null) subject = it.subject
            if (keywords == null) keywords = it.keywords
        }
    }
    ToolSheetLayout(ToolCatalog[ToolId.PROPERTIES], c.document.name) {
        val info = details
        if (info == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ToolSheetLayout
        }
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                InfoRow("Pages", info.pageCount.toString())
                InfoRow("Size", FileStore.formatSize(c.document.sizeBytes))
                InfoRow("PDF version", info.version.toString())
                InfoRow("Password", if (c.document.isProtected) "Yes" else "No")
                InfoRow("Signed", if (info.signed) "Yes — edits will invalidate the signature" else "No")
                InfoRow("Form", if (info.isXfa) "XFA (not supported)" else if (info.hasForm) "Fillable" else "None")
                info.producer?.takeIf { it.isNotBlank() }?.let { InfoRow("Producer", it) }
                info.creator?.takeIf { it.isNotBlank() }?.let { InfoRow("Creator", it) }
            }
        }
        listOf(
            Triple("Title", title) { v: String -> title = v },
            Triple("Author", author) { v: String -> author = v },
            Triple("Subject", subject) { v: String -> subject = v },
            Triple("Keywords", keywords) { v: String -> keywords = v }
        ).forEach { (label, value, set) ->
            OutlinedTextField(
                value = value.orEmpty(),
                onValueChange = set,
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        GradientButton("Save properties", icon = Icons.Rounded.Save, modifier = Modifier.fillMaxWidth(), onClick = {
            c.vm.updateProperties(title.orEmpty(), author.orEmpty(), subject.orEmpty(), keywords.orEmpty())
            c.close()
        })
    }
}

// endregion

// region Convert

@Composable
private fun ToJpgSheet(c: SheetContext) {
    val pages = rememberPageScope(PageScope.ALL)
    var dpi by remember { mutableIntStateOf(150) }
    ToolSheetLayout(ToolCatalog[ToolId.PDF_TO_JPG], "Each page becomes a JPG, saved together in a .zip.") {
        Label("Quality")
        Pills {
            listOf(96 to "Screen", 150 to "Standard", 300 to "Print").forEach { (value, label) ->
                ChoicePill("$label · $value dpi", dpi == value, { dpi = value })
            }
        }
        PageScopePicker(pages, c.document.pageCount, c.currentPage)
        GradientButton("Convert & save", icon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth(), onClick = {
            pages.resolve(c.currentPage, c.document.pageCount)?.let { list ->
                c.vm.exportImages(list.map { it - 1 }, dpi)
                c.close()
            }
        })
    }
}

@Composable
private fun ToTextSheet(c: SheetContext) {
    ToolSheetLayout(ToolCatalog[ToolId.PDF_TO_TEXT], "Saves all selectable text as a .txt file. For scans, run Make searchable first.") {
        GradientButton("Export text", icon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth(), onClick = {
            c.vm.exportText()
            c.close()
        })
    }
}

// endregion

// region Security

@Composable
private fun ProtectSheet(c: SheetContext) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ToolSheetLayout(
        ToolCatalog[ToolId.PROTECT],
        if (c.document.isProtected) "This PDF already has a password. Setting a new one replaces it." else "Anyone opening the saved file will need this password."
    ) {
        PasswordField(password, { password = it; error = null }, "Password", imeAction = ImeAction.Next)
        PasswordField(confirm, { confirm = it; error = null }, "Confirm password", error = error)
        val strength = when {
            password.isEmpty() -> null
            password.length < 6 -> "Weak — use at least 6 characters" to MaterialTheme.colorScheme.error
            password.length < 10 || password.all(Char::isLetter) -> "Okay — longer is stronger" to Color(0xFFF59E0B)
            else -> "Strong" to Color(0xFF10B981)
        }
        strength?.let { (text, color) -> Text(text, color = color, style = MaterialTheme.typography.labelLarge) }
        Text(
            "Encrypted with AES-256. Keep the password safe — it can't be recovered.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GradientButton("Protect & save", icon = Icons.Rounded.Lock, modifier = Modifier.fillMaxWidth(), onClick = {
            error = when {
                password.length < 4 -> "Use at least 4 characters."
                password != confirm -> "Passwords don't match."
                else -> null
            }
            if (error == null) {
                c.vm.setPassword(password)
                c.vm.saveCopy()
                c.close()
            }
        })
    }
}

@Composable
private fun UnlockSheet(c: SheetContext) {
    ToolSheetLayout(
        ToolCatalog[ToolId.UNLOCK],
        when {
            !c.document.isProtected -> "This PDF doesn't have a password."
            c.document.canRemovePassword -> "Save a copy that opens without a password."
            else -> "This PDF has restrictions but you didn't enter its password. Reopen it with the password to remove protection."
        }
    ) {
        GradientButton(
            "Remove password & save",
            icon = Icons.Rounded.LockOpen,
            enabled = c.document.isProtected && c.document.canRemovePassword,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                c.vm.removePassword()
                c.vm.saveCopy()
                c.close()
            }
        )
        if (c.document.isProtected) {
            TextButton(onClick = { c.vm.removePassword(); c.close() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Remove without saving yet")
            }
        }
    }
}

// endregion
