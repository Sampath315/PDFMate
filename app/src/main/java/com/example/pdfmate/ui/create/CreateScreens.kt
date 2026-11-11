package com.example.pdfmate.ui.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pdfmate.core.files.FileStore.formatSize
import com.example.pdfmate.core.pdf.ImagePageSize
import com.example.pdfmate.ui.AppViewModel
import com.example.pdfmate.ui.components.BitmapView
import com.example.pdfmate.ui.components.ChoicePill
import com.example.pdfmate.ui.components.EmptyState
import com.example.pdfmate.ui.components.GradientButton
import com.example.pdfmate.ui.components.IconBadge
import com.example.pdfmate.ui.theme.ConvertGreen
import com.example.pdfmate.ui.theme.OrganizeBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun CreateTopBar(title: String, subtitle: String, onBack: () -> Unit) {
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
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BottomActions(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Column(
            Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
    }
}

@Composable
fun MergeScreen(vm: AppViewModel, onAddFiles: () -> Unit) {
    val items = vm.mergeItems
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CreateTopBar("Merge PDFs", if (items.isEmpty()) "Combine files in the order you want" else "${items.size} files") { vm.back() }
        if (items.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.MergeType,
                    title = "Add two or more PDFs",
                    body = "They'll be joined top to bottom. You can reorder them before merging."
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.animateItem()
                    ) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = OrganizeBlue,
                                modifier = Modifier.width(22.dp)
                            )
                            IconBadge(Icons.Rounded.PictureAsPdf, OrganizeBlue)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatSize(item.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.moveMergeItem(index, index - 1) }, enabled = index > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move up")
                            }
                            IconButton(onClick = { vm.moveMergeItem(index, index + 1) }, enabled = index < items.lastIndex) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Move down")
                            }
                            IconButton(onClick = { vm.removeMergeItem(item.id) }) { Icon(Icons.Rounded.Close, "Remove") }
                        }
                    }
                }
            }
        }
        BottomActions {
            OutlinedButton(onClick = onAddFiles, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (items.isEmpty()) "Add PDFs" else "Add more")
            }
            GradientButton(
                text = if (items.size < 2) "Merge" else "Merge ${items.size} files",
                onClick = vm::runMerge,
                icon = Icons.AutoMirrored.Rounded.MergeType,
                enabled = items.size >= 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ImagesToPdfScreen(vm: AppViewModel, onAddImages: () -> Unit) {
    val items = vm.imageItems
    val pageSize = vm.imagePageSize
    val margins = vm.imageMargins
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CreateTopBar("Images to PDF", if (items.isEmpty()) "Photos and scans become pages" else "${items.size} images") { vm.back() }
        if (items.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.AddPhotoAlternate,
                    title = "Pick some images",
                    body = "Each image becomes one page. Rotate and reorder them before converting."
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.animateItem()
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.85f)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            ImageThumb(item.uri, item.rotation)
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(ConvertGreen)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { vm.moveImage(index, index - 1) }, enabled = index > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move earlier", Modifier.rotate(-90f))
                            }
                            IconButton(onClick = { vm.rotateImage(item.id) }) { Icon(Icons.AutoMirrored.Rounded.RotateRight, "Rotate") }
                            IconButton(onClick = { vm.removeImage(item.id) }) { Icon(Icons.Rounded.Close, "Remove") }
                            IconButton(onClick = { vm.moveImage(index, index + 1) }, enabled = index < items.lastIndex) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move later", Modifier.rotate(90f))
                            }
                        }
                    }
                }
            }
        }
        BottomActions {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Page size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ImagePageSize.entries.forEach { size ->
                        ChoicePill(
                            text = size.label,
                            selected = pageSize == size,
                            onClick = { vm.updateImagePageSize(size) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Margins", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Add whitespace around each image",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = margins, onCheckedChange = vm::updateImageMargins)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onAddImages, modifier = Modifier.height(54.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (items.isEmpty()) "Add" else "More")
                }
                GradientButton(
                    text = "Create PDF",
                    onClick = { vm.runImagesToPdf(pageSize, margins) },
                    icon = Icons.Rounded.PictureAsPdf,
                    enabled = items.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ImageThumb(uri: Uri, rotation: Int) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) { value = loadThumbnail(context, uri, 360) }
    BitmapView(bitmap, Modifier.fillMaxSize().rotate(rotation.toFloat()), ContentScale.Fit)
    if (bitmap == null) Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.outline)
}

private suspend fun loadThumbnail(context: Context, uri: Uri, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}
