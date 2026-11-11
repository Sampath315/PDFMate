package com.example.pdfmate.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pdfmate.ui.components.BusyOverlay
import com.example.pdfmate.ui.components.PasswordDialog
import com.example.pdfmate.ui.create.ImagesToPdfScreen
import com.example.pdfmate.ui.create.MergeScreen
import com.example.pdfmate.ui.home.HomeScreen
import com.example.pdfmate.ui.organize.OrganizeScreen
import com.example.pdfmate.ui.theme.PDFMateTheme
import com.example.pdfmate.ui.viewer.ViewerScreen
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private const val PDF_MIME = "application/pdf"

/** CREATE_DOCUMENT with a per-request mime type (the stock contract fixes the type at construction). */
private class CreateTypedDocument : ActivityResultContract<ExportRequest, Uri?>() {
    override fun createIntent(context: Context, input: ExportRequest): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mime)
            .putExtra(Intent.EXTRA_TITLE, input.fileName)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

@Composable
fun PdfMateApp(vm: AppViewModel) {
    val dark = when (vm.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    PDFMateTheme(darkTheme = dark) {
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        var toolAfterPick by rememberSaveable { mutableStateOf<ToolId?>(null) }

        val createDocument = rememberLauncherForActivityResult(CreateTypedDocument()) { vm.onExportTarget(it) }
        val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val tool = toolAfterPick
            toolAfterPick = null
            if (uri != null) vm.openDocument(uri, tool)
        }
        val pickMerge = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addMergeItems(it) }
        val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addImages(it) }

        LaunchedEffect(vm) {
            vm.eventFlow.collect { event ->
                when (event) {
                    is UiEvent.Message -> launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(
                            message = event.text,
                            actionLabel = if (event.offerUndo) "Undo" else null,
                            withDismissAction = !event.offerUndo,
                            duration = if (event.offerUndo) SnackbarDuration.Long else SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) vm.undo()
                    }
                    is UiEvent.CreateDocument -> runCatching { createDocument.launch(event.request) }.onFailure {
                        vm.onExportTarget(null)
                        vm.message("No app is available to save files.")
                    }
                    is UiEvent.Share -> runCatching {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType(event.mime)
                            .putExtra(Intent.EXTRA_STREAM, event.uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        send.clipData = ClipData.newRawUri(null, event.uri)
                        context.startActivity(Intent.createChooser(send, null))
                    }.onFailure { vm.message("No app is available to share with.") }
                    is UiEvent.Print -> runCatching { printPdf(context, event.file, event.name) }
                        .onFailure { vm.message("Printing isn't available on this device.") }
                }
            }
        }

        BackHandler(enabled = vm.screen != Screen.HOME) { vm.back() }

        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = vm.screen,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f)) togetherWith fadeOut(tween(120))
                },
                label = "screen"
            ) { screen ->
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        recents = vm.recents,
                        themeMode = vm.themeMode,
                        loadThumbnail = vm::recentThumbnail,
                        onOpenPdf = { openPdf.launch(arrayOf(PDF_MIME)) },
                        onOpenRecent = { vm.openDocument(it.uri) },
                        onRemoveRecent = { vm.removeRecent(it.uri) },
                        onClearRecents = vm::clearRecents,
                        onTool = { tool ->
                            when (tool) {
                                ToolId.MERGE -> {
                                    vm.navigate(Screen.MERGE)
                                    if (vm.mergeItems.isEmpty()) pickMerge.launch(arrayOf(PDF_MIME))
                                }
                                ToolId.IMAGES_TO_PDF -> {
                                    vm.navigate(Screen.IMAGES)
                                    if (vm.imageItems.isEmpty()) pickImages.launch(arrayOf("image/*"))
                                }
                                else -> {
                                    toolAfterPick = tool
                                    openPdf.launch(arrayOf(PDF_MIME))
                                }
                            }
                        },
                        onThemeChange = vm::updateTheme
                    )
                    Screen.VIEWER -> ViewerScreen(vm)
                    Screen.ORGANIZE -> OrganizeScreen(vm)
                    Screen.MERGE -> MergeScreen(vm) { pickMerge.launch(arrayOf(PDF_MIME)) }
                    Screen.IMAGES -> ImagesToPdfScreen(vm) { pickImages.launch(arrayOf("image/*")) }
                }
            }

            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = if (vm.screen == Screen.HOME) 8.dp else 96.dp)
            )

            BusyOverlay(vm.busy)
        }

        vm.passwordPrompt?.let { prompt ->
            PasswordDialog(prompt, onSubmit = vm::submitPassword, onCancel = vm::cancelPassword)
        }
    }
}

private fun printPdf(context: Context, file: File, name: String) {
    val manager = context.getSystemService(PrintManager::class.java) ?: error("No print service")
    manager.print(name, object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            callback.onLayoutFinished(
                PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
                true
            )
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }, null)
}
