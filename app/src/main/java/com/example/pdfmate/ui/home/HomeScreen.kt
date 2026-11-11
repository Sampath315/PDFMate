package com.example.pdfmate.ui.home

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pdfmate.core.files.FileStore
import com.example.pdfmate.core.files.RecentPdf
import com.example.pdfmate.ui.ThemeMode
import com.example.pdfmate.ui.ToolId
import com.example.pdfmate.ui.components.BitmapView
import com.example.pdfmate.ui.components.EmptyState
import com.example.pdfmate.ui.components.SectionHeader
import com.example.pdfmate.ui.components.ToolCatalog
import com.example.pdfmate.ui.components.ToolCategory
import com.example.pdfmate.ui.components.ToolTile
import com.example.pdfmate.ui.theme.BrandGradient
import com.example.pdfmate.ui.theme.BrandRed

@Composable
fun HomeScreen(
    recents: List<RecentPdf>,
    themeMode: ThemeMode,
    loadThumbnail: suspend (Uri) -> Bitmap?,
    onOpenPdf: () -> Unit,
    onOpenRecent: (RecentPdf) -> Unit,
    onRemoveRecent: (RecentPdf) -> Unit,
    onClearRecents: () -> Unit,
    onTool: (ToolId) -> Unit,
    onThemeChange: (ThemeMode) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val tools = remember(query) { ToolCatalog.search(query) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(144.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        fullSpan("header") {
            Header(themeMode, onThemeChange, Modifier.windowInsetsPadding(WindowInsets.statusBars))
        }
        fullSpan("hero") { HeroCard(onOpenPdf, onTool) }
        if (recents.isNotEmpty() && query.isBlank()) {
            fullSpan("recents") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                    SectionHeader("Recent files") {
                        TextButton(onClick = onClearRecents) { Text("Clear") }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recents, key = { it.uri.toString() }) { recent ->
                            RecentCard(recent, loadThumbnail, { onOpenRecent(recent) }, { onRemoveRecent(recent) })
                        }
                    }
                }
            }
        }
        fullSpan("search") {
            ToolSearchField(query, { query = it }, Modifier.padding(top = 16.dp))
        }
        if (tools.isEmpty()) {
            fullSpan("empty") { EmptyState(Icons.Rounded.Search, "No tools found", "Try a different word, like “merge” or “sign”.") }
        }
        ToolCategory.entries.forEach { category ->
            val inCategory = tools.filter { it.category == category }
            if (inCategory.isEmpty()) return@forEach
            fullSpan("cat_${category.name}") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(category.accent))
                    Spacer(Modifier.width(10.dp))
                    Text(category.title, style = MaterialTheme.typography.titleMedium)
                }
            }
            items(inCategory, key = { it.id.name }) { tool ->
                ToolTile(tool, onClick = { onTool(tool.id) })
            }
        }
        fullSpan("footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Privacy note", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "PDFMate processes PDFs on your device. Sharing happens only when you choose to.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.Favorite, null, tint = BrandRed, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Made with care for every page",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    }
}

private fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun Header(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BrandGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("PDFMate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("Every PDF tool, in your pocket", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(
                onClick = { menu = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Icon(
                    when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Rounded.SettingsBrightness
                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                    },
                    contentDescription = "Theme",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                listOf(
                    Triple(ThemeMode.SYSTEM, "System default", Icons.Rounded.SettingsBrightness),
                    Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
                    Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode)
                ).forEach { (mode, label, icon) ->
                    val selected = mode == themeMode
                    val contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    DropdownMenuItem(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            ),
                        text = {
                            Text(
                                label,
                                color = contentColor,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (selected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = if (selected) {
                            {
                                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = contentColor)
                            }
                        } else {
                            null
                        },
                        onClick = {
                            menu = false
                            onThemeChange(mode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(onOpenPdf: () -> Unit, onTool: (ToolId) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(BrandGradient)
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), radius = size.height * 0.75f, center = Offset(size.width * 0.95f, size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.height * 0.45f, center = Offset(size.width * 0.78f, size.height * 1.05f))
        }
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Open a PDF", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                "View it, then edit text, sign, organize, compress or convert — right from the viewer.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onOpenPdf,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = BrandRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose file", style = MaterialTheme.typography.labelLarge, color = BrandRed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Surface(
                    onClick = { onTool(ToolId.IMAGES_TO_PDF) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Text(
                        "Images to PDF",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCard(
    recent: RecentPdf,
    loadThumbnail: suspend (Uri) -> Bitmap?,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val thumbnail by produceState<Bitmap?>(null, recent.uri) { value = loadThumbnail(recent.uri) }
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.74f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (thumbnail != null) {
                BitmapView(thumbnail, Modifier.fillMaxSize(), ContentScale.Crop)
            } else {
                Icon(
                    Icons.Rounded.Description,
                    null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(40.dp).align(Alignment.Center)
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Remove from recents") },
                    leadingIcon = { Icon(Icons.Rounded.Close, null) },
                    onClick = {
                        menu = false
                        onRemove()
                    }
                )
            }
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(recent.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = buildList {
                if (recent.pageCount > 0) add("${recent.pageCount} pg")
                if (recent.sizeBytes > 0) add(FileStore.formatSize(recent.sizeBytes))
                if (recent.openedAt > 0) add(DateUtils.getRelativeTimeSpanString(recent.openedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString())
            }.joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun ToolSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Search tools") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, "Clear") } }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    )
}
