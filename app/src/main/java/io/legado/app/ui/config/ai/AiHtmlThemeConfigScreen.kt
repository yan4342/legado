package io.legado.app.ui.config.ai

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.ai.chat.html.AiChatHtmlThemeStore
import io.legado.app.ui.common.compose.CategorySection
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.common.compose.settingItem.SettingItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiHtmlThemeConfigScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { AiChatHtmlThemeStore(context.applicationContext) }
    var themes by remember { mutableStateOf(store.listThemes()) }
    var selectedId by remember { mutableStateOf(AppConfig.aiChatHtmlThemeId) }
    var browsingThemeId by remember { mutableStateOf<String?>(null) }
    var browsingReadOnly by remember { mutableStateOf(false) }
    var editingPath by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    var editorValue by remember { mutableStateOf(TextFieldValue("")) }
    var editingReadOnly by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var activeSearchMatchIndex by remember { mutableStateOf(-1) }
    var showDeleteThemeConfirm by remember { mutableStateOf<String?>(null) }
    var showVersionHistory by remember { mutableStateOf(false) }
    var rollbackTargetVersion by remember { mutableStateOf<Int?>(null) }
    var showReplaceShellConfirm by remember { mutableStateOf(false) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var previewFontPath by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        themes = store.listThemes()
        selectedId = AppConfig.aiChatHtmlThemeId
    }

    LaunchedEffect(Unit) {
        AiChatHtmlThemeStore.changes.collect { refresh() }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = store.exportZip(selectedId)
        if (bytes == null) {
            Toast.makeText(context, R.string.ai_html_theme_export_failed, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            Toast.makeText(context, R.string.ai_html_theme_exported, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, R.string.ai_html_theme_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    val importZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot read zip")
            val info = store.importZip(bytes).getOrThrow()
            store.setTheme(info.id)
            refresh()
            Toast.makeText(context, R.string.ai_html_theme_imported, Toast.LENGTH_SHORT).show()
            browsingThemeId = info.id
            browsingReadOnly = false
        }.onFailure {
            Toast.makeText(
                context,
                it.message ?: context.getString(R.string.ai_html_theme_import_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val importFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        val packId = browsingThemeId ?: return@rememberLauncherForActivityResult
        if (browsingReadOnly || !store.isUserTheme(packId)) {
            Toast.makeText(context, R.string.ai_html_theme_builtin_readonly, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        var ok = 0
        var lastError: String? = null
        for (uri in uris) {
            runCatching {
                val name = queryDisplayName(context, uri) ?: "file"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Cannot read file")
                store.importLocalFile(packId, name, bytes, notify = false).getOrThrow()
                ok++
            }.onFailure {
                lastError = it.message
            }
        }
        AiChatHtmlThemeStore.notifyChanged()
        refresh()
        if (ok > 0) {
            Toast.makeText(
                context,
                context.getString(R.string.ai_html_theme_files_imported, ok),
                Toast.LENGTH_SHORT,
            ).show()
        }
        if (lastError != null && ok < uris.size) {
            Toast.makeText(context, lastError, Toast.LENGTH_LONG).show()
        }
    }

    fun openFiles(themeId: String) {
        val isUser = store.isUserTheme(themeId)
        browsingReadOnly = !isUser
        browsingThemeId = themeId
        editingPath = null
    }

    fun openEditor(themeId: String, path: String, readOnly: Boolean) {
        val rel = AiChatHtmlThemeStore.normalizePackPath(path).getOrNull()
        if (rel != null && rel.startsWith("media/") && !AiChatHtmlThemeStore.isTextMediaPath(rel)) {
            Toast.makeText(context, R.string.ai_html_theme_binary_media, Toast.LENGTH_LONG).show()
            return
        }
        val text = store.readPackFile(themeId, path).orEmpty()
        editingPath = path
        editingText = text
        editorValue = TextFieldValue(text, selection = TextRange(text.length))
        editingReadOnly = readOnly
        showSearch = false
        searchText = ""
        activeSearchMatchIndex = -1
    }

    fun navigateBack() {
        when {
            showSearch -> {
                showSearch = false
                searchText = ""
                activeSearchMatchIndex = -1
            }
            editingPath != null -> editingPath = null
            browsingThemeId != null -> {
                browsingThemeId = null
                browsingReadOnly = false
            }
            else -> onBack()
        }
    }

    BackHandler { navigateBack() }

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary

    val browsingId = browsingThemeId
    val editPath = editingPath
    val extension = editPath?.substringAfterLast('.', "").orEmpty().lowercase()
    val searchMatches = remember(editingText, searchText) {
        findSearchMatches(editingText, searchText)
    }
    val highlightTransformation = rememberHighlightTransformation(
        extension = extension,
        searchMatches = searchMatches,
        activeSearchMatchIndex = activeSearchMatchIndex,
    )

    LaunchedEffect(searchText, editingText) {
        val matches = findSearchMatches(editingText, searchText)
        activeSearchMatchIndex = when {
            matches.isEmpty() -> -1
            activeSearchMatchIndex !in matches.indices -> 0
            else -> activeSearchMatchIndex
        }
        if (matches.isNotEmpty() && activeSearchMatchIndex >= 0) {
            val range = matches[activeSearchMatchIndex]
            editorValue = TextFieldValue(
                text = editingText,
                selection = TextRange(range.first, range.last + 1),
            )
        }
    }

    LaunchedEffect(activeSearchMatchIndex) {
        val idx = activeSearchMatchIndex
        if (idx >= 0 && idx < searchMatches.size) {
            val range = searchMatches[idx]
            editorValue = TextFieldValue(
                text = editingText,
                selection = TextRange(range.first, range.last + 1),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (editPath != null && showSearch) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(
                                    onSurfaceColor.copy(alpha = 0.08f),
                                    RoundedCornerShape(16.dp),
                                ),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = onSurfaceColor),
                                cursorBrush = SolidColor(onSurfaceColor),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = null,
                                            tint = onSurfaceColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box(Modifier.weight(1f)) {
                                            if (searchText.isBlank()) {
                                                Text(
                                                    stringResource(R.string.ai_search_hint),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = onSurfaceColor.copy(alpha = 0.5f),
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                },
                            )
                        }
                    } else {
                        Text(
                            when {
                                editPath != null -> stringResource(R.string.ai_html_theme_edit_file)
                                browsingId != null -> stringResource(R.string.ai_html_theme_files) + " · $browsingId"
                                else -> stringResource(R.string.ai_html_theme_config)
                            },
                            color = onSurfaceColor,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigateBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = onSurfaceColor,
                        )
                    }
                },
                actions = {
                    if (editPath != null) {
                        if (!showSearch) {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = "Search",
                                    tint = onSurfaceColor,
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (searchMatches.isNotEmpty()) {
                                        activeSearchMatchIndex = if (activeSearchMatchIndex <= 0) {
                                            searchMatches.lastIndex
                                        } else {
                                            activeSearchMatchIndex - 1
                                        }
                                    }
                                },
                                enabled = searchMatches.isNotEmpty(),
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.ai_search_prev_match),
                                    tint = onSurfaceColor,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (searchMatches.isNotEmpty()) {
                                        activeSearchMatchIndex = if (activeSearchMatchIndex >= searchMatches.lastIndex) {
                                            0
                                        } else {
                                            activeSearchMatchIndex + 1
                                        }
                                    }
                                },
                                enabled = searchMatches.isNotEmpty(),
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.ai_search_next_match),
                                    tint = onSurfaceColor,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                stringResource(
                                    R.string.ai_match_count,
                                    if (searchMatches.isEmpty()) 0 else activeSearchMatchIndex + 1,
                                    searchMatches.size,
                                ),
                                color = onSurfaceColor,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            IconButton(onClick = { showSearch = false; searchText = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = onSurfaceColor,
                                )
                            }
                        }
                    }
                    when {
                        editPath != null && browsingId != null && !editingReadOnly -> {
                            TextButton(
                                onClick = {
                                    store.writePackFile(
                                        browsingId,
                                        editPath,
                                        editingText,
                                        skipShellValidation = false,
                                        bumpVersion = false,
                                        notify = true,
                                    ).onSuccess {
                                        Toast.makeText(
                                            context,
                                            R.string.ai_html_theme_file_saved,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        editingPath = null
                                        refresh()
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message
                                                ?: context.getString(R.string.ai_html_theme_file_save_failed),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(R.string.ai_html_theme_save_file),
                                    color = onSurfaceColor,
                                )
                            }
                        }
                        browsingId != null && editPath == null && !browsingReadOnly -> {
                            IconButton(
                                onClick = {
                                    importFilesLauncher.launch(
                                        arrayOf(
                                            "text/*",
                                            "image/*",
                                            "font/*",
                                            "application/font-woff",
                                            "application/font-woff2",
                                            "*/*",
                                        ),
                                    )
                                },
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = stringResource(R.string.ai_html_theme_import_files),
                                    tint = onSurfaceColor,
                                )
                            }
                        }
                        browsingId == null -> {
                            IconButton(
                                onClick = {
                                    importZipLauncher.launch(arrayOf("application/zip", "*/*"))
                                },
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = stringResource(R.string.ai_html_theme_import),
                                    tint = onSurfaceColor,
                                )
                            }
                            IconButton(onClick = { exportLauncher.launch("aichat-theme-$selectedId.zip") }) {
                                Icon(
                                    Icons.Default.Upload,
                                    contentDescription = stringResource(R.string.ai_html_theme_export),
                                    tint = onSurfaceColor,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    titleContentColor = onSurfaceColor,
                    navigationIconContentColor = onSurfaceColor,
                    actionIconContentColor = onSurfaceColor,
                ),
            )
        },
    ) { padding ->
        when {
            editPath != null && browsingId != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        editPath,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                    )
                    key(editPath) {
                        val hScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(12.dp),
                                )
                                .horizontalScroll(hScrollState)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            BasicTextField(
                                value = editorValue,
                                onValueChange = { if (!editingReadOnly) { editorValue = it; editingText = it.text } },
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                cursorBrush = SolidColor(onSurfaceColor),
                                readOnly = editingReadOnly,
                                visualTransformation = highlightTransformation,
                            )
                        }
                    }
                }
            }
            browsingId != null -> {
                val paths = store.listPackPaths(browsingId)
                    .filter { !it.startsWith("media/") }
                val dirty = !browsingReadOnly && store.isDirty(browsingId)
                val version = store.readManifest(browsingId)?.version ?: 1
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ai_html_theme_files),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = buildString {
                                        append("v")
                                        append(version)
                                        if (dirty) {
                                            append(" · ")
                                            append(context.getString(R.string.ai_html_theme_dirty))
                                        }
                                        if (browsingReadOnly) {
                                            append(" · ")
                                            append(context.getString(R.string.ai_html_theme_builtin))
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (browsingReadOnly) {
                                        stringResource(R.string.ai_html_theme_builtin_readonly)
                                    } else {
                                        stringResource(R.string.ai_html_theme_files_import_hint)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            if (browsingReadOnly) {
                                TextButton(
                                    onClick = {
                                        store.copyBuiltinToUser(browsingId)
                                            .onSuccess { info ->
                                                refresh()
                                                Toast.makeText(
                                                    context,
                                                    R.string.ai_html_theme_copied,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                browsingThemeId = info.id
                                                browsingReadOnly = false
                                            }
                                            .onFailure {
                                                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                                            }
                                    },
                                ) {
                                    Text(stringResource(R.string.ai_html_theme_copy_to_edit))
                                }
                            } else {
                                TextButton(onClick = { showVersionHistory = true }) {
                                    Text(stringResource(R.string.ai_html_theme_history))
                                }
                                TextButton(
                                    onClick = {
                                        store.commitTheme(browsingId, message = context.getString(R.string.ai_html_theme_default_commit))
                                            .onSuccess { info ->
                                                Toast.makeText(
                                                    context,
                                                    context.getString(
                                                        R.string.ai_html_theme_committed,
                                                        info.version,
                                                    ),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                refresh()
                                            }
                                            .onFailure {
                                                Toast.makeText(
                                                    context,
                                                    it.message
                                                        ?: context.getString(R.string.ai_html_theme_commit_failed),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                    },
                                    enabled = dirty,
                                ) {
                                    Text(stringResource(R.string.ai_html_theme_commit))
                                }
                            }
                        }
                    }
                    item {
                        val isUserPack = !browsingReadOnly
                        if (isUserPack) {
                            val shellKind = store.shellKindOf(browsingId)
                            if (shellKind == "custom") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.ai_html_theme_shell_custom_notice),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = { showReplaceShellConfirm = true }) {
                                            Text(stringResource(R.string.ai_html_theme_shell_replace))
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.ai_html_theme_shell_upgrade_hint),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = {
                                            store.upgradeSharedShell(browsingId).onSuccess {
                                                refresh()
                                                Toast.makeText(
                                                    context,
                                                    R.string.ai_html_theme_shell_upgraded,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }.onFailure {
                                                Toast.makeText(
                                                    context,
                                                    it.message ?: context.getString(R.string.ai_html_theme_shell_upgraded),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.ai_html_theme_shell_upgrade))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        val mediaPaths = store.listMediaPaths(browsingId)
                        if (mediaPaths.isNotEmpty()) {
                            val referencedMap = mediaPaths.associateWith {
                                store.isMediaReferenced(browsingId, it)
                            }
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                CategorySection(stringResource(R.string.ai_html_theme_media)) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        mediaPaths.forEach { path ->
                                            val meta = store.mediaMeta(browsingId, path)
                                            if (meta != null) {
                                                key(path) {
                                                    MediaCard(
                                                        store = store,
                                                        themeId = browsingId,
                                                        meta = meta,
                                                        referenced = referencedMap[path] == true,
                                                        onImageClick = { previewImagePath = path },
                                                        onFontClick = { previewFontPath = path },
                                                        onSvgClick = {
                                                            openEditor(
                                                                browsingId,
                                                                path,
                                                                readOnly = browsingReadOnly,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    items(paths, key = { it }) { path ->
                        val isBinaryMedia = path.startsWith("media/") &&
                            !AiChatHtmlThemeStore.isTextMediaPath(path)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openEditor(browsingId, path, readOnly = browsingReadOnly || isBinaryMedia)
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (browsingReadOnly || isBinaryMedia) Icons.Default.Visibility else Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                path,
                                modifier = Modifier.padding(start = 12.dp).weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val ext = path.substringAfterLast('.', "").lowercase()
                            if (ext.isNotEmpty() && ext.length <= 8) {
                                Text(
                                    ext.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.ai_html_theme_config_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                        TextButton(
                            onClick = {
                                store.createFromStarter(AiChatHtmlThemeStore.STARTER_BLANK)
                                    .onSuccess { info ->
                                        store.setTheme(info.id)
                                        refresh()
                                        Toast.makeText(
                                            context,
                                            R.string.ai_html_theme_blank_created,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        browsingThemeId = info.id
                                        browsingReadOnly = false
                                    }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message
                                                ?: context.getString(R.string.ai_html_theme_blank_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                            },
                        ) {
                            Text(stringResource(R.string.ai_html_theme_create_blank))
                        }
                    }
                    item {
                        CategorySection(stringResource(R.string.ai_html_theme_packs)) {
                            themes.forEach { theme ->
                                val isUser = store.isUserTheme(theme.id)
                                SettingItem(
                                    title = theme.name,
                                    description = buildString {
                                        append(theme.id)
                                        append(" · v")
                                        append(theme.version)
                                        if (theme.dirty && isUser) {
                                            append(" · ")
                                            append(context.getString(R.string.ai_html_theme_dirty))
                                        }
                                        append(" · ")
                                        append(
                                            if (isUser) {
                                                context.getString(R.string.ai_html_theme_user)
                                            } else {
                                                context.getString(R.string.ai_html_theme_builtin)
                                            },
                                        )
                                    },
                                    trailingContent = {
                                        FilterChip(
                                            selected = theme.id == selectedId || theme.selected,
                                            onClick = {
                                                store.setTheme(theme.id)
                                                refresh()
                                            },
                                            label = {
                                                Text(
                                                    if (theme.id == selectedId) {
                                                        stringResource(R.string.ai_html_theme_active)
                                                    } else {
                                                        stringResource(R.string.ai_html_theme_use)
                                                    },
                                                )
                                            },
                                        )
                                    },
                                    dropdownMenu = { dismiss ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(
                                                        if (isUser) R.string.ai_html_theme_open_files
                                                        else R.string.ai_html_theme_view_files,
                                                    ),
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (isUser) Icons.Default.FolderOpen else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                dismiss()
                                                openFiles(theme.id)
                                            },
                                        )
                                        if (!isUser) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.ai_html_theme_copy)) },
                                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                                onClick = {
                                                    dismiss()
                                                    store.copyBuiltinToUser(theme.id).onSuccess { info ->
                                                        refresh()
                                                        Toast.makeText(
                                                            context,
                                                            R.string.ai_html_theme_copied,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                        browsingThemeId = info.id
                                                        browsingReadOnly = false
                                                    }.onFailure {
                                                        Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                            )
                                        }
                                        if (isUser) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete)) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                                onClick = {
                                                    dismiss()
                                                    showDeleteThemeConfirm = theme.id
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    item {
                        TextButton(onClick = onBack, modifier = Modifier.padding(bottom = 16.dp)) {
                            Text(stringResource(R.string.back))
                        }
                    }
                }
            }
        }
        // Delete theme confirmation
        showDeleteThemeConfirm?.let { themeId ->
            val themeName = themes.find { it.id == themeId }?.name ?: themeId
            AlertDialog(
                onDismissRequest = { showDeleteThemeConfirm = null },
                title = { Text(stringResource(R.string.delete)) },
                text = { Text(stringResource(R.string.sure_del) + " 「$themeName」？") },
                confirmButton = {
                    TextButton(onClick = {
                        store.deleteUser(themeId)
                        if (browsingThemeId == themeId) {
                            browsingThemeId = null
                            browsingReadOnly = false
                        }
                        showDeleteThemeConfirm = null
                        refresh()
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteThemeConfirm = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        // Version history bottom sheet
        if (showVersionHistory && browsingId != null) {
            val currentVersion = store.readManifest(browsingId)?.version ?: 1
            // Dedupe by version (keep the newest record) to survive legacy duplicate commit logs.
            val commits = store.listCommits(browsingId).distinctBy { it.version }
            ModalLegadoBottomSheet(
                show = true,
                onDismissRequest = { showVersionHistory = false },
                title = stringResource(R.string.ai_html_theme_history) + " · $browsingId",
                skipPartiallyExpanded = true,
            ) {
                if (commits.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_html_theme_history_empty),
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                    ) {
                        items(commits, key = { it.version }) { commit ->
                            SettingItem(
                                title = buildString {
                                    append("v")
                                    append(commit.version)
                                    if (commit.message.isNotBlank()) {
                                        append("  ")
                                        append(commit.message)
                                    }
                                },
                                description = formatCommitTime(commit.timestamp),
                                onClick = null,
                                trailingContent = {
                                    if (commit.version == currentVersion) {
                                        Text(
                                            text = stringResource(R.string.ai_html_theme_active),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        TextButton(onClick = { rollbackTargetVersion = commit.version }) {
                                            Text(stringResource(R.string.ai_html_theme_rollback))
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Rollback confirmation
        rollbackTargetVersion?.let { targetVersion ->
            AlertDialog(
                onDismissRequest = { rollbackTargetVersion = null },
                title = { Text(stringResource(R.string.ai_html_theme_rollback_title, targetVersion)) },
                text = { Text(stringResource(R.string.ai_html_theme_rollback_confirm, targetVersion)) },
                confirmButton = {
                    TextButton(onClick = {
                        val themeId = browsingId
                        if (themeId != null) {
                            store.rollbackToVersion(themeId, targetVersion)
                                .onSuccess { info ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.ai_html_theme_rolled_back, info.version),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    refresh()
                                    showVersionHistory = true
                                }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: context.getString(R.string.ai_html_theme_rollback_failed),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                        }
                        rollbackTargetVersion = null
                    }) {
                        Text(stringResource(R.string.ai_html_theme_rollback))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { rollbackTargetVersion = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        // Replace custom shell with latest shared shell confirmation
        if (showReplaceShellConfirm && browsingId != null) {
            val themeId = browsingId
            AlertDialog(
                onDismissRequest = { showReplaceShellConfirm = false },
                title = { Text(stringResource(R.string.ai_html_theme_shell_replace_title)) },
                text = { Text(stringResource(R.string.ai_html_theme_shell_replace_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showReplaceShellConfirm = false
                        store.upgradeSharedShell(themeId, force = true).onSuccess {
                            refresh()
                            Toast.makeText(
                                context,
                                R.string.ai_html_theme_shell_upgraded,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }.onFailure {
                            Toast.makeText(
                                context,
                                it.message ?: context.getString(R.string.ai_html_theme_shell_upgraded),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }) {
                        Text(stringResource(R.string.ai_html_theme_shell_replace))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReplaceShellConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        // Fullscreen image preview
        val previewImage = previewImagePath
        if (previewImage != null && browsingId != null) {
            val bitmap = rememberMediaBitmap(store, browsingId, previewImage)
            Dialog(onDismissRequest = { previewImagePath = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f))
                        .clickable { previewImagePath = null }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = previewImage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 700.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = previewImage.removePrefix("media/"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }
        }

        // Font preview
        val previewFont = previewFontPath
        if (previewFont != null && browsingId != null) {
            val fontFamily = rememberFontFamily(store, browsingId, previewFont)
            AlertDialog(
                onDismissRequest = { previewFontPath = null },
                title = {
                    Text(stringResource(R.string.ai_html_theme_media_font_preview))
                },
                text = {
                    Column {
                        Text(
                            text = previewFont.removePrefix("media/"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "AaBbCcDd 1234 汉字样例",
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = fontFamily,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "The quick brown fox jumps over the lazy dog. 床前明月光，疑是地上霜。",
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = fontFamily,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { previewFontPath = null }) {
                        Text(stringResource(R.string.close))
                    }
                },
            )
        }
    }
}

private fun formatCommitTime(timestamp: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(timestamp)

@Composable
private fun MediaCard(
    store: AiChatHtmlThemeStore,
    themeId: String,
    meta: AiChatHtmlThemeStore.MediaMeta,
    referenced: Boolean,
    onImageClick: () -> Unit,
    onFontClick: () -> Unit,
    onSvgClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val isSvg = meta.extension == "svg"
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    when {
                        isSvg -> onSvgClick()
                        meta.isImage -> onImageClick()
                        meta.isFont -> onFontClick()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                meta.isImage && !isSvg -> {
                    val bmp = rememberMediaBitmap(store, themeId, meta.path)
                    val bitmap = bmp
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = meta.path,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = meta.extension.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                meta.isFont -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = rememberFontFamily(store, themeId, meta.path),
                        )
                        Text(
                            text = meta.extension.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    Text(
                        text = meta.extension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = meta.path.removePrefix("media/"),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (referenced) {
                stringResource(R.string.ai_html_theme_media_referenced)
            } else {
                stringResource(R.string.ai_html_theme_media_unreferenced)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (referenced) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun rememberMediaBitmap(
    store: AiChatHtmlThemeStore,
    themeId: String,
    path: String,
): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, themeId, path) {
        value = withContext(Dispatchers.IO) {
            store.readMediaBytes(themeId, path)
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?.asImageBitmap()
        }
    }
    return state.value
}

@Composable
private fun rememberFontFamily(
    store: AiChatHtmlThemeStore,
    themeId: String,
    path: String,
): FontFamily {
    val typeface = produceState<Typeface?>(null, themeId, path) {
        value = withContext(Dispatchers.IO) {
            store.mediaFile(themeId, path)
                ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
        }
    }.value
    return typeface?.let { FontFamily(it) } ?: FontFamily.Default
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
    return uri.lastPathSegment
}

// -------- pre-compiled regex patterns (created once, reused on every keystroke) --------

private val RE_CSS_COMMENT    = Regex("""/\*[\s\S]*?\*/""")
private val RE_STR_SQ_DQ     = Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""")
private val RE_CSS_AT_RULE   = Regex("""@[a-zA-Z_-][\w-]*""")
private val RE_CSS_SELECTOR  = Regex("""(?m)^\s*[^@\s/{][^\{]*?(?=\s*\{)""")
private val RE_CSS_PROPERTY  = Regex("""(?m)^\s*([-\w]+)(?=\s*:)""")
private val RE_CSS_NUMBER    = Regex("""\b\d+(?:\.\d+)?(?:px|rem|em|vh|vw|%|deg|s|ms)?\b""")
private val RE_CSS_HEX       = Regex("""#[0-9a-fA-F]{3,8}\b""")
private val RE_CSS_IMPORTANT = Regex("""!important\b""")

private val RE_HTML_COMMENT  = Regex("""<!--[\s\S]*?-->""")
private val RE_HTML_DOCTYPE  = Regex("""<!DOCTYPE[^>]*>""", RegexOption.IGNORE_CASE)
private val RE_HTML_TAG      = Regex("""</?[A-Za-z][^>]*?>""")
private val RE_HTML_ATTR     = Regex("""\b[a-zA-Z_:][\w:.-]*(?=\s*=)""")
private val RE_HTML_ENTITY   = Regex("""&[a-zA-Z]+;|&#\d+;|&#x[0-9a-fA-F]+;""")

private val RE_JS_COMMENT    = Regex("""//.*?$|/\*[\s\S]*?\*/""", setOf(RegexOption.MULTILINE))
private val RE_JS_STRING     = Regex("""`(?:\\.|[^`\\])*`|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""")
private val RE_JS_REGEX      = Regex("""/([^/\\]|\\.)+/[gimsuy]*""")
private val RE_JS_NUMBER     = Regex("""\b\d+(?:\.\d+)?\b""")
private val RE_JS_PROPERTY   = Regex("""\b[a-zA-Z_$][\w$]*(?=\s*:)""")
private val RE_JS_KEYWORD    = Regex(
    """\b(const|let|var|function|return|if|else|for|while|do|switch|case|break|""" +
    """continue|try|catch|finally|throw|class|extends|new|this|super|import|""" +
    """from|export|default|async|await|true|false|null|undefined|""" +
    """typeof|instanceof|in|of|delete|void|yield)\b""",
)

private const val MAX_HIGHLIGHT_CHARS = 100_000

private fun findSearchMatches(text: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val source = text.lowercase()
    val target = needle.lowercase()
    val result = mutableListOf<IntRange>()
    var index = 0
    while (true) {
        index = source.indexOf(target, index)
        if (index < 0) break
        result += index until (index + target.length)
        index += target.length.coerceAtLeast(1)
    }
    return result
}

@Composable
private fun rememberHighlightTransformation(
    extension: String,
    searchMatches: List<IntRange>,
    activeSearchMatchIndex: Int,
): VisualTransformation {
    val colorScheme = MaterialTheme.colorScheme
    val schemeRef = remember { mutableStateOf(colorScheme) }
    schemeRef.value = colorScheme
    return remember(extension, searchMatches, activeSearchMatchIndex) {
        HighlightTransformation(extension, searchMatches, activeSearchMatchIndex) { schemeRef.value }
    }
}

private class HighlightTransformation(
    private val extension: String,
    private val searchMatches: List<IntRange>,
    private val activeSearchMatchIndex: Int,
    private val colorScheme: () -> androidx.compose.material3.ColorScheme,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val cs = colorScheme()
        val builder = buildAnnotatedString {
            if (raw.length <= MAX_HIGHLIGHT_CHARS) {
                when (extension) {
                    "css"  -> highlight(raw, cssHighlighters(cs))
                    "html" -> highlight(raw, htmlHighlighters(cs))
                    "js"   -> highlight(raw, jsHighlighters(cs))
                    else   -> append(raw)
                }
            } else {
                append(raw)
            }
            for ((idx, r) in searchMatches.withIndex()) {
                addStyle(
                    SpanStyle(
                        background = if (idx == activeSearchMatchIndex)
                            cs.tertiaryContainer.copy(alpha = 0.95f)
                        else
                            cs.secondaryContainer.copy(alpha = 0.7f),
                        color = cs.onSurface,
                    ),
                    r.first, r.last + 1,
                )
            }
        }
        return TransformedText(builder, OffsetMapping.Identity)
    }
}

// -------- token-based highlighting (single-pass, pre-compiled regexes) --------

private data class Tok(val start: Int, val end: Int, val style: SpanStyle)

private fun AnnotatedString.Builder.highlight(
    text: String,
    rules: List<Pair<Regex, SpanStyle>>,
) {
    val tokens = mutableListOf<Tok>()
    for ((re, style) in rules) {
        re.findAll(text).forEach { m ->
            tokens.add(Tok(m.range.first, m.range.last + 1, style))
        }
    }
    tokens.sortWith(compareBy { it.start })
    var cursor = 0
    for (t in tokens) {
        if (t.start < cursor) continue
        if (t.start > cursor) append(text.substring(cursor, t.start))
        withStyle(t.style) { append(text.substring(t.start, t.end)) }
        cursor = t.end
    }
    if (cursor < text.length) append(text.substring(cursor))
}

private fun cssHighlighters(cs: androidx.compose.material3.ColorScheme) = listOf(
    RE_CSS_COMMENT    to SpanStyle(color = cs.onSurfaceVariant, fontStyle = FontStyle.Italic),
    RE_STR_SQ_DQ      to SpanStyle(color = cs.tertiary),
    RE_CSS_AT_RULE    to SpanStyle(color = cs.primary, fontWeight = FontWeight.SemiBold),
    RE_CSS_SELECTOR   to SpanStyle(color = cs.primary),
    RE_CSS_PROPERTY   to SpanStyle(color = cs.secondary),
    RE_CSS_NUMBER     to SpanStyle(color = cs.error),
    RE_CSS_HEX        to SpanStyle(color = cs.tertiary, fontWeight = FontWeight.Medium),
    RE_CSS_IMPORTANT  to SpanStyle(color = cs.primary, fontWeight = FontWeight.Bold),
)

private fun htmlHighlighters(cs: androidx.compose.material3.ColorScheme) = listOf(
    RE_HTML_COMMENT to SpanStyle(color = cs.onSurfaceVariant, fontStyle = FontStyle.Italic),
    RE_HTML_DOCTYPE to SpanStyle(color = cs.secondary),
    RE_HTML_TAG     to SpanStyle(color = cs.primary, fontWeight = FontWeight.SemiBold),
    RE_STR_SQ_DQ    to SpanStyle(color = cs.tertiary),
    RE_HTML_ATTR    to SpanStyle(color = cs.primary),
    RE_HTML_ENTITY  to SpanStyle(color = cs.error),
)

private fun jsHighlighters(cs: androidx.compose.material3.ColorScheme) = listOf(
    RE_JS_COMMENT to SpanStyle(color = cs.onSurfaceVariant, fontStyle = FontStyle.Italic),
    RE_JS_STRING  to SpanStyle(color = cs.tertiary),
    RE_JS_REGEX   to SpanStyle(color = cs.tertiary),
    RE_JS_KEYWORD to SpanStyle(color = cs.primary, fontWeight = FontWeight.SemiBold),
    RE_JS_PROPERTY to SpanStyle(color = cs.secondary),
    RE_JS_NUMBER  to SpanStyle(color = cs.error),
)
