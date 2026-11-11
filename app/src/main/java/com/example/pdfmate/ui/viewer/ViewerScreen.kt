package com.example.pdfmate.ui.viewer

import android.graphics.Bitmap
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.pdfmate.core.pdf.FontFamilyHint
import com.example.pdfmate.core.pdf.NormPoint
import com.example.pdfmate.core.pdf.NormRect
import com.example.pdfmate.core.pdf.PageTextSelection
import com.example.pdfmate.core.pdf.PageTextLayout
import com.example.pdfmate.core.pdf.PageTextPosition
import com.example.pdfmate.ui.AppViewModel
import com.example.pdfmate.ui.DocumentUi
import com.example.pdfmate.ui.Screen
import com.example.pdfmate.ui.ToolId
import com.example.pdfmate.ui.ViewerMode
import com.example.pdfmate.ui.components.BitmapView
import com.example.pdfmate.ui.components.ChoicePill
import com.example.pdfmate.ui.components.CompactToolTile
import com.example.pdfmate.ui.components.ToolCatalog
import com.example.pdfmate.ui.components.ToolCategory
import com.example.pdfmate.ui.theme.BrandGradient
import com.example.pdfmate.ui.theme.EditViolet
import com.example.pdfmate.ui.theme.LocalDarkTheme
import com.example.pdfmate.ui.theme.ViewerCanvasDark
import com.example.pdfmate.ui.theme.ViewerCanvasLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 8f

private data class TextSelectionHandlePosition(val handle: TextSelectionHandle, val point: Offset)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(vm: AppViewModel) {
    val doc = vm.document
    if (doc == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val state = remember { ViewerState() }
    val listState = rememberLazyListState()
    val mode = vm.viewerMode
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    val toolsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDropMarks by remember { mutableStateOf(false) }
    var dockVisible by remember { mutableStateOf(true) }
    var signedNoticeDismissed by rememberSaveable { mutableStateOf(false) }
    var textSelections by remember(doc.versionKey) { mutableStateOf(emptyMap<Int, PageTextSelection>()) }
    var textSelectionRange by remember(doc.versionKey) { mutableStateOf<TextSelectionRange?>(null) }
    var selectionRevision by remember(doc.versionKey) { mutableIntStateOf(0) }
    var selectingAllText by remember { mutableStateOf(false) }

    val currentPage by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .firstOrNull { center in it.offset..(it.offset + it.size) }?.index
                ?: info.visibleItemsInfo.firstOrNull()?.index
                ?: 0
        }
    }

    fun exitMode() {
        state.cancelEditing()
        state.addTextMode = false
        state.clearMarks()
        state.signature = null
        vm.viewerMode = ViewerMode.VIEW
    }

    fun startSignaturePlacement(bitmap: Bitmap) {
        state.signature = bitmap
        state.signaturePage = currentPage.coerceIn(0, doc.pageCount - 1)
        state.signatureRect = NormRect(0.52f, 0.74f, 0.9f, 0.82f)
        vm.activeSheet = null
        vm.viewerMode = ViewerMode.SIGN
    }

    val appendLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::appendPdf)
    }
    val signatureImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { runCatching { loadSignatureImage(context, uri) }.getOrNull() }
                if (bitmap == null) vm.message("That image couldn't be opened.") else startSignaturePlacement(bitmap)
            }
        }
    }

    fun handleTool(tool: ToolId) {
        toolsOpen = false
        textSelections = emptyMap()
        textSelectionRange = null
        selectionRevision++
        exitMode()
        when (tool) {
            ToolId.EDIT_TEXT -> vm.viewerMode = ViewerMode.EDIT
            ToolId.ADD_TEXT -> {
                state.addTextMode = true
                vm.viewerMode = ViewerMode.EDIT
            }
            ToolId.DRAW -> vm.viewerMode = ViewerMode.ANNOTATE
            ToolId.CROP -> {
                state.cropPage = currentPage
                state.cropRect = NormRect(0.08f, 0.08f, 0.92f, 0.92f)
                state.cropAllPages = false
                vm.viewerMode = ViewerMode.CROP
            }
            ToolId.ORGANIZE -> vm.navigate(Screen.ORGANIZE)
            ToolId.IMAGES_TO_PDF -> vm.navigate(Screen.IMAGES)
            else -> vm.activeSheet = tool
        }
    }

    LaunchedEffect(vm.pendingTool) {
        val tool = vm.pendingTool ?: return@LaunchedEffect
        vm.pendingTool = null
        handleTool(tool)
    }

    fun commitEdit() {
        val target = state.editTarget ?: return
        if (state.clearEditAtVersion != null) return
        val text = state.editValue.text
        val job = when (target) {
            is EditTarget.Existing -> vm.replaceText(target.pageIndex, target.block.id, target.block.text, text)
            is EditTarget.New -> if (text.isBlank()) null else vm.addText(target.pageIndex, target.origin, text, target.style)
        }
        keyboard?.hide()
        if (job == null) {
            state.cancelEditing()
            return
        }
        state.clearEditAtVersion = Int.MAX_VALUE
        scope.launch {
            job.join()
            state.clearEditAtVersion = vm.document?.versionKey ?: 0
        }
    }

    suspend fun loadSelections(range: TextSelectionRange): Map<Int, PageTextSelection> = buildMap {
        for (pageIndex in range.start.pageIndex..range.end.pageIndex) {
            val layout = vm.pageLayout(pageIndex) ?: continue
            val start = if (pageIndex == range.start.pageIndex) range.start.characterIndex else 0
            val end = if (pageIndex == range.end.pageIndex) range.end.characterIndex else layout.characterCount
            layout.selectRange(start, end)?.let { put(pageIndex, it) }
        }
    }

    fun applyTextSelection(range: TextSelectionRange) {
        val revision = ++selectionRevision
        scope.launch {
            val selections = loadSelections(range)
            if (revision == selectionRevision) {
                textSelectionRange = range
                textSelections = selections
            }
        }
    }

    fun selectWord(pageIndex: Int, point: NormPoint) {
        val revision = ++selectionRevision
        scope.launch {
            val selection = vm.pageLayout(pageIndex)?.selectWordAt(point)
            if (revision != selectionRevision) return@launch
            if (selection == null) {
                vm.message("No selectable text at this location.")
            } else {
                applyTextSelection(
                    TextSelectionRange(
                        PageTextPosition(pageIndex, selection.startIndex),
                        PageTextPosition(pageIndex, selection.endIndex)
                    )
                )
            }
        }
    }

    fun selectCurrentPageText() {
        if (selectingAllText) return
        val pageIndex = currentPage
        val revision = ++selectionRevision
        scope.launch {
            selectingAllText = true
            try {
                val selection = vm.pageLayout(pageIndex)?.selectAll()
                if (revision != selectionRevision) return@launch
                if (selection == null) {
                    vm.message("This page has no selectable text.")
                } else {
                    textSelectionRange = TextSelectionRange(
                        PageTextPosition(pageIndex, selection.startIndex),
                        PageTextPosition(pageIndex, selection.endIndex)
                    )
                    textSelections = mapOf(pageIndex to selection)
                }
            } finally {
                selectingAllText = false
            }
        }
    }

    fun selectAllPagesText() {
        if (selectingAllText) return
        val revision = ++selectionRevision
        scope.launch {
            selectingAllText = true
            try {
                val selections = buildMap {
                    for (pageIndex in 0 until doc.pageCount) {
                        vm.pageLayout(pageIndex)?.selectAll()?.let { put(pageIndex, it) }
                    }
                }
                if (revision != selectionRevision) return@launch
                val first = selections.entries.firstOrNull()
                val last = selections.entries.lastOrNull()
                if (first == null || last == null) {
                    vm.message("This PDF has no selectable text.")
                } else {
                    textSelectionRange = TextSelectionRange(
                        PageTextPosition(first.key, first.value.startIndex),
                        PageTextPosition(last.key, last.value.endIndex)
                    )
                    textSelections = selections
                }
            } finally {
                selectingAllText = false
            }
        }
    }

    fun clearTextSelection() {
        selectionRevision++
        textSelectionRange = null
        textSelections = emptyMap()
    }

    fun dragSelectionHandle(handle: TextSelectionHandle, point: Offset, pageLeft: Float, pageWidth: Float) {
        val revision = ++selectionRevision
        val fingerY = point.y
        val layoutInfo = listState.layoutInfo
        val edgeDistance = with(density) { 48.dp.toPx() }
        when {
            fingerY < layoutInfo.viewportStartOffset + edgeDistance -> scope.launch { listState.scrollBy(-18f) }
            fingerY > layoutInfo.viewportEndOffset - edgeDistance -> scope.launch { listState.scrollBy(18f) }
        }
        val targetItem = layoutInfo.visibleItemsInfo.minByOrNull { item ->
            when {
                fingerY < item.offset -> item.offset - fingerY
                fingerY > item.offset + item.size -> fingerY - (item.offset + item.size)
                else -> 0f
            }
        } ?: return
        val targetPoint = NormPoint(
            ((point.x - pageLeft) / pageWidth).coerceIn(0f, 1f),
            ((fingerY - targetItem.offset) / targetItem.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        )
        scope.launch {
            val characterIndex = vm.pageLayout(targetItem.index)?.characterIndexAt(targetPoint) ?: return@launch
            if (revision != selectionRevision) return@launch
            val current = textSelectionRange ?: return@launch
            val candidate = PageTextPosition(
                targetItem.index,
                if (handle == TextSelectionHandle.END) characterIndex + 1 else characterIndex
            )
            val next = when (handle) {
                TextSelectionHandle.START -> if (candidate < current.end) current.copy(start = candidate) else current
                TextSelectionHandle.END -> if (candidate > current.start) current.copy(end = candidate) else current
            }
            val selections = loadSelections(next)
            if (revision == selectionRevision) {
                textSelectionRange = next
                textSelections = selections
            }
        }
    }

    fun copySelectedText() {
        val text = textSelections.toSortedMap().values.joinToString("\n") { it.text }
        if (text.isBlank()) return
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("PDF text", text))
        vm.message("Selected text copied")
    }

    BackHandler {
        when {
            textSelections.isNotEmpty() -> clearTextSelection()
            state.editTarget != null -> state.cancelEditing()
            mode == ViewerMode.ANNOTATE && state.hasPendingMarks -> confirmDropMarks = true
            mode != ViewerMode.VIEW -> exitMode()
            searchOpen -> {
                searchOpen = false
                vm.clearSearch()
            }
            doc.dirty -> confirmLeave = true
            else -> vm.back()
        }
    }

    // Bring the current search hit into view.
    val search = vm.search
    val currentHit = search.hits.getOrNull(search.current)
    var viewport by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    LaunchedEffect(search.query, search.current, currentHit != null) {
        val hit = currentHit ?: return@LaunchedEffect
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == hit.pageIndex }
        val pageHeight = item?.size ?: (viewport.width / doc.pageSizes[hit.pageIndex].aspectRatio).roundToInt()
        val top = hit.rects.minOf { it.top }
        listState.animateScrollToItem(hit.pageIndex, (top * pageHeight - viewport.height / 3f).roundToInt().coerceAtLeast(0))
    }
    val pendingViewerPage = vm.pendingViewerPage
    LaunchedEffect(pendingViewerPage, doc.versionKey) {
        val pageIndex = pendingViewerPage ?: return@LaunchedEffect
        listState.animateScrollToItem(pageIndex.coerceIn(0, doc.pageCount - 1))
        vm.consumePendingViewerPage(pageIndex)
    }
    val hitsByPage = remember(search.hits) { search.hits.groupBy({ it.pageIndex }, { it.rects }).mapValues { it.value.flatten() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        if (searchOpen) {
            SearchBar(vm, onClose = {
                searchOpen = false
                vm.clearSearch()
            })
        } else {
            ViewerTopBar(
                doc = doc,
                currentPage = currentPage,
                editing = mode == ViewerMode.EDIT,
                onBack = {
                    when {
                        textSelections.isNotEmpty() -> clearTextSelection()
                        doc.dirty -> confirmLeave = true
                        else -> vm.back()
                    }
                },
                onUndo = vm::undo,
                onRedo = vm::redo,
                onSearch = {
                    exitMode()
                    searchOpen = true
                },
                onTogglePen = {
                    if (mode == ViewerMode.EDIT) exitMode() else handleTool(ToolId.EDIT_TEXT)
                },
                onSave = vm::saveCopy,
                onShare = vm::share,
                onPrint = vm::print,
                onTool = ::handleTool
            )
        }
        AnimatedVisibility(doc.signed && !signedNoticeDismissed) {
            Notice(
                icon = Icons.Rounded.Verified,
                text = "This PDF is digitally signed. Any change will invalidate the signature.",
                onDismiss = { signedNoticeDismissed = true }
            )
        }
        if (textSelections.isNotEmpty() && mode == ViewerMode.VIEW) {
            TextSelectionToolbar(
                selectedCharacters = textSelections.values.sumOf { it.text.length },
                selectingAll = selectingAllText,
                onSelectCurrentPage = ::selectCurrentPageText,
                onSelectAllPages = ::selectAllPagesText,
                onCopy = ::copySelectedText,
                onClear = ::clearTextSelection
            )
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (LocalDarkTheme.current) ViewerCanvasDark else ViewerCanvasLight)
                .clipToBounds()
        ) {
            viewport = androidx.compose.ui.unit.IntSize(constraints.maxWidth, constraints.maxHeight)
            val hScroll = rememberScrollState()
            var pinchScale by remember { mutableFloatStateOf(1f) }
            var pinchBaseZoom by remember { mutableFloatStateOf(state.zoom) }
            var pinchOrigin by remember { mutableStateOf(Offset.Zero) }
            var pinchTranslation by remember { mutableStateOf(Offset.Zero) }
            val contentWidth = maxWidth * state.zoom
            val sidePadding = if (state.zoom > 1f) 8.dp else 14.dp
            val pageWidth = contentWidth - sidePadding * 2
            val pageWidthPx = with(density) { pageWidth.toPx() }
            val pageLeft = with(density) { sidePadding.toPx() } - hScroll.value
            val textHandlePositions by remember(pageWidthPx, pageLeft, textSelections, textSelectionRange) {
                derivedStateOf {
                    val visiblePages = listState.layoutInfo.visibleItemsInfo.associateBy { it.index }
                    buildList {
                        textSelectionRange?.let { range ->
                            val startPage = visiblePages[range.start.pageIndex]
                            val startRect = textSelections[range.start.pageIndex]?.rects?.firstOrNull()
                            if (startPage != null && startRect != null) {
                                add(
                                    TextSelectionHandlePosition(
                                        TextSelectionHandle.START,
                                        Offset(pageLeft + startRect.left * pageWidthPx, startPage.offset + startRect.bottom * startPage.size)
                                    )
                                )
                            }
                            val endPage = visiblePages[range.end.pageIndex]
                            val endRect = textSelections[range.end.pageIndex]?.rects?.lastOrNull()
                            if (endPage != null && endRect != null) {
                                add(
                                    TextSelectionHandlePosition(
                                        TextSelectionHandle.END,
                                        Offset(pageLeft + endRect.right * pageWidthPx, endPage.offset + endRect.bottom * endPage.size)
                                    )
                                )
                            }
                        }
                    }
                }
            }
            val currentTextHandles = rememberUpdatedState(textHandlePositions)
            val currentPageLeft = rememberUpdatedState(pageLeft)
            val currentPageWidth = rememberUpdatedState(pageWidthPx)
            val currentHandleDrag = rememberUpdatedState<(TextSelectionHandle, Offset) -> Unit> { handle, point ->
                dragSelectionHandle(handle, point, currentPageLeft.value, currentPageWidth.value)
            }

            val applyZoom by rememberUpdatedState { requested: Float, focus: Offset, gestureTranslation: Offset ->
                val old = state.zoom
                val target = requested.coerceIn(MIN_ZOOM, MAX_ZOOM)
                if (abs(target - old) > 0.01f || gestureTranslation != Offset.Zero) {
                    val info = listState.layoutInfo
                    val anchor = info.visibleItemsInfo.minByOrNull { item ->
                        when {
                            focus.y < item.offset -> item.offset - focus.y
                            focus.y > item.offset + item.size -> focus.y - (item.offset + item.size)
                            else -> 0f
                        }
                    }
                    val oldSidePadding = if (old > 1f) 8.dp else 14.dp
                    val newSidePadding = if (target > 1f) 8.dp else 14.dp
                    val oldPageWidth = with(density) { (maxWidth * old - oldSidePadding * 2).toPx() }.coerceAtLeast(1f)
                    val newPageWidth = with(density) { (maxWidth * target - newSidePadding * 2).toPx() }.coerceAtLeast(1f)
                    val pageX = (focus.x + hScroll.value - with(density) { oldSidePadding.toPx() })
                        .div(oldPageWidth)
                        .coerceIn(0f, 1f)
                    val targetFocus = focus + gestureTranslation
                    val horizontalTarget = with(density) { newSidePadding.toPx() } + pageX * newPageWidth - targetFocus.x
                    val pointWithinAnchor = anchor?.let { (focus.y - it.offset).coerceIn(0f, it.size.toFloat()) }
                    val scaleRatio = newPageWidth / oldPageWidth
                    pinchBaseZoom = old
                    pinchScale = target / old
                    pinchOrigin = focus
                    pinchTranslation = gestureTranslation
                    state.zoom = target
                    scope.launch {
                        withFrameNanos { }
                        if (anchor != null && pointWithinAnchor != null) {
                            val desiredOffset = targetFocus.y - pointWithinAnchor * scaleRatio
                            listState.scrollToItem(anchor.index, -desiredOffset.roundToInt())
                        }
                        hScroll.scrollTo(horizontalTarget.roundToInt().coerceIn(0, hScroll.maxValue))
                        pinchTranslation = Offset.Zero
                        pinchScale = 1f
                        pinchBaseZoom = target
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val handle = currentTextHandles.value
                                .minByOrNull { (it.point - down.position).getDistance() }
                                ?.takeIf { (it.point - down.position).getDistance() <= with(density) { 28.dp.toPx() } }
                            if (handle != null) {
                                down.consume()
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    currentHandleDrag.value(handle.handle, change.position)
                                }
                            } else {
                                var pinching = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val pressed = event.changes.count { it.pressed }
                                    if (pressed == 0) break
                                    if (pressed >= 2) {
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        if (!pinching) {
                                            pinching = true
                                            pinchOrigin = centroid
                                            pinchTranslation = Offset.Zero
                                        }
                                        val pan = event.calculatePan()
                                        val previousScale = pinchScale
                                        val nextScale = (previousScale * event.calculateZoom())
                                            .coerceIn(MIN_ZOOM / state.zoom, MAX_ZOOM / state.zoom)
                                        val effectiveScale = nextScale / previousScale
                                        pinchTranslation = accumulatePinchTranslation(
                                            origin = pinchOrigin,
                                            translation = pinchTranslation,
                                            centroid = centroid,
                                            pan = pan,
                                            scaleChange = effectiveScale
                                        )
                                        pinchScale = nextScale
                                        event.changes.forEach { it.consume() }
                                    } else if (pinching) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                if (pinching) {
                                    val scale = pinchScale
                                    val origin = pinchOrigin
                                    val translation = pinchTranslation
                                    applyZoom(state.zoom * scale, origin, translation)
                                }
                            }
                        }
                    }
                    .pointerInput(mode) {
                        if (mode != ViewerMode.VIEW) return@pointerInput
                        detectTapGestures(
                            onTap = { dockVisible = !dockVisible },
                            onDoubleTap = { applyZoom(if (state.zoom > 1.3f) 1f else 2.5f, it, Offset.Zero) }
                        )
                    }
                    .graphicsLayer {
                        val layoutCompensation = pinchBaseZoom / state.zoom
                        scaleX = pinchScale * layoutCompensation
                        scaleY = pinchScale * layoutCompensation
                        translationX = pinchTranslation.x
                        translationY = pinchTranslation.y
                        transformOrigin = if (size.width > 0f) {
                            TransformOrigin(pinchOrigin.x / size.width, pinchOrigin.y / size.height)
                        } else {
                            TransformOrigin.Center
                        }
                    }
                    .horizontalScroll(hScroll, enabled = state.zoom > 1.01f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 16.dp, bottom = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(doc.pageCount, key = { it }) { index ->
                        PageItem(
                            vm = vm,
                            doc = doc,
                            index = index,
                            pageWidth = pageWidth,
                            state = state,
                            mode = mode,
                            hits = hitsByPage[index].orEmpty(),
                            currentHit = currentHit?.takeIf { it.pageIndex == index }?.rects.orEmpty(),
                            selection = textSelections[index],
                            onSelectText = ::selectWord,
                            onLockedText = {
                                vm.message("This text uses a font that can't be re-encoded. Use Add text to type over it.")
                            },
                            onSubmit = ::commitEdit
                        )
                    }
                }
            }

            TextSelectionHandles(textHandlePositions)

            PageIndicator(listState.isScrollInProgress, currentPage, doc.pageCount, Modifier.align(Alignment.TopCenter))
            FastPageScrollbar(listState, doc.pageCount, Modifier.align(Alignment.CenterEnd))

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                when (mode) {
                    ViewerMode.VIEW -> androidx.compose.animation.AnimatedVisibility(
                        visible = dockVisible,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        ViewerDock(
                            dirty = doc.dirty,
                            onPages = { handleTool(ToolId.ORGANIZE) },
                            onAnnotate = { handleTool(ToolId.DRAW) },
                            onSign = { handleTool(ToolId.SIGN) },
                            onTools = { toolsOpen = true },
                            onSave = vm::saveCopy
                        )
                    }
                    ViewerMode.EDIT -> EditBar(state, onExit = ::exitMode, onCommit = ::commitEdit)
                    ViewerMode.ANNOTATE -> AnnotateBar(
                        state = state,
                        onCancel = { if (state.hasPendingMarks) confirmDropMarks = true else exitMode() },
                        onApply = {
                            vm.applyAnnotations(state.ink.toMap(), state.highlights.toMap())
                            exitMode()
                        }
                    )
                    ViewerMode.CROP -> CropBar(
                        state = state,
                        onCancel = ::exitMode,
                        onApply = {
                            val pages = if (state.cropAllPages) (0 until doc.pageCount).toList() else listOf(state.cropPage)
                            vm.crop(pages, state.cropRect)
                            exitMode()
                        }
                    )
                    ViewerMode.SIGN -> SignBar(
                        page = state.signaturePage,
                        pageCount = doc.pageCount,
                        onMove = { page ->
                            state.signaturePage = page
                            scope.launch { listState.animateScrollToItem(page) }
                        },
                        onCancel = ::exitMode,
                        onPlace = {
                            state.signature?.let { vm.placeSignature(state.signaturePage, it, state.signatureRect) }
                            exitMode()
                        }
                    )
                }
            }
        }
    }

    if (toolsOpen || vm.activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = {
                toolsOpen = false
                vm.activeSheet = null
            },
            sheetState = toolsSheetState
        ) {
            val tool = vm.activeSheet
            if (tool == null) {
                ToolsGrid(onTool = ::handleTool)
            } else {
                ToolSheetContent(
                    tool,
                    SheetContext(
                        vm = vm,
                        document = doc,
                        currentPage = currentPage,
                        close = {
                            scope.launch { toolsSheetState.hide() }.invokeOnCompletion { vm.activeSheet = null }
                        },
                        pickPdfToAppend = { appendLauncher.launch(arrayOf("application/pdf")) },
                        pickSignatureImage = { signatureImageLauncher.launch(arrayOf("image/*")) },
                        onSignature = ::startSignaturePlacement
                    )
                )
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Save your changes?") },
            text = { Text("Your edits to “${doc.name}” haven't been saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.saveCopy()
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.back()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    if (confirmDropMarks) {
        AlertDialog(
            onDismissRequest = { confirmDropMarks = false },
            title = { Text("Discard drawing?") },
            text = { Text("Your marks haven't been applied to the PDF.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDropMarks = false
                    exitMode()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDropMarks = false }) { Text("Keep drawing") } }
        )
    }
}

// region Page

@Composable
private fun PageItem(
    vm: AppViewModel,
    doc: DocumentUi,
    index: Int,
    pageWidth: Dp,
    state: ViewerState,
    mode: ViewerMode,
    hits: List<NormRect>,
    currentHit: List<NormRect>,
    selection: PageTextSelection?,
    onSelectText: (Int, NormPoint) -> Unit,
    onLockedText: () -> Unit,
    onSubmit: () -> Unit
) {
    val pageSize = doc.pageSizes[index]
    val widthPx = with(LocalDensity.current) { pageWidth.roundToPx() }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderedVersion by remember { mutableIntStateOf(-1) }
    LaunchedEffect(doc.versionKey, widthPx) {
        // Let fast flings settle before rendering pages that are only passing by.
        if (bitmap != null && renderedVersion == doc.versionKey) delay(120)
        vm.renderPage(doc.versionKey, index, widthPx)?.let {
            bitmap = it
            renderedVersion = doc.versionKey
        }
    }
    val clearAt = state.clearEditAtVersion
    LaunchedEffect(clearAt, renderedVersion) {
        if (clearAt != null && clearAt != Int.MAX_VALUE && state.editTarget?.pageIndex == index && renderedVersion >= clearAt) {
            state.cancelEditing()
        }
    }
    val needsLayout = mode == ViewerMode.EDIT || (mode == ViewerMode.ANNOTATE && state.inkTool == InkTool.HIGHLIGHT_TEXT)
    val layout by produceState<PageTextLayout?>(null, doc.versionKey, index, needsLayout) {
        value = if (needsLayout) vm.pageLayout(index) else null
    }
    Box(
        Modifier
            .width(pageWidth)
            .aspectRatio(pageSize.aspectRatio)
            .shadow(5.dp, RoundedCornerShape(3.dp))
            .background(Color.White)
            .pointerInput(mode, doc.versionKey, selection != null) {
                if (mode == ViewerMode.VIEW && selection == null) {
                    detectTapGestures(onLongPress = { offset ->
                        onSelectText(
                            index,
                            NormPoint(
                                (offset.x / size.width).coerceIn(0f, 1f),
                                (offset.y / size.height).coerceIn(0f, 1f)
                            )
                        )
                    })
                }
            }
    ) {
        BitmapView(bitmap, Modifier.fillMaxSize())
        if (bitmap == null) {
            CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.Center), strokeWidth = 3.dp)
        }
        if (hits.isNotEmpty() || currentHit.isNotEmpty()) SearchHighlights(hits, currentHit)
        if (!selection?.rects.isNullOrEmpty()) TextSelectionHighlights(selection!!.rects)
        when (mode) {
            ViewerMode.EDIT -> EditOverlay(index, layout, state, bitmap, pageSize.width, onLockedText, onSubmit)
            ViewerMode.ANNOTATE -> InkOverlay(index, state, layout)
            ViewerMode.CROP -> if (index == state.cropPage || state.cropAllPages) CropOverlay(state)
            ViewerMode.SIGN -> state.signature?.takeIf { index == state.signaturePage }?.let { SignatureOverlay(state, it) }
            ViewerMode.VIEW -> Unit
        }
    }
}

@Composable
private fun TextSelectionHandles(handles: List<TextSelectionHandlePosition>) {
    val density = LocalDensity.current
    val handleWidth = with(density) { 24.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        handles.forEach { handle ->
            Box(Modifier.offset {
                IntOffset((handle.point.x - handleWidth / 2f).roundToInt(), handle.point.y.roundToInt())
            }.size(width = 24.dp, height = 30.dp)) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(3.dp)
                        .height(11.dp)
                        .background(Color(0xFF1A73E8), RoundedCornerShape(2.dp))
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A73E8))
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun FastPageScrollbar(listState: LazyListState, pageCount: Int, modifier: Modifier = Modifier) {
    if (pageCount < 4) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val progress by remember(listState, pageCount) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null || pageCount <= 1) 0f else {
                val withinPage = (-first.offset).coerceIn(0, first.size).toFloat() / first.size.coerceAtLeast(1)
                ((first.index + withinPage) / pageCount).coerceIn(0f, 1f)
            }
        }
    }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var currentTarget by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .width(30.dp)
            .padding(vertical = 48.dp)
    ) {
        val thumbHeight = 48.dp
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val travel = (constraints.maxHeight - thumbHeightPx).coerceAtLeast(0f)
        val top = if (dragging) dragPosition.coerceIn(0f, travel) else progress * travel
        val page = if (dragging) currentTarget else (progress * (pageCount - 1)).roundToInt()
        val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
        val thumbColor = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(3.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(trackColor)
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(pageCount, travel) {
                    var lastRequestedPage = -1
                    fun requestPage(position: Float) {
                        currentTarget = ((position / travel.coerceAtLeast(1f)) * (pageCount - 1))
                            .roundToInt()
                            .coerceIn(0, pageCount - 1)
                        if (currentTarget != lastRequestedPage) {
                            lastRequestedPage = currentTarget
                            scope.launch { listState.scrollToItem(currentTarget) }
                        }
                    }
                    detectDragGestures(
                        onDragStart = { point ->
                            dragging = true
                            dragPosition = (point.y - thumbHeightPx / 2f).coerceIn(0f, travel)
                            requestPage(dragPosition)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, amount ->
                        change.consume()
                        dragPosition = (dragPosition + amount.y).coerceIn(0f, travel)
                        requestPage(dragPosition)
                    }
                }
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, top.roundToInt()) }
                    .padding(end = 8.dp)
                    .width(12.dp)
                    .height(thumbHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(thumbColor)
            )
        }
        if (dragging) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(y = with(density) { (top - constraints.maxHeight / 2f + thumbHeightPx / 2f).toDp() }),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ) {
                Text("${page + 1} / $pageCount", modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TextSelectionToolbar(
    selectedCharacters: Int,
    selectingAll: Boolean,
    onSelectCurrentPage: () -> Unit,
    onSelectAllPages: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$selectedCharacters selected",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selectingAll) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onSelectCurrentPage) {
                    Icon(Icons.Rounded.SelectAll, contentDescription = "Select all text on this page")
                }
            }
            TextButton(onClick = onSelectAllPages, enabled = !selectingAll) { Text("All pages") }
            TextButton(onClick = onCopy) { Text("Copy") }
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear text selection") }
        }
    }
}

@Composable
private fun PageIndicator(scrolling: Boolean, page: Int, count: Int, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling) {
        if (scrolling) {
            visible = true
        } else {
            delay(1200)
            visible = false
        }
    }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier.padding(top = 12.dp)) {
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.72f)) {
            Text(
                "${page + 1} / $count",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss", Modifier.size(18.dp)) }
    }
}

// endregion

// region Top bars

@Composable
private fun ViewerTopBar(
    doc: DocumentUi,
    currentPage: Int,
    editing: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSearch: () -> Unit,
    onTogglePen: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onTool: (ToolId) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (doc.isProtected) {
                        Icon(Icons.Rounded.Lock, "Password protected", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    buildString {
                        append("Page ${(currentPage + 1).coerceAtMost(doc.pageCount)} of ${doc.pageCount}")
                        if (doc.dirty) append(" · Unsaved")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (doc.dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (doc.canUndo) IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
            if (doc.canRedo) IconButton(onClick = onRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (editing) EditViolet else Color.Transparent)
                    .clickable(onClick = onTogglePen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = if (editing) "Stop editing" else "Edit text",
                    tint = if (editing) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Save a copy") }, leadingIcon = { Icon(Icons.Rounded.Save, null) }, onClick = { menu = false; onSave() })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text("Print") }, leadingIcon = { Icon(Icons.Rounded.Print, null) }, onClick = { menu = false; onPrint() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Properties") }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { menu = false; onTool(ToolId.PROPERTIES) })
                    DropdownMenuItem(
                        text = { Text(if (doc.isProtected) "Change password" else "Add password") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                        onClick = { menu = false; onTool(ToolId.PROTECT) }
                    )
                    if (doc.isProtected) {
                        DropdownMenuItem(
                            text = { Text("Remove password") },
                            leadingIcon = { Icon(ToolCatalog[ToolId.UNLOCK].icon, null) },
                            onClick = { menu = false; onTool(ToolId.UNLOCK) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(vm: AppViewModel, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf(vm.search.query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val search = vm.search
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") }
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.runSearch(it)
                },
                placeholder = { Text("Find in document") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.moveSearch(1) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
            )
            when {
                search.running && search.hits.isEmpty() -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                query.isNotBlank() -> Text(
                    if (search.hits.isEmpty()) "No results" else "${search.current + 1}/${search.hits.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { vm.moveSearch(-1) }, enabled = search.hits.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous") }
            IconButton(onClick = { vm.moveSearch(1) }, enabled = search.hits.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowDown, "Next") }
        }
    }
}

// endregion

// region Bottom bars

@Composable
private fun FloatingPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp,
        tonalElevation = 2.dp,
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth()
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun ViewerDock(
    dirty: Boolean,
    onPages: () -> Unit,
    onAnnotate: () -> Unit,
    onSign: () -> Unit,
    onTools: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 14.dp,
            modifier = Modifier.weight(1f)
        ) {
            Row(Modifier.height(66.dp).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                DockItem(Icons.Rounded.GridView, "Pages", onPages)
                DockItem(Icons.Rounded.Draw, "Annotate", onAnnotate)
                DockItem(Icons.Rounded.HistoryEdu, "Sign", onSign)
                DockItem(Icons.Rounded.Apps, "Tools", onTools)
            }
        }
        Box {
            Box(
                Modifier
                    .size(66.dp)
                    .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x66E5394B), spotColor = Color(0x66E5394B))
                    .clip(RoundedCornerShape(22.dp))
                    .background(BrandGradient)
                    .clickable(onClick = onSave),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Save, "Save", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            if (dirty) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFC107))
                )
            }
        }
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun BarActions(cancelLabel: String = "Cancel", applyLabel: String, applyEnabled: Boolean = true, onCancel: () -> Unit, onApply: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel) { Text(cancelLabel) }
        Button(onClick = onApply, enabled = applyEnabled, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(applyLabel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditBar(state: ViewerState, onExit: () -> Unit, onCommit: () -> Unit) {
    val target = state.editTarget
    FloatingPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (target == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoicePill("Edit text", !state.addTextMode, { state.addTextMode = false })
                    ChoicePill("Add text", state.addTextMode, { state.addTextMode = true })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onExit) { Text("Done") }
                }
                Text(
                    if (state.addTextMode) "Tap where you want to type." else "Tap any outlined text to change it. The keyboard opens with the same font, size and color.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val committing = state.clearEditAtVersion != null
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(EditViolet.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.TextFields, null, tint = EditViolet, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (target is EditTarget.Existing) "Editing text" else "New text", style = MaterialTheme.typography.titleSmall)
                        Text(target.style.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (committing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                if (target is EditTarget.New && !committing) {
                    NewTextStyleControls(target.style) { style ->
                        state.newTextStyle = style
                        state.editTarget = target.copy(style = style)
                    }
                }
                BarActions(applyLabel = "Done", applyEnabled = !committing, onCancel = state::cancelEditing, onApply = onCommit)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTextStyleControls(style: com.example.pdfmate.core.pdf.TextStyleHint, onChange: (com.example.pdfmate.core.pdf.TextStyleHint) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { onChange(style.copy(sizePt = (style.sizePt - 1f).coerceAtLeast(6f))) }) { Icon(Icons.Rounded.Remove, "Smaller") }
            Text("${style.sizePt.roundToInt()} pt", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp))
            IconButton(onClick = { onChange(style.copy(sizePt = (style.sizePt + 1f).coerceAtMost(96f))) }) { Icon(Icons.Rounded.Add, "Larger") }
            ToggleIcon(Icons.Rounded.FormatBold, "Bold", style.bold) { onChange(style.copy(bold = it)) }
            ToggleIcon(Icons.Rounded.FormatItalic, "Italic", style.italic) { onChange(style.copy(italic = it)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(FontFamilyHint.SANS to "Sans", FontFamilyHint.SERIF to "Serif", FontFamilyHint.MONO to "Mono").forEach { (family, label) ->
                ChoicePill(label, style.family == family, {
                    val name = when (family) {
                        FontFamilyHint.SANS -> "Helvetica"
                        FontFamilyHint.SERIF -> "Times"
                        FontFamilyHint.MONO -> "Courier"
                    }
                    onChange(style.copy(family = family, fontName = name))
                })
            }
        }
        Swatches(ViewerState.InkColors, style.colorArgb) { onChange(style.copy(colorArgb = it)) }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Swatches(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { color ->
            val active = color == selected
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(if (active) 3.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onSelect(color) }
            )
        }
    }
}

@Composable
private fun AnnotateBar(state: ViewerState, onCancel: () -> Unit, onApply: () -> Unit) {
    FloatingPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                listOf(
                    Triple(InkTool.PEN, Icons.Rounded.Edit, "Pen"),
                    Triple(InkTool.MARKER, Icons.Rounded.Brush, "Marker"),
                    Triple(InkTool.HIGHLIGHT_TEXT, Icons.Rounded.Highlight, "Highlight"),
                    Triple(InkTool.ERASER, Icons.Rounded.CleaningServices, "Eraser"),
                    Triple(InkTool.PAN, Icons.Rounded.PanTool, "Scroll")
                ).forEach { (tool, icon, label) ->
                    val active = state.inkTool == tool
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable {
                                state.inkTool = tool
                                if (tool == InkTool.HIGHLIGHT_TEXT || tool == InkTool.MARKER) {
                                    if (state.inkColor == ViewerState.InkColors.first()) state.inkColor = ViewerState.InkColors[4]
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.inkTool != InkTool.ERASER && state.inkTool != InkTool.PAN) {
                Swatches(ViewerState.InkColors, state.inkColor) { state.inkColor = it }
            }
            if (state.inkTool == InkTool.PEN || state.inkTool == InkTool.MARKER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Slider(value = state.inkWidth, onValueChange = { state.inkWidth = it }, valueRange = 0.0015f..0.012f, modifier = Modifier.weight(1f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = state::undoMark, enabled = state.hasPendingMarks) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo mark") }
                Spacer(Modifier.weight(1f))
                BarActions(applyLabel = "Apply", applyEnabled = state.hasPendingMarks, onCancel = onCancel, onApply = onApply)
            }
        }
    }
}

@Composable
private fun CropBar(state: ViewerState, onCancel: () -> Unit, onApply: () -> Unit) {
    FloatingPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Drag the corners to choose what to keep.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill("Page ${state.cropPage + 1}", !state.cropAllPages, { state.cropAllPages = false })
                ChoicePill("All pages", state.cropAllPages, { state.cropAllPages = true })
            }
            BarActions(applyLabel = "Crop", onCancel = onCancel, onApply = onApply)
        }
    }
}

@Composable
private fun SignBar(page: Int, pageCount: Int, onMove: (Int) -> Unit, onCancel: () -> Unit, onPlace: () -> Unit) {
    FloatingPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Drag to move · pull the corner to resize", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMove(page - 1) }, enabled = page > 0) { Icon(Icons.Rounded.ChevronLeft, "Previous page") }
                Text("Page ${page + 1} of $pageCount", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { onMove(page + 1) }, enabled = page < pageCount - 1) { Icon(Icons.Rounded.ChevronRight, "Next page") }
                Spacer(Modifier.weight(1f))
            }
            BarActions(applyLabel = "Place", onCancel = onCancel, onApply = onPlace)
        }
    }
}

// endregion

@Composable
private fun ToolsGrid(onTool: (ToolId) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("All tools", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
        }
        ToolCategory.entries.forEach { category ->
            val tools = ToolCatalog.all.filter { it.category == category && it.id != ToolId.IMAGES_TO_PDF }
            item(span = { GridItemSpan(maxLineSpan) }, key = "category_${category.name}") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 10.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(category.accent))
                    Spacer(Modifier.width(8.dp))
                    Text(category.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            items(tools, key = { "tool_${it.id.name}" }) { tool -> CompactToolTile(tool, onClick = { onTool(tool.id) }) }
        }
    }
}
