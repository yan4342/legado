package io.legado.app.ui.widget.dialog

import android.os.Build
import android.text.Spanned
import android.view.textclassifier.TextClassifier
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.coil.CoilImagesPlugin
import io.legado.app.ui.about.CrashViewModel
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.utils.FileDoc
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// region TextMode

enum class TextMode { MD, HTML, TEXT }

// endregion

// region Markdown Content

/**
 * Markwon 渲染的 Markdown 内容底板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegadoMarkdownContent(
    title: String,
    markdownContent: String,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface
    var markdown by remember { mutableStateOf<Spanned?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val requestDismiss = rememberDelayedDismiss(sheetState, onDismiss)

    val markwon = remember {
        Markwon.builder(ctx)
            .usePlugin(CoilImagesPlugin.create(ctx))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(ctx))
            .build()
    }

    LaunchedEffect(markdownContent) {
        markdown = withContext(Dispatchers.IO) {
            markwon.toMarkdown(markdownContent)
        }
    }

    ModalLegadoBottomSheet(
        show = true,
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        title = title,
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                androidx.compose.runtime.key(markdown) {
                    AndroidView(
                        factory = { c ->
                            TextView(c).apply {
                                setTextColor(textColor.toArgb())
                                textSize = 15f
                                setLineSpacing(4f, 1f)
                                setTextIsSelectable(true)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    setTextClassifier(TextClassifier.NO_OP)
                                }
                            }
                        },
                        update = { tv ->
                            markdown?.let { md ->
                                markwon.setParsedMarkdown(tv, md)
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// endregion

// region Text Content

/**
 * 纯文本 / HTML / Markdown 内容底板。替代 [TextDialog]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegadoTextContent(
    title: String,
    content: String?,
    mode: TextMode = TextMode.TEXT,
    autoCloseMs: Long = 0,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var remainingSeconds by remember { mutableStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val requestDismiss = rememberDelayedDismiss(sheetState, onDismiss)

    // Auto-close timer
    if (autoCloseMs > 0) {
        LaunchedEffect(autoCloseMs) {
            remainingSeconds = (autoCloseMs / 1000).toInt()
            while (remainingSeconds > 0) {
                kotlinx.coroutines.delay(1000)
                remainingSeconds -= 1
            }
            requestDismiss()
        }
    }

    val resolvedContent = content ?: ""
    val displayContent = if (mode == TextMode.TEXT && resolvedContent.length >= 32 * 1024) {
        resolvedContent.substring(0, 32 * 1024) + "\n\n数据太大，无法全部显示…"
    } else {
        resolvedContent
    }

    ModalLegadoBottomSheet(
        show = true,
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        title = title,
    ) {
        // Close button
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            FilledTonalButton(onClick = requestDismiss) {
                Text(
                    text = stringResource(R.string.close)
                        + if (remainingSeconds > 0) " (${remainingSeconds}s)" else ""
                )
            }
        }

        when (mode) {
            TextMode.HTML -> AndroidView(
                factory = { c ->
                    TextView(c).apply {
                        setTextIsSelectable(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            setTextClassifier(TextClassifier.NO_OP)
                        }
                    }
                },
                update = { tv ->
                    @Suppress("DEPRECATION")
                    tv.text = android.text.Html.fromHtml(displayContent, 0)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            TextMode.MD -> {
                var markdown by remember { mutableStateOf<Spanned?>(null) }
                val textColor = MaterialTheme.colorScheme.onSurface
                val markwon = remember {
                    Markwon.builder(ctx)
                        .usePlugin(CoilImagesPlugin.create(ctx))
                        .usePlugin(HtmlPlugin.create())
                        .usePlugin(TablePlugin.create(ctx))
                        .build()
                }
                LaunchedEffect(displayContent) {
                    markdown = withContext(Dispatchers.IO) {
                        markwon.toMarkdown(displayContent)
                    }
                }
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        androidx.compose.runtime.key(markdown) {
                            AndroidView(
                                factory = { c ->
                                    TextView(c).apply {
                                        setTextColor(textColor.toArgb())
                                        textSize = 15f
                                        setLineSpacing(4f, 1f)
                                        setTextIsSelectable(true)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            setTextClassifier(TextClassifier.NO_OP)
                                        }
                                    }
                                },
                                update = { tv ->
                                    markdown?.let { md ->
                                        markwon.setParsedMarkdown(tv, md)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            TextMode.TEXT -> SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = displayContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// endregion

// region Log List Content

/**
 * 运行时日志列表底板。替代 [io.legado.app.ui.about.AppLogDialog]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegadoLogListContent(
    onDismiss: () -> Unit,
) {
    var logs by remember { mutableStateOf(AppLog.logs.toList()) }
    var showDetail by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val requestDismiss = rememberDelayedDismiss(sheetState, onDismiss)

    ModalLegadoBottomSheet(
        show = true,
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        title = stringResource(R.string.log),
        actions = {
            IconButton(onClick = {
                AppLog.clear()
                logs = emptyList()
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_clear_all),
                    contentDescription = stringResource(R.string.clear),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        if (logs.isEmpty()) {
            Text(
                text = "暂无日志",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs) { item ->
                    val (time, msg, throwable) = item
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date(time)),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = if (throwable != null) {
                                Modifier.clickable { showDetail = throwable.stackTraceToString() }
                            } else Modifier,
                        )
                    }
                }
            }
        }
    }

    // Detail sheet
    ModalLegadoBottomSheet(
        show = showDetail != null,
        onDismissRequest = { showDetail = null },
        title = "Log",
    ) {
        Text(
            text = showDetail ?: "",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// endregion

// region Crash Log Content

/**
 * 崩溃日志文件列表底板。替代 [io.legado.app.ui.about.CrashLogsDialog]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegadoCrashLogContent(
    onDismiss: () -> Unit,
    crashViewModel: CrashViewModel = viewModel(),
) {
    val logs by crashViewModel.logList.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val requestDismiss = rememberDelayedDismiss(sheetState, onDismiss)

    LaunchedEffect(Unit) {
        crashViewModel.initData()
    }

    ModalLegadoBottomSheet(
        show = true,
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        title = stringResource(R.string.crash_log),
        actions = {
            IconButton(onClick = { crashViewModel.clearCrashLog() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_clear_all),
                    contentDescription = stringResource(R.string.clear),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        if (logs.isEmpty()) {
            Text(
                text = "暂无日志",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(logs) { fileDoc ->
                    Text(
                        text = fileDoc.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { crashViewModel.readFileContent(fileDoc) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    // File content detail
    val fileContent by crashViewModel.fileContent.collectAsState()
    ModalLegadoBottomSheet(
        show = fileContent != null,
        onDismissRequest = { crashViewModel.clearFileContent() },
        title = crashViewModel.currentFileName,
    ) {
        Text(
            text = fileContent ?: "",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// endregion
