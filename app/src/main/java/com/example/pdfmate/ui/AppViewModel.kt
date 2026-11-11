package com.example.pdfmate.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdfmate.core.files.FileStore
import com.example.pdfmate.core.files.RecentDocumentsStore
import com.example.pdfmate.core.files.RecentPdf
import com.example.pdfmate.core.pdf.CompressionLevel
import com.example.pdfmate.core.pdf.Conversions
import com.example.pdfmate.core.pdf.FormFieldInfo
import com.example.pdfmate.core.pdf.ImagePageSize
import com.example.pdfmate.core.pdf.ImageSource
import com.example.pdfmate.core.pdf.InkStroke
import com.example.pdfmate.core.pdf.NormPoint
import com.example.pdfmate.core.pdf.NormRect
import com.example.pdfmate.core.pdf.PageNumberOptions
import com.example.pdfmate.core.pdf.PageSlot
import com.example.pdfmate.core.pdf.PageTextAnalyzer
import com.example.pdfmate.core.pdf.PageTextLayout
import com.example.pdfmate.core.pdf.PdfDetails
import com.example.pdfmate.core.pdf.PdfFiles
import com.example.pdfmate.core.pdf.PdfIo
import com.example.pdfmate.core.pdf.PdfPasswordException
import com.example.pdfmate.core.pdf.ProtectionSettings
import com.example.pdfmate.core.pdf.TextEditException
import com.example.pdfmate.core.pdf.TextEditOutcome
import com.example.pdfmate.core.pdf.TextEditor
import com.example.pdfmate.core.pdf.TextStyleHint
import com.example.pdfmate.core.pdf.WatermarkOptions
import com.example.pdfmate.core.pdf.addPageNumbers
import com.example.pdfmate.core.pdf.addWatermark
import com.example.pdfmate.core.pdf.compress
import com.example.pdfmate.core.pdf.cropPages
import com.example.pdfmate.core.pdf.drawInk
import com.example.pdfmate.core.pdf.fillForm
import com.example.pdfmate.core.pdf.formFields
import com.example.pdfmate.core.pdf.highlightAreas
import com.example.pdfmate.core.pdf.placeImage
import com.example.pdfmate.core.pdf.reorganize
import com.example.pdfmate.core.pdf.rotatePages
import com.example.pdfmate.core.pdf.setMetadata
import com.example.pdfmate.core.render.PageRenderer
import com.example.pdfmate.core.render.PageSize
import com.example.pdfmate.core.session.DocumentSession
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Screen { HOME, VIEWER, ORGANIZE, MERGE, IMAGES }

enum class ToolId {
    MERGE, SPLIT, ORGANIZE, EXTRACT, ROTATE, REMOVE_PAGES,
    EDIT_TEXT, ADD_TEXT, DRAW, SIGN, FORMS,
    COMPRESS, OCR, WATERMARK, PAGE_NUMBERS, CROP, PROPERTIES,
    IMAGES_TO_PDF, PDF_TO_JPG, PDF_TO_TEXT,
    PROTECT, UNLOCK
}

enum class ViewerMode { VIEW, EDIT, ANNOTATE, SIGN, CROP }

data class DocumentUi(
    val name: String,
    val pageSizes: List<PageSize>,
    val versionKey: Int,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val dirty: Boolean,
    val isProtected: Boolean,
    val canRemovePassword: Boolean,
    val signed: Boolean,
    val sizeBytes: Long
) {
    val pageCount: Int get() = pageSizes.size
}

data class BusyState(val message: String, val progress: Float? = null)

data class PasswordPrompt(val name: String, val error: String? = null)

data class ExportRequest(val fileName: String, val mime: String)

data class SearchHit(val pageIndex: Int, val rects: List<NormRect>)

data class SearchState(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val current: Int = 0,
    val running: Boolean = false
)

data class MergeItem(val id: String, val uri: Uri, val name: String, val sizeBytes: Long)

data class ImageItem(val id: String, val uri: Uri, val name: String, val rotation: Int = 0)

sealed interface UiEvent {
    data class Message(val text: String, val offerUndo: Boolean = false) : UiEvent
    data class CreateDocument(val request: ExportRequest) : UiEvent
    data class Share(val uri: Uri, val mime: String) : UiEvent
    data class Print(val file: File, val name: String) : UiEvent
}

private class PendingOpen(val uri: Uri?, val name: String, val source: File, val dir: File, val tool: ToolId?)

private class PendingExport(val file: File, val request: ExportRequest, val marksSaved: Boolean)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val recentStore = RecentDocumentsStore(application)
    private val settings = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(settings.getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    )
        private set
    var recents by mutableStateOf(recentStore.load())
        private set
    val backStack = mutableStateListOf(Screen.HOME)
    val screen: Screen get() = backStack.last()

    var document by mutableStateOf<DocumentUi?>(null)
        private set
    var busy by mutableStateOf<BusyState?>(null)
        private set
    var passwordPrompt by mutableStateOf<PasswordPrompt?>(null)
        private set
    var viewerMode by mutableStateOf(ViewerMode.VIEW)
    var activeSheet by mutableStateOf<ToolId?>(null)

    /** Tool chosen on the home screen before the document was opened; the viewer consumes it once. */
    var pendingTool by mutableStateOf<ToolId?>(null)
    var pendingViewerPage by mutableStateOf<Int?>(null)
        private set
    var search by mutableStateOf(SearchState())
        private set
    val mergeItems = mutableStateListOf<MergeItem>()
    val imageItems = mutableStateListOf<ImageItem>()
    var imagePageSize by mutableStateOf(ImagePageSize.A4)
        private set
    var imageMargins by mutableStateOf(true)
        private set

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var session: DocumentSession? = null
    private var renderer: PageRenderer? = null
    private var versionCounter = 0
    private var pendingOpen: PendingOpen? = null
    private var pendingExport: PendingExport? = null
    private var searchJob: Job? = null
    private val editMutex = Mutex()
    private val layoutMutex = Mutex()
    private var layoutDocument: Pair<String, PDDocument>? = null
    private val layouts = HashMap<String, PageTextLayout>()
    private val bitmapCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        PdfIo.init(application)
        FileStore.cleanTransientFiles(application)
    }

    // region Navigation & settings

    fun navigate(target: Screen) {
        if (screen != target) backStack += target
    }

    fun openViewerPage(pageIndex: Int) {
        pendingViewerPage = pageIndex.coerceAtLeast(0)
        back()
    }

    fun consumePendingViewerPage(pageIndex: Int) {
        if (pendingViewerPage == pageIndex) pendingViewerPage = null
    }

    /** Returns false when there's nothing left to pop (the activity should finish). */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        val leaving = backStack.removeAt(backStack.lastIndex)
        if (leaving == Screen.VIEWER && Screen.VIEWER !in backStack) closeDocument()
        return true
    }

    fun goHome() {
        while (backStack.size > 1) back()
    }

    fun updateTheme(mode: ThemeMode) {
        themeMode = mode
        settings.edit { putString("theme", mode.name) }
    }

    fun removeRecent(uri: Uri) {
        recents = recentStore.remove(uri)
    }

    fun clearRecents() {
        recents = recentStore.clear()
        FileStore.thumbsDir(context).listFiles()?.forEach(File::delete)
    }

    fun updateImagePageSize(pageSize: ImagePageSize) {
        imagePageSize = pageSize
    }

    fun updateImageMargins(enabled: Boolean) {
        imageMargins = enabled
    }

    // endregion

    // region Opening & closing

    fun openDocument(uri: Uri, tool: ToolId? = null) {
        cancelPassword()
        launchTask("Opening…") {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val dir = newSessionDir()
            val source = File(dir, "source.pdf")
            val name = try {
                withContext(Dispatchers.IO) {
                    FileStore.copyIn(context, uri, source)
                    FileStore.displayName(context, uri)
                }
            } catch (exception: Throwable) {
                dir.deleteRecursively()
                throw exception
            }
            pendingOpen = PendingOpen(uri, name, source, dir, tool)
            tryOpen("")
        }
    }

    fun submitPassword(password: String) {
        launchTask("Unlocking…") { tryOpen(password) }
    }

    fun cancelPassword() {
        passwordPrompt = null
        pendingOpen?.dir?.deleteRecursively()
        pendingOpen = null
    }

    private suspend fun tryOpen(password: String) {
        val pending = pendingOpen ?: return
        val working = File(pending.dir, "v0.pdf")
        val opened = try {
            withContext(Dispatchers.IO) { PdfIo.openAsWorkingCopy(pending.source, password, working) }
        } catch (exception: PdfPasswordException) {
            passwordPrompt = PasswordPrompt(pending.name, if (exception.passwordWasSupplied) "Incorrect password. Try again." else null)
            return
        } catch (exception: Throwable) {
            cancelPassword()
            throw exception
        }
        pending.source.delete()
        pendingOpen = null
        passwordPrompt = null
        val newSession = DocumentSession(
            dir = pending.dir,
            sourceUri = pending.uri,
            name = pending.name,
            wasEncrypted = opened.wasEncrypted,
            openPassword = password,
            originalPermissions = opened.permissions,
            signed = opened.signed,
            initial = working,
            startsDirty = false
        )
        startSession(newSession, pending.tool)
        pending.uri?.let { uri ->
            recents = recentStore.remember(
                RecentPdf(uri, pending.name, System.currentTimeMillis(), opened.pageCount, working.length())
            )
            saveThumbnail(uri)
        }
    }

    /** Opens a freshly generated PDF (merge, images) as an unsaved document. */
    private suspend fun openGenerated(file: File, dir: File, name: String, returnTo: Screen? = null) {
        val newSession = DocumentSession(dir, null, name, false, "", null, false, file, startsDirty = true)
        startSession(newSession, null, returnTo)
    }

    private suspend fun startSession(newSession: DocumentSession, tool: ToolId?, returnTo: Screen? = null) {
        releaseSession()
        session = newSession
        refreshDocument()
        viewerMode = ViewerMode.VIEW
        activeSheet = null
        search = SearchState()
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        returnTo?.let(backStack::add)
        backStack += Screen.VIEWER
        pendingTool = tool
    }

    fun closeDocument() {
        searchJob?.cancel()
        val closing = session
        session = null
        document = null
        search = SearchState()
        viewerMode = ViewerMode.VIEW
        activeSheet = null
        pendingTool = null
        viewModelScope.launch(NonCancellable) { releaseResources(closing) }
    }

    private suspend fun releaseSession() {
        val previous = session
        session = null
        releaseResources(previous)
    }

    private suspend fun releaseResources(closing: DocumentSession?) {
        renderer?.close()
        renderer = null
        closeLayoutDocument()
        bitmapCache.evictAll()
        withContext(Dispatchers.IO) { closing?.close() }
    }

    private fun newSessionDir(): File =
        File(FileStore.sessionsRoot(context), UUID.randomUUID().toString()).apply { mkdirs() }

    private suspend fun refreshDocument() {
        val current = session ?: return
        val opened = withContext(Dispatchers.IO) { PageRenderer.open(current.current) }
        val previous = renderer
        renderer = opened
        previous?.close()
        closeLayoutDocument()
        bitmapCache.evictAll()
        versionCounter++
        document = DocumentUi(
            name = current.name,
            pageSizes = opened.pageSizes,
            versionKey = versionCounter,
            canUndo = current.canUndo,
            canRedo = current.canRedo,
            dirty = current.isDirty,
            isProtected = current.protection != null,
            canRemovePassword = current.canRemovePassword,
            signed = current.signed,
            sizeBytes = current.current.length()
        )
        if (search.query.isNotBlank()) runSearch(search.query)
    }

    private fun updateFlags() {
        val current = session ?: return
        document = document?.copy(
            dirty = current.isDirty,
            isProtected = current.protection != null,
            canRemovePassword = current.canRemovePassword,
            canUndo = current.canUndo,
            canRedo = current.canRedo
        )
    }

    // endregion

    // region Rendering & text layout

    suspend fun renderPage(versionKey: Int, index: Int, widthPx: Int): Bitmap? {
        if (versionKey != versionCounter) return null
        val bucket = ((widthPx + 63) / 64) * 64
        val key = "$versionKey:$index:$bucket"
        bitmapCache.get(key)?.let { return it }
        val active = renderer ?: return null
        return try {
            active.render(index, bucket).also { bitmapCache.put(key, it) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun recentThumbnail(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val file = thumbnailFile(uri)
        if (file.isFile) BitmapFactory.decodeFile(file.path) else null
    }

    private suspend fun saveThumbnail(uri: Uri) {
        val bitmap = renderPage(versionCounter, 0, 320) ?: return
        withContext(Dispatchers.IO) {
            runCatching { thumbnailFile(uri).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) } }
        }
    }

    private fun thumbnailFile(uri: Uri): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray())
        return File(FileStore.thumbsDir(context), digest.joinToString("") { "%02x".format(it) } + ".jpg")
    }

    suspend fun pageLayout(index: Int): PageTextLayout? {
        val current = session ?: return null
        val version = current.versionId
        return layoutMutex.withLock {
            layouts["$version:$index"]?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                runCatching {
                    val open = layoutDocument?.takeIf { it.first == version }?.second
                        ?: PdfIo.load(current.current).also { layoutDocument = version to it }
                    PageTextAnalyzer.analyze(open, index)
                }.getOrNull()
            }?.also { layouts["$version:$index"] = it }
        }
    }

    private suspend fun closeLayoutDocument() {
        layoutMutex.withLock {
            layoutDocument?.second?.let { runCatching { it.close() } }
            layoutDocument = null
            layouts.clear()
        }
    }

    // endregion

    // region Search

    fun runSearch(query: String) {
        searchJob?.cancel()
        val pages = document?.pageCount ?: return
        if (query.isBlank()) {
            search = SearchState()
            return
        }
        search = SearchState(query = query, running = true)
        searchJob = viewModelScope.launch {
            val hits = mutableListOf<SearchHit>()
            for (index in 0 until pages) {
                val layout = pageLayout(index) ?: continue
                layout.find(query).forEach { rects -> hits += SearchHit(index, rects) }
                search = search.copy(hits = hits.toList())
            }
            search = search.copy(running = false)
        }
    }

    fun moveSearch(step: Int) {
        val hits = search.hits
        if (hits.isEmpty()) return
        search = search.copy(current = (search.current + step + hits.size) % hits.size)
    }

    fun clearSearch() {
        searchJob?.cancel()
        search = SearchState()
    }

    // endregion

    // region Editing

    fun undo() = changeVersion { it.undo() }

    fun redo() = changeVersion { it.redo() }

    private fun changeVersion(step: (DocumentSession) -> Boolean) {
        viewModelScope.launch {
            editMutex.withLock {
                val current = session ?: return@withLock
                if (step(current)) refreshDocument()
            }
        }
    }

    /** Applies [edit] to a copy of the current version and makes the result the new version. */
    private fun editDocument(
        busyMessage: String?,
        doneMessage: String?,
        edit: suspend (PDDocument) -> String?
    ): Job = launchTask(busyMessage) {
            editMutex.withLock {
                val current = session ?: return@withLock
                val output = current.newVersionFile()
                val note = withContext(Dispatchers.IO) {
                    try {
                        PdfIo.load(current.current).use { pdf ->
                            val note = edit(pdf)
                            PdfIo.save(pdf, output)
                            note
                        }
                    } catch (exception: Throwable) {
                        output.delete()
                        throw exception
                    }
                }
                current.commit(output)
                refreshDocument()
                val text = listOfNotNull(doneMessage, note).joinToString(" · ")
                if (text.isNotEmpty()) events.send(UiEvent.Message(text, offerUndo = true))
            }
        }

    private fun TextEditOutcome.note(): String? =
        if (exactFont) null else "Original font lacks some characters; used $fontLabel"

    fun replaceText(pageIndex: Int, blockId: String, originalText: String, newText: String): Job? {
        if (newText == originalText) return null
        return editDocument(null, "Text updated") { pdf ->
            TextEditor.replaceText(pdf, pageIndex, blockId, originalText, newText).note()
        }
    }

    fun addText(pageIndex: Int, origin: NormPoint, text: String, style: TextStyleHint): Job =
        editDocument(null, "Text added") { pdf -> TextEditor.addText(pdf, pageIndex, origin, text, style).note() }

    /** Burns pending drawings and text highlights (page index → marks) into the document in one step. */
    fun applyAnnotations(ink: Map<Int, List<InkStroke>>, highlights: Map<Int, List<Pair<NormRect, Int>>>) {
        if (ink.values.all { it.isEmpty() } && highlights.values.all { it.isEmpty() }) return
        editDocument("Applying…", "Annotations applied") { pdf ->
            highlights.forEach { (page, marks) ->
                marks.groupBy({ it.second }, { it.first }).forEach { (color, rects) -> pdf.highlightAreas(page, rects, color) }
            }
            ink.forEach { (page, strokes) -> pdf.drawInk(page, strokes) }
            null
        }
    }

    fun placeSignature(pageIndex: Int, bitmap: Bitmap, rect: NormRect) {
        editDocument("Placing signature…", "Signature added") { pdf ->
            pdf.placeImage(pageIndex, bitmap, rect)
            null
        }
    }

    fun organize(slots: List<PageSlot>) {
        editDocument("Updating pages…", "Pages updated") { pdf ->
            pdf.reorganize(slots)
            null
        }
    }

    fun rotatePages(pageIndexes: List<Int>, degrees: Int) {
        editDocument("Rotating…", "Rotated ${pageIndexes.size} page(s)") { pdf ->
            pdf.rotatePages(pageIndexes, degrees)
            null
        }
    }

    fun deletePages(pageIndexes: Set<Int>) {
        val count = document?.pageCount ?: return
        if (pageIndexes.size >= count) {
            message("A PDF needs at least one page.")
            return
        }
        editDocument("Removing pages…", "Removed ${pageIndexes.size} page(s)") { pdf ->
            pdf.reorganize((0 until pdf.numberOfPages).filter { it !in pageIndexes }.map { PageSlot(it, 0) })
            null
        }
    }

    fun crop(pageIndexes: List<Int>, rect: NormRect) {
        editDocument("Cropping…", "Cropped ${pageIndexes.size} page(s)") { pdf ->
            pdf.cropPages(pageIndexes, rect)
            null
        }
    }

    fun watermark(options: WatermarkOptions) {
        editDocument("Adding watermark…", "Watermark added") { pdf ->
            pdf.addWatermark(options)
            null
        }
    }

    fun pageNumbers(options: PageNumberOptions) {
        editDocument("Numbering pages…", "Page numbers added") { pdf ->
            pdf.addPageNumbers(options)
            null
        }
    }

    fun updateProperties(title: String, author: String, subject: String, keywords: String) {
        editDocument(null, "Properties saved") { pdf ->
            pdf.setMetadata(title, author, subject, keywords)
            null
        }
    }

    fun fillForm(values: Map<String, String>, flatten: Boolean) {
        editDocument("Filling form…", if (flatten) "Form filled and flattened" else "Form filled") { pdf ->
            pdf.fillForm(values, flatten)
            null
        }
    }

    fun compress(level: CompressionLevel) {
        val before = session?.current?.length() ?: return
        launchTask("Compressing…") {
            editMutex.withLock {
                val current = session ?: return@withLock
                val output = current.newVersionFile()
                withContext(Dispatchers.IO) {
                    PdfIo.load(current.current).use { pdf ->
                        pdf.compress(level)
                        PdfIo.save(pdf, output)
                    }
                }
                val after = output.length()
                if (after >= before * 0.98) {
                    output.delete()
                    events.send(UiEvent.Message("This PDF is already well optimized."))
                    return@withLock
                }
                current.commit(output)
                refreshDocument()
                val saved = ((before - after) * 100 / before).toInt()
                events.send(
                    UiEvent.Message(
                        "Reduced ${FileStore.formatSize(before)} → ${FileStore.formatSize(after)} (−$saved%)",
                        offerUndo = true
                    )
                )
            }
        }
    }

    fun makeSearchable() {
        launchTask("Recognizing text…") {
            editMutex.withLock {
                val current = session ?: return@withLock
                val output = current.newVersionFile()
                val pages = try {
                    withContext(Dispatchers.IO) {
                        Conversions.makeSearchable(current.current, output) { done, total ->
                            busy = BusyState("Recognizing text… page $done of $total", done / total.toFloat())
                        }
                    }
                } catch (exception: Throwable) {
                    output.delete()
                    throw exception
                }
                current.commit(output)
                refreshDocument()
                events.send(UiEvent.Message("Made $pages page(s) searchable", offerUndo = true))
            }
        }
    }

    fun appendPdf(uri: Uri) {
        launchTask("Adding PDF…") {
            editMutex.withLock {
                val current = session ?: return@withLock
                val incoming = File(current.dir, "incoming.pdf")
                val output = current.newVersionFile()
                try {
                    withContext(Dispatchers.IO) {
                        FileStore.copyIn(context, uri, incoming)
                        PdfFiles.merge(listOf(current.current, incoming), output)
                    }
                } catch (exception: Throwable) {
                    output.delete()
                    throw exception
                } finally {
                    incoming.delete()
                }
                current.commit(output)
                refreshDocument()
                events.send(UiEvent.Message("Added ${FileStore.displayName(context, uri)}", offerUndo = true))
            }
        }
    }

    suspend fun details(): PdfDetails? {
        val current = session ?: return null
        return withContext(Dispatchers.IO) { runCatching { PdfIo.details(current.current) }.getOrNull() }
    }

    suspend fun formFields(): List<FormFieldInfo> {
        val current = session ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { PdfIo.load(current.current).use { it.formFields() } }.getOrDefault(emptyList())
        }
    }

    // endregion

    // region Security

    fun setPassword(password: String) {
        val current = session ?: return
        current.protection = ProtectionSettings(password, current.originalPermissions)
        updateFlags()
        message("Password will be applied when you save")
    }

    fun removePassword() {
        val current = session ?: return
        if (!current.canRemovePassword) {
            message("Open the PDF with its password to remove protection.")
            return
        }
        current.protection = if (current.wasEncrypted && current.openPassword.isEmpty()) {
            ProtectionSettings("", current.originalPermissions)
        } else {
            null
        }
        updateFlags()
        message("Password will be removed when you save")
    }

    // endregion

    // region Saving & exporting

    private fun exportName(suffix: String, extension: String): String {
        val base = FileStore.baseName(session?.name ?: "Document")
        return if (suffix.isEmpty()) "$base.$extension" else "${base}_$suffix.$extension"
    }

    /** Saves the document: the file is fully written and verified before the save picker opens. */
    fun saveCopy() {
        val current = session ?: return
        prepareExport(ExportRequest(exportName(if (current.isDirty && current.sourceUri != null) "edited" else "", "pdf"), PDF), marksSaved = true) { output ->
            PdfIo.writeExport(current.current, output, current.protection)
        }
    }

    fun exportExtract(pages: List<Int>) {
        val current = session ?: return
        prepareExport(ExportRequest(exportName("pages", "pdf"), PDF)) { output ->
            val unprotected = File(output.parentFile, "extract_tmp.pdf")
            try {
                PdfFiles.extract(current.current, pages, unprotected)
                PdfIo.writeExport(unprotected, output, current.protection)
            } finally {
                unprotected.delete()
            }
        }
    }

    fun exportSplit(groups: List<List<Int>>) {
        val current = session ?: return
        prepareExport(ExportRequest(exportName("split", "zip"), ZIP)) { output ->
            PdfFiles.splitToZip(current.current, FileStore.baseName(current.name), groups, output.parentFile!!, output)
        }
    }

    fun exportImages(pages: List<Int>, dpi: Int) {
        val current = session ?: return
        prepareExport(ExportRequest(exportName("images", "zip"), ZIP)) { output ->
            Conversions.pagesToImagesZip(current.current, FileStore.baseName(current.name), pages, dpi, output) { done, total ->
                busy = BusyState("Rendering page $done of $total", done / total.toFloat())
            }
        }
    }

    fun exportText() {
        val current = session ?: return
        prepareExport(ExportRequest(exportName("text", "txt"), "text/plain")) { output ->
            PdfIo.extractText(current.current, output)
        }
    }

    private fun prepareExport(request: ExportRequest, marksSaved: Boolean = false, write: suspend (File) -> Unit) {
        launchTask("Preparing file…") {
            discardPendingExport()
            val output = File(FileStore.exportDir(context), "${UUID.randomUUID()}.${request.fileName.substringAfterLast('.')}")
            try {
                withContext(Dispatchers.IO) { write(output) }
                check(output.isFile && output.length() > 0) { "The file could not be created." }
            } catch (exception: Throwable) {
                output.delete()
                throw exception
            }
            pendingExport = PendingExport(output, request, marksSaved)
            events.send(UiEvent.CreateDocument(request))
        }
    }

    /** Called with the location the user picked, or null if they cancelled. */
    fun onExportTarget(target: Uri?) {
        val pending = pendingExport ?: run {
            target?.let { FileStore.deleteDocument(context, it) }
            return
        }
        pendingExport = null
        if (target == null) {
            pending.file.delete()
            return
        }
        launchTask("Saving…") {
            try {
                withContext(Dispatchers.IO) { FileStore.copyOut(context, pending.file, target) }
            } finally {
                pending.file.delete()
            }
            if (pending.marksSaved) {
                session?.markSaved()
                updateFlags()
            }
            events.send(UiEvent.Message("Saved ${pending.request.fileName}"))
        }
    }

    private fun discardPendingExport() {
        pendingExport?.file?.delete()
        pendingExport = null
    }

    fun share() {
        val current = session ?: return
        launchTask("Preparing to share…") {
            val uri = withContext(Dispatchers.IO) {
                val prepared = File(FileStore.exportDir(context), "share_${UUID.randomUUID()}.pdf")
                try {
                    PdfIo.writeExport(current.current, prepared, current.protection)
                    FileStore.shareUri(context, prepared, exportName("", "pdf"))
                } finally {
                    prepared.delete()
                }
            }
            events.send(UiEvent.Share(uri, PDF))
        }
    }

    fun print() {
        val current = session ?: return
        viewModelScope.launch { events.send(UiEvent.Print(current.current, current.name)) }
    }

    // endregion

    // region Merge & images

    fun addMergeItems(uris: List<Uri>) {
        uris.forEach { uri ->
            if (mergeItems.none { it.uri == uri }) {
                val size = runCatching {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                }.getOrNull() ?: -1L
                mergeItems += MergeItem(UUID.randomUUID().toString(), uri, FileStore.displayName(context, uri), size)
            }
        }
    }

    fun moveMergeItem(from: Int, to: Int) {
        if (from !in mergeItems.indices || to !in mergeItems.indices) return
        mergeItems.add(to, mergeItems.removeAt(from))
    }

    fun removeMergeItem(id: String) {
        mergeItems.removeAll { it.id == id }
    }

    fun runMerge() {
        val items = mergeItems.toList()
        if (items.size < 2) {
            message("Add at least two PDFs.")
            return
        }
        launchTask("Merging ${items.size} PDFs…") {
            val dir = newSessionDir()
            try {
                val output = File(dir, "v0.pdf")
                withContext(Dispatchers.IO) {
                    val inputs = items.mapIndexed { index, item ->
                        File(dir, "in_$index.pdf").also { file ->
                            FileStore.copyIn(context, item.uri, file)
                            try {
                                PdfIo.load(file).close()
                            } catch (_: PdfPasswordException) {
                                throw IllegalArgumentException("${item.name} is password protected. Unlock it first.")
                            }
                        }
                    }
                    PdfFiles.merge(inputs, output)
                    inputs.forEach(File::delete)
                }
                mergeItems.clear()
                openGenerated(output, dir, "Merged.pdf")
            } catch (exception: Throwable) {
                dir.deleteRecursively()
                throw exception
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        uris.forEach { uri ->
            imageItems += ImageItem(UUID.randomUUID().toString(), uri, FileStore.displayName(context, uri))
        }
    }

    fun moveImage(from: Int, to: Int) {
        if (from !in imageItems.indices || to !in imageItems.indices) return
        imageItems.add(to, imageItems.removeAt(from))
    }

    fun rotateImage(id: String) {
        val index = imageItems.indexOfFirst { it.id == id }
        if (index >= 0) imageItems[index] = imageItems[index].let { it.copy(rotation = (it.rotation + 90) % 360) }
    }

    fun removeImage(id: String) {
        imageItems.removeAll { it.id == id }
    }

    fun runImagesToPdf(pageSize: ImagePageSize, margins: Boolean) {
        val items = imageItems.toList()
        if (items.isEmpty()) {
            message("Add at least one image.")
            return
        }
        launchTask("Creating PDF…") {
            val dir = newSessionDir()
            try {
                val output = File(dir, "v0.pdf")
                withContext(Dispatchers.IO) {
                    Conversions.imagesToPdf(
                        context,
                        items.map { ImageSource(it.uri, it.rotation) },
                        pageSize,
                        margins,
                        dir,
                        output
                    )
                }
                openGenerated(output, dir, "Images.pdf", returnTo = Screen.IMAGES)
            } catch (exception: Throwable) {
                dir.deleteRecursively()
                throw exception
            }
        }
    }

    // endregion

    fun message(text: String) {
        viewModelScope.launch { events.send(UiEvent.Message(text)) }
    }

    private fun launchTask(busyMessage: String?, block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch {
            if (busyMessage != null) busy = BusyState(busyMessage)
            try {
                block()
            } catch (exception: CancellationException) {
                throw exception
            } catch (error: Throwable) {
                events.send(UiEvent.Message(error.userMessage()))
            } finally {
                if (busyMessage != null) busy = null
            }
        }

    override fun onCleared() {
        discardPendingExport()
        val closing = session
        session = null
        val activeRenderer = renderer
        renderer = null
        CoroutineScope(Dispatchers.IO).launch {
            activeRenderer?.close()
            closeLayoutDocument()
            closing?.close()
        }
    }

    private companion object {
        const val PDF = "application/pdf"
        const val ZIP = "application/zip"
    }
}

fun Throwable.userMessage(): String = when (this) {
    is PdfPasswordException -> message ?: "This PDF is password protected."
    is TextEditException -> message ?: "This text can't be edited."
    is OutOfMemoryError -> "Not enough memory to finish. Try a smaller file or fewer pages."
    is IllegalArgumentException, is IllegalStateException -> message ?: "Something went wrong."
    is IOException -> message?.takeIf { it.length < 140 } ?: "The file couldn't be read or written."
    else -> "Something went wrong: ${this::class.java.simpleName}"
}
