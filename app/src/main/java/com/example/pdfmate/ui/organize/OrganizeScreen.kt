package com.example.pdfmate.ui.organize

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import com.example.pdfmate.core.pdf.PageSlot
import com.example.pdfmate.ui.AppViewModel
import com.example.pdfmate.ui.components.BitmapView
import com.example.pdfmate.ui.components.ConfirmDialog
import com.example.pdfmate.ui.theme.OrganizeBlue
import java.util.UUID

private data class Slot(val key: String, val source: Int, val rotation: Int)

@Composable
fun OrganizeScreen(vm: AppViewModel) {
    val doc = vm.document ?: return
    val slots = remember(doc.versionKey) {
        mutableStateListOf<Slot>().apply { addAll((0 until doc.pageCount).map { Slot(UUID.randomUUID().toString(), it, 0) }) }
    }
    val selected = remember(doc.versionKey) { mutableStateListOf<String>() }
    val changed by remember(doc.versionKey) {
        derivedStateOf { slots.size != doc.pageCount || slots.withIndex().any { (i, s) -> s.source != i || s.rotation % 360 != 0 } }
    }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pendingPageToOpen by remember { mutableStateOf<Int?>(null) }
    val gridState = rememberLazyGridState()
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val edge = with(LocalDensity.current) { 72.dp.toPx() }

    fun leave() = if (changed) confirmDiscard = true else { vm.back(); Unit }

    BackHandler { leave() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
            Row(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Organize pages", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (selected.isEmpty()) "${slots.size} pages" else "${selected.size} of ${slots.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    if (selected.size == slots.size) selected.clear() else {
                        selected.clear()
                        selected.addAll(slots.map { it.key })
                    }
                }) { Icon(Icons.Rounded.SelectAll, "Select all") }
                Button(
                    onClick = {
                        vm.organize(slots.map { PageSlot(it.source, it.rotation) })
                        vm.back()
                    },
                    enabled = changed && slots.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text("Apply") }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .weight(1f)
                .pointerInput(doc.versionKey) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            gridState.layoutInfo.visibleItemsInfo
                                .firstOrNull {
                                    start.x >= it.offset.x && start.x <= it.offset.x + it.size.width &&
                                        start.y >= it.offset.y && start.y <= it.offset.y + it.size.height
                                }
                                ?.let {
                                    dragging = it.key as String
                                    dragOffset = Offset.Zero
                                }
                        },
                        onDrag = { change, amount ->
                            val key = dragging ?: return@detectDragGesturesAfterLongPress
                            change.consume()
                            dragOffset += amount
                            // Scroll when the finger nears the top or bottom edge.
                            val y = change.position.y
                            val scroll = when {
                                y < edge -> -(edge - y) / 4f
                                y > size.height - edge -> (y - (size.height - edge)) / 4f
                                else -> 0f
                            }
                            if (scroll != 0f) dragOffset += Offset(0f, gridState.dispatchRawDelta(scroll))
                            val items = gridState.layoutInfo.visibleItemsInfo
                            val current = items.firstOrNull { it.key == key } ?: return@detectDragGesturesAfterLongPress
                            val center = current.offset.toOffset() + dragOffset +
                                Offset(current.size.width / 2f, current.size.height / 2f)
                            val target = items.firstOrNull {
                                it.key != key &&
                                    center.x >= it.offset.x && center.x <= it.offset.x + it.size.width &&
                                    center.y >= it.offset.y && center.y <= it.offset.y + it.size.height
                            } ?: return@detectDragGesturesAfterLongPress
                            val from = slots.indexOfFirst { it.key == key }
                            val to = slots.indexOfFirst { it.key == target.key }
                            if (from < 0 || to < 0) return@detectDragGesturesAfterLongPress
                            slots.add(to, slots.removeAt(from))
                            dragOffset += current.offset.toOffset() - target.offset.toOffset()
                        },
                        onDragEnd = { dragging = null; dragOffset = Offset.Zero },
                        onDragCancel = { dragging = null; dragOffset = Offset.Zero }
                    )
                }
        ) {
            itemsIndexed(slots, key = { _, slot -> slot.key }) { index, slot ->
                val isDragging = dragging == slot.key
                PageThumb(
                    vm = vm,
                    versionKey = doc.versionKey,
                    aspect = doc.pageSizes[slot.source].aspectRatio,
                    slot = slot,
                    number = index + 1,
                    selected = slot.key in selected,
                    onClick = { if (slot.key in selected) selected.remove(slot.key) else selected.add(slot.key) },
                    onOpenPage = {
                        if (changed) {
                            pendingPageToOpen = slot.source
                            confirmDiscard = true
                        } else {
                            vm.openViewerPage(slot.source)
                        }
                    },
                    modifier = if (isDragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                scaleX = 1.06f
                                scaleY = 1.06f
                            }
                    } else {
                        Modifier.animateItem()
                    }
                )
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val any = selected.isNotEmpty()
                fun rotate(delta: Int) {
                    for (i in slots.indices) if (slots[i].key in selected) slots[i] = slots[i].copy(rotation = slots[i].rotation + delta)
                }
                BarAction(Icons.AutoMirrored.Rounded.RotateLeft, "Left", any) { rotate(-90) }
                BarAction(Icons.AutoMirrored.Rounded.RotateRight, "Right", any) { rotate(90) }
                BarAction(Icons.Rounded.ContentCopy, "Duplicate", any) {
                    val copies = slots.filter { it.key in selected }.map { it.copy(key = UUID.randomUUID().toString()) }
                    val insertAt = slots.indexOfLast { it.key in selected } + 1
                    slots.addAll(insertAt, copies)
                }
                BarAction(Icons.Rounded.Delete, "Delete", any && selected.size < slots.size) {
                    slots.removeAll { it.key in selected }
                    selected.clear()
                }
            }
        }
        if (selected.isEmpty()) {
            Text(
                "Tap pages to select · long-press and drag to reorder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard page changes?",
            text = "Your reordering and edits on this screen haven't been applied.",
            confirm = "Discard",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                val pageIndex = pendingPageToOpen
                pendingPageToOpen = null
                if (pageIndex == null) vm.back() else vm.openViewerPage(pageIndex)
            },
            onDismiss = { confirmDiscard = false; pendingPageToOpen = null },
            dismiss = "Keep editing"
        )
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun PageThumb(
    vm: AppViewModel,
    versionKey: Int,
    aspect: Float,
    slot: Slot,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onOpenPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(null, versionKey, slot.source) { value = vm.renderPage(versionKey, slot.source, 280) }
    val angle by animateFloatAsState(slot.rotation.toFloat(), label = "rotation")
    val quarterTurn = ((slot.rotation % 180) + 180) % 180 == 90
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) OrganizeBlue.copy(alpha = 0.12f) else Color.Transparent)
                .border(if (selected) 2.dp else 0.dp, if (selected) OrganizeBlue else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val cellAspect = maxWidth / maxHeight
            val shown = if (quarterTurn) 1f / aspect else aspect
            val (fitW, fitH) = if (shown > cellAspect) maxWidth to maxWidth / shown else maxHeight * shown to maxHeight
            val (drawW, drawH) = if (quarterTurn) fitH to fitW else fitW to fitH
            Box(
                Modifier
                    .requiredSize(drawW, drawH)
                    .rotate(angle)
                    .shadow(3.dp, RoundedCornerShape(2.dp))
                    .background(Color.White)
            ) {
                BitmapView(bitmap, Modifier.fillMaxSize())
            }
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = if (selected) OrganizeBlue else Color.Gray.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            androidx.compose.material3.IconButton(
                onClick = onOpenPage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Open page $number",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (slot.source + 1 != number) "$number  ·  was ${slot.source + 1}" else "$number",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(0.dp))
    }
}
