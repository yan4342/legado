package io.legado.app.ui.ai.chat

import android.app.Activity
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.SystemClock
import io.legado.app.R
import io.legado.app.ui.common.compose.TransparentTopAppBarStatusBar
import io.legado.app.data.AppDatabase
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.WritingInputMode
import io.legado.app.domain.model.WritingInputModeSwitchState
import io.legado.app.domain.model.WritingUserInput
import io.legado.app.help.config.AppConfig
import io.legado.app.help.IntentData
import io.legado.app.lib.theme.LocalAiChatSemanticColors
import io.legado.app.ui.book.info.compose.BookInfoComposeActivity
import io.legado.app.ui.book.searchContent.SearchContentActivity
import io.legado.app.ui.common.compose.VerticalScrollStateScrollbar
import io.legado.app.ui.common.compose.topbar.TopBarAnimatedActionButton
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.share
import io.legado.app.utils.startActivity
import android.content.Intent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import java.time.Duration
import java.time.Instant

/**
 * Formats a timestamp (epoch millis) as a relative time string in Chinese.
 */
fun formatRelativeTime(epochMillis: Long): String {
    val now = Instant.now()
    val then = Instant.ofEpochMilli(epochMillis)
    val duration = Duration.between(then, now)
    val seconds = duration.seconds
    val minutes = duration.toMinutes()
    val hours = duration.toHours()
    val days = duration.toDays()

    return when {
        seconds < 60 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 2 -> "昨天"
        days < 7 -> "${days}天前"
        days < 30 -> "${days / 7}周前"
        days < 365 -> "${days / 30}个月前"
        else -> "${days / 365}年前"
    }
}

/** Anchor captured before loading earlier messages so scroll position can be restored. */
private data class HistoryScrollAnchor(
    val anchorMessageId: String?,
    val firstVisibleIndex: Int,
    val scrollOffset: Int,
    val messageCount: Int,
)

/** Synthetic message id for the folded-history summary shown at the top of the timeline. */
private const val CONTEXT_SUMMARY_MESSAGE_ID = "context_compressed_summary"

/** Synthetic message id for the in-progress compression indicator at the bottom of the timeline. */
private const val CONTEXT_COMPRESSING_MESSAGE_ID = "context_compressing"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel,
    onBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToAiChatColors: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var toolsPanelOpen by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    val inputText = inputValue.text
    var inputModeSwitchState by remember { mutableStateOf(WritingInputModeSwitchState()) }
    var inputModeName by rememberSaveable { mutableStateOf(WritingInputMode.DIALOGUE.name) }
    val inputMode = WritingInputMode.valueOf(inputModeName)
    val writingInputHint = when (inputMode) {
        WritingInputMode.ACTION -> stringResource(R.string.ai_input_hint_action)
        WritingInputMode.DIALOGUE -> stringResource(R.string.ai_input_hint_dialogue)
    }

    val atMentionQuery = remember(inputText) {
        val lastAt = inputText.lastIndexOf('@')
        if (lastAt < 0) null
        else {
            val after = inputText.substring(lastAt + 1)
            if (after.contains(' ') || after.contains('\n')) null
            else after
        }
    }
    val atMentionCards = remember(atMentionQuery, state.selectedCharacterCards) {
        atMentionQuery?.let { viewModel.matchAtMention(it) }.orEmpty()
    }
    val showAtMentionMenu = atMentionCards.isNotEmpty()

    val slashQuery = remember(inputText) {
        io.legado.app.domain.usecase.ai.SlashCommandCatalog.completionQuery(inputText)
    }
    val slashCommands = remember(slashQuery) {
        slashQuery?.let { viewModel.matchSlashCommands(it) }.orEmpty()
    }
    val showSlashMenu = slashCommands.isNotEmpty()

    // Only persist draft after SetInputText restores this conversation's draft,
    // so a switch cannot write the previous composer text into the new session.
    var draftReadyConversationId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.currentConversationId) {
        if (draftReadyConversationId != state.currentConversationId) {
            draftReadyConversationId = null
        }
    }
    LaunchedEffect(inputText, state.isSending, draftReadyConversationId, state.currentConversationId) {
        val readyId = draftReadyConversationId ?: return@LaunchedEffect
        if (!state.isSending && readyId == state.currentConversationId) {
            viewModel.onIntent(AiChatIntent.UpdateDraftInput(inputText))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiChatEffect.ShowMessage -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                is AiChatEffect.SetInputText -> {
                    inputValue = effect.text.toTextFieldValueAtEnd()
                    inputModeSwitchState = WritingInputModeSwitchState()
                    draftReadyConversationId = effect.forConversationId ?: state.currentConversationId
                }
                is AiChatEffect.CopyToClipboard -> {
                    context.sendToClip(effect.text)
                }
                is AiChatEffect.ShareText -> {
                    context.share(effect.text, effect.title)
                }
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val semanticColors = LocalAiChatSemanticColors.current

    TransparentTopAppBarStatusBar(barColor = colorScheme.surface)

    // Edge-to-edge: bottomBar uses Compose WindowInsets. Keep ADJUST_NOTHING so the system
    // does not also shrink the window (that combo leaves a blank gap above the keyboard).
    // Re-apply on ON_RESUME — sheets/dialogs can change softInputMode while we stay composed.
    // After setSoftInputMode, re-assert status-bar icons: OEM Android 13 often resets them.
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLightStatusBar = ColorUtils.isColorLight(colorScheme.surface.toArgb())
    val latestLightStatusBar = rememberUpdatedState(isLightStatusBar)
    DisposableEffect(lifecycleOwner) {
        val activity = view.context as Activity
        val window = activity.window
        @Suppress("DEPRECATION")
        val previousMode = window.attributes.softInputMode
        fun applyAdjustNothing() {
            @Suppress("DEPRECATION")
            val current = window.attributes.softInputMode
            window.setSoftInputMode(
                (current and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv())
                    or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            )
            activity.setLightStatusBar(latestLightStatusBar.value)
        }
        applyAdjustNothing()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) applyAdjustNothing()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            window.setSoftInputMode(previousMode)
        }
    }

    // Re-assert when keyboard opens without an Activity pause (e.g. after in-page sheets).
    val imeVisibleForSoftInput = WindowInsets.isImeVisible
    LaunchedEffect(imeVisibleForSoftInput) {
        if (!imeVisibleForSoftInput) return@LaunchedEffect
        val activity = view.context as Activity
        val window = activity.window
        @Suppress("DEPRECATION")
        val current = window.attributes.softInputMode
        window.setSoftInputMode(
            (current and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv())
                or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
        )
        activity.setLightStatusBar(latestLightStatusBar.value)
    }

    val ds = remember { AiChatDialogState() }//ds 是一个 AiChatDialogState 的实例，用于管理对话框的状态。
    var showContextBar by remember { mutableStateOf(true) }
    var galgameHudExpanded by remember { mutableStateOf(false) }
    var hudImportError by remember { mutableStateOf<String?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }

    // File launchers for HUD import/export
    val hudExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = """{"type":"legado_galgame_hud","galgameHudHtml":${io.legado.app.utils.GSON.toJson(state.galgameHudHtml)}}"""
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, R.string.ai_hud_exported, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "${R.string.ai_hud_export_failed}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val hudImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                if (json != null) {
                    val obj = io.legado.app.utils.GSON.fromJson(json, com.google.gson.JsonObject::class.java)
                    val html = obj?.get("galgameHudHtml")?.asString
                    if (!html.isNullOrBlank()) {
                        viewModel.onIntent(AiChatIntent.ImportHudHtml(html))
                    } else {
                        hudImportError = context.getString(R.string.ai_hud_import_invalid)
                    }
                }
            } catch (e: Exception) {
                hudImportError = "${context.getString(R.string.ai_hud_import_failed)}: ${e.message}"
            }
        }
    }

    // Collapse HUD when new HTML arrives
    LaunchedEffect(state.galgameHudHtml) {
        galgameHudExpanded = false
    }
    // Show import error toast
    LaunchedEffect(hudImportError) {
        hudImportError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            hudImportError = null
        }
    }

    // File launchers for outline import/export
    val outlineExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(state.outlineExportJson.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, R.string.ai_outline_exported, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "${context.getString(R.string.ai_outline_export_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val outlineImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                if (!json.isNullOrBlank()) {
                    viewModel.onIntent(AiChatIntent.UpdateOutlineImportJson(json))
                    if (!state.showOutlineImportDialog) {
                        viewModel.onIntent(AiChatIntent.ShowOutlineImportDialog)
                    }
                } else {
                    Toast.makeText(context, R.string.ai_outline_import_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "${context.getString(R.string.ai_outline_import_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onIntent(AiChatIntent.AddAttachmentsFromUris(uris))
        }
    }

    var editingText by remember { mutableStateOf("") }

    val isWritingMode = state.conversationType == "writing"

    // Messages including streaming temp as the last item when generating.
    // A persisted compression summary is rendered as a regular message at the top of the
    // timeline — it replaces the folded-away early messages. While a compression is running,
    // a progress message is appended at the bottom.
    val displayMessages = remember(
        state.messages, state.streamingMessage, state.displayLimit,
        state.regeneratingMessageId, state.compressedSummary, state.isCompressing,
    ) {
        val base = state.messages
            .filter { it.id != state.regeneratingMessageId }
            .takeLast(state.displayLimit)
        val withSummary = if (state.compressedSummary.isNotBlank()) {
            listOf(
                AiChatMessageUi(
                    id = CONTEXT_SUMMARY_MESSAGE_ID,
                    role = AiMessageRole.ASSISTANT,
                    content = state.compressedSummary,
                    createdAt = Long.MIN_VALUE,
                ),
            ) + base
        } else {
            base
        }
        val withProgress = if (state.isCompressing) {
            withSummary + AiChatMessageUi(
                id = CONTEXT_COMPRESSING_MESSAGE_ID,
                role = AiMessageRole.ASSISTANT,
                content = context.getString(R.string.ai_context_compressing),
                createdAt = System.currentTimeMillis(),
            )
        } else {
            withSummary
        }
        val streamMsg = state.streamingMessage
        val result = if (streamMsg != null) {
            val last = withProgress.lastOrNull()
            val alreadySaved = last != null &&
                last.role == AiMessageRole.ASSISTANT &&
                ((streamMsg.content.isNotBlank() && last.content == streamMsg.content) ||
                    (last.parts.isNotEmpty() && last.parts == streamMsg.parts))
            if (alreadySaved) withProgress
            else withProgress + streamMsg
        } else {
            withProgress
        }
        result
    }

    val currentConvTitle = state.conversations.find { it.isSelected }?.title
        ?: state.conversations.firstOrNull()?.title.orEmpty()

    // HTML App 播放器全屏打开时屏蔽左右会话列表与工具面板（关闭 + 禁手势）。
    val htmlAppActive = state.activeHtmlApp != null
    LaunchedEffect(htmlAppActive) {
        if (htmlAppActive) {
            toolsPanelOpen = false
            drawerState.close()
        }
    }

    LaunchedEffect(toolsPanelOpen) {
        if (toolsPanelOpen) drawerState.close()
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.isOpen) toolsPanelOpen = false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Only keep Material close-gestures while the left drawer is open.
        // Opening either drawer is handled by EndModalDrawer so its left-swipe
        // is not stolen by ModalNavigationDrawer's full-width anchoredDraggable.
        gesturesEnabled = drawerState.isOpen && !toolsPanelOpen && !htmlAppActive,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.ai_recent_chats),
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(state.conversations, key = { it.id }) { conv ->
                            NavigationDrawerItem(
                                label = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (conv.type == "writing") {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = colorScheme.primary,
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                conv.title.ifBlank { "Chat ${conv.id.take(8)}" },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            text = formatRelativeTime(conv.updatedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                selected = conv.isSelected,
                                onClick = {
                                    viewModel.onIntent(AiChatIntent.SelectConversation(conv.id))
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                badge = {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                ds.renameDialogConv = conv
                                                ds.renameText = conv.title
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                modifier = Modifier.size(16.dp),
                                                tint = colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                ds.showDeleteConversationConfirm = conv
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                modifier = Modifier.size(16.dp),
                                                tint = colorScheme.error,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToAiChatColors()
                            },
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Palette,
                                contentDescription = stringResource(R.string.ai_chat_color_config),
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToAiSettings()
                            },
                            modifier = Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.ai_config),
                            )
                        }
                    }
                }
            }
        },
) {
    EndModalDrawer(
        isOpen = toolsPanelOpen,
        onOpen = {
            toolsPanelOpen = true
            scope.launch { drawerState.close() }
        },
        onClose = { toolsPanelOpen = false },
        gesturesEnabled = !htmlAppActive,
        isLeftDrawerOpen = drawerState.isOpen,
        onOpenLeftDrawer = {
            toolsPanelOpen = false
            scope.launch { drawerState.open() }
        },
        onCloseLeftDrawer = { scope.launch { drawerState.close() } },
        drawerContent = {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "写作工具",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { toolsPanelOpen = false },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.selectedCharacterCards.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.selectedCharacterCards.forEach { card ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    ds.editingCharacter = card
                                    ds.showCharacterEditDialog = true
                                },
                                label = {
                                    Text(
                                        card.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp).clickable {
                                            val remaining = state.selectedCharacterCards.filter { it.id != card.id }.map { it.id }.toSet()
                                            viewModel.onIntent(AiChatIntent.UpdateConversationCharacters(remaining))
                                        },
                                    )
                                },
                            )
                        }
                        AssistChip(
                            onClick = {
                                ds.selectedCardIdsForSheet = state.selectedCharacterCards.map { it.id }.toSet()
                                ds.showCharacterMultiSelect = true
                            },
                            label = { Text("+", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                } else if (state.selectedCharacterCard != null) {
                    InputChip(
                        selected = true,
                        onClick = {
                            state.selectedCharacterCard?.let { card ->
                                ds.editingCharacter = card
                                ds.showCharacterEditDialog = true
                            }
                        },
                        label = {
                            Text(
                                state.selectedCharacterCard!!.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    ds.selectedCardIdsForSheet = setOf(state.selectedCharacterCard!!.id)
                                    ds.showCharacterMultiSelect = true
                                },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Manage characters", modifier = Modifier.size(14.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    AssistChip(
                        onClick = {
                            ds.selectedCardIdsForSheet = emptySet()
                            ds.showCharacterMultiSelect = true
                        },
                        label = { Text(stringResource(R.string.ai_no_character), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                InputChip(
                    selected = state.userCardEnabled,
                    onClick = {
                        ds.clearSheetTarget()
                        ds.showUserCardEditDialog = true
                    },
                    label = {
                        Text(
                            state.userName.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.ai_user_description),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                InputChip(
                    selected = false,
                    onClick = { ds.showPromptSheet = true },
                    label = { Text(stringResource(R.string.ai_writing_prompt), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                InputChip(
                    selected = state.conversationSkillIds != null,
                    onClick = { ds.showSkillSheet = true },
                    label = { Text(stringResource(R.string.ai_conversation_skills), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                InputChip(
                    selected = false,
                    onClick = { ds.showWorldBookSheet = true },
                    label = { Text(stringResource(R.string.ai_world_book), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                InputChip(
                    selected = state.workspaceId != null,
                    onClick = {
                        toolsPanelOpen = false
                        viewModel.onIntent(AiChatIntent.ShowWorkspaceSheet)
                    },
                    label = {
                        Text(
                            if (state.isMaintainingStructuredData) {
                                stringResource(R.string.ai_workspace_maintaining)
                            } else {
                                stringResource(R.string.ai_workspace)
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                InputChip(
                    selected = false,
                    onClick = {
                        ds.clearSheetTarget()
                        ds.showMemoryTableSheet = true
                    },
                    label = { Text(stringResource(R.string.ai_workspace_memory_tables), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                InputChip(
                    selected = state.outlineEnabled,
                    onClick = {
                        ds.clearSheetTarget()
                        ds.showOutlineSheet = true
                    },
                    label = { Text(stringResource(R.string.ai_workspace_outline), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                InputChip(
                    selected = false,
                    onClick = {
                        ds.showExecutionHistorySheet = true
                    },
                    label = {
                        Text(
                            stringResource(R.string.ai_execution_history_title),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Inter-character chat toggle (multi-character only)
                if (state.selectedCharacterCards.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "角色间互动",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.interCharacterChatEnabled,
                            onCheckedChange = { viewModel.onIntent(AiChatIntent.ToggleInterCharacterChat) },
                        )
                    }
                }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "润色",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.postEditEnabled,
                            onCheckedChange = { viewModel.onIntent(AiChatIntent.TogglePostEdit) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.ai_dialogue_highlight),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.dialogueHighlightEnabled,
                            onCheckedChange = { viewModel.onIntent(AiChatIntent.ToggleDialogueHighlight) },
                        )
                    }
                    if (isWritingMode && state.writingSubMode == "roleplay") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.ai_roleplay_dialogue_bubble),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = state.roleplayDialogueBubbleEnabled,
                                onCheckedChange = {
                                    viewModel.onIntent(AiChatIntent.ToggleRoleplayDialogueBubble)
                                },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Galgame HUD",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.galgameEnabled,
                            onCheckedChange = { viewModel.onIntent(AiChatIntent.ToggleGalgame) },
                        )
                    }
                // HUD import/export
                if (isWritingMode && state.galgameEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.ai_hud_style),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { hudImportLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_import),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.ai_hud_import),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                hudExportLauncher.launch("galgame_hud_${System.currentTimeMillis()}.json")
                            },
                            enabled = state.galgameHudHtml.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_export),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.ai_hud_export),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        },
    ) {
    Scaffold(
            containerColor = colorScheme.background,
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (currentConvTitle.isNotBlank()) currentConvTitle else stringResource(R.string.ai_chat),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            state.conversations.find { it.isSelected }?.let { conv ->
                                if (state.isSending) {
                                    val typingName = state.streamingMessage?.speakerName
                                    if (typingName.isNullOrBlank()) {
                                        Text(
                                            text = generationStatusLabel(
                                                streaming = state.streamingMessage,
                                                isSending = state.isSending,
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Text(
                                            text = "${typingName} 正在输入…",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = semanticColors.speakerColors.getOrElse(
                                                state.streamingMessage?.speakerColorIndex ?: 0,
                                            ) { semanticColors.speakerColors[0] },
                                        )
                                    }
                                } else if (conv.providerName.isNotBlank()) {
                                    Text(
                                        text = "${conv.providerName} / ${conv.modelName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.padding(start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.conversations.size > 1) {
                                IconButton(onClick = {
                                    toolsPanelOpen = false
                                    scope.launch { drawerState.open() }
                                },
                                            modifier = Modifier.size(35.dp)) 
                                {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_menu),
                                        contentDescription = "Chats",
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.onIntent(AiChatIntent.NewConversation) },
                                        modifier = Modifier.size(35.dp)) 
                            {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ai_new_chat))
                            }
                        }
                    },
                    actions = {
                        Row(
                            modifier = Modifier.padding(end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(
                                selected = !isWritingMode,
                                onClick = {
                                    if (isWritingMode) viewModel.onIntent(AiChatIntent.SwitchMode("chat"))
                                },
                                label = { Text(stringResource(R.string.ai_chat_mode), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 0.dp),
                            )
                            FilterChip(
                                selected = isWritingMode,
                                onClick = {
                                    if (!isWritingMode) viewModel.onIntent(AiChatIntent.SwitchMode("writing"))
                                },
                                label = { Text(stringResource(R.string.ai_writing_mode), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 0.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.surface,
                        titleContentColor = colorScheme.onSurface,
                    ),
                )
            },
            bottomBar = {
                AiChatBottomBar(
                    state = state,
                    viewModel = viewModel,
                    dialogState = ds,
                    inputValue = inputValue,
                    onInputValueChange = { inputValue = it },
                    inputText = inputText,
                    inputMode = inputMode,
                    onInputModeChange = { mode, text, switchState ->
                        inputModeName = mode.name
                        inputValue = text
                        inputModeSwitchState = switchState
                    },
                    inputModeSwitchState = inputModeSwitchState,
                    onInputModeSwitchStateChange = { inputModeSwitchState = it },
                    isWritingMode = isWritingMode,
                    writingInputHint = writingInputHint,
                    showAtMentionMenu = showAtMentionMenu && !showSlashMenu,
                    atMentionCards = atMentionCards,
                    showSlashMenu = showSlashMenu,
                    slashCommands = slashCommands,
                    galgameHudExpanded = galgameHudExpanded,
                    onGalgameHudExpandedChange = { galgameHudExpanded = it },
                    showContextBar = showContextBar,
                    onShowContextBarChange = { showContextBar = it },
                    toolsPanelOpen = toolsPanelOpen,
                    onToolsPanelOpenChange = { toolsPanelOpen = it },
                    drawerState = drawerState,
                    scope = scope,
                    onAttachClick = {
                        attachmentLauncher.launch(
                            arrayOf(
                                "image/*",
                                "application/pdf",
                                "text/plain",
                                "application/epub+zip",
                            ),
                        )
                    },
                )
            },
        ) { padding ->
            val density = LocalDensity.current
            val imeBottom = WindowInsets.ime.getBottom(density)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(
                        if (!AppConfig.isEInkMode) Modifier.nestedScroll(
                            remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {} }
                        ) else Modifier
                    ),
            ) {
                // Measured height of the tool-approval overlay (incl. enter/exit animation).
                // Shrink the message list viewport by this amount so messages are not covered,
                // without changing LazyColumn scroll anchors via contentPadding.
                var approvalOverlayHeightPx by remember { mutableIntStateOf(0) }
                val approvalOverlayBottomPad = with(density) { approvalOverlayHeightPx.toDp() }
                val approvalOverlayActive =
                    state.pendingToolConfs.isNotEmpty() || approvalOverlayHeightPx > 0
                // Stay frozen briefly after the panel dismisses so confirm/reject + tool
                // execution cannot yank the list via scrollToItem(last).
                var approvalScrollFrozen by remember { mutableStateOf(false) }

                //dialog region was moved to AiChatDialogs.kt
                key(state.currentConversationId) {
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = (displayMessages.size - 1).coerceAtLeast(0)
                    )
                    var historyScrollAnchor by remember { mutableStateOf<HistoryScrollAnchor?>(null) }
                    var historyLoadCooldownUntil by remember { mutableStateOf(0L) }
                    // Once the user leaves the bottom, pin browsing mode so size/layout glitches
                    // cannot yank the list back toward the newest messages.
                    var browsingHistory by remember { mutableStateOf(false) }
                    var previousConversationId by remember { mutableStateOf(state.currentConversationId) }

                    LaunchedEffect(approvalOverlayActive) {
                        if (approvalOverlayActive) {
                            approvalScrollFrozen = true
                            return@LaunchedEffect
                        }
                        if (approvalScrollFrozen) {
                            browsingHistory = true
                            kotlinx.coroutines.delay(500)
                            approvalScrollFrozen = false
                        }
                    }

                    // Pin to latest messages on conversation switch (including when messages
                    // arrive before uiState.currentConversationId catches up).
                    var previousDisplaySize by remember { mutableIntStateOf(displayMessages.size) }
                    var previousLastId by remember { mutableStateOf(displayMessages.lastOrNull()?.id) }
                    var wasCompressing by remember { mutableStateOf(state.isCompressing) }
                    LaunchedEffect(
                        state.currentConversationId,
                        displayMessages.size,
                        displayMessages.lastOrNull()?.id,
                        state.isCompressing,
                    ) {
                        val cid = state.currentConversationId
                        val conversationChanged = cid != previousConversationId
                        val lastId = displayMessages.lastOrNull()?.id
                        val size = displayMessages.size
                        val lostStreamingPlaceholder =
                            previousLastId == "streaming_temp" &&
                                lastId != "streaming_temp" &&
                                size < previousDisplaySize
                        // Compression finished: the summary became the first message at the top
                        // of the timeline, replacing the folded-away history. Scroll to reveal it.
                        val compressionJustFinished =
                            wasCompressing && !state.isCompressing && state.compressedSummary.isNotBlank()
                        previousDisplaySize = size
                        previousLastId = lastId
                        wasCompressing = state.isCompressing
                        if (conversationChanged) {
                            previousConversationId = cid
                            browsingHistory = false
                        }
                        if (displayMessages.isEmpty()) return@LaunchedEffect
                        if (state.isLoadingHistory || historyScrollAnchor != null) return@LaunchedEffect
                        if (!conversationChanged && browsingHistory && !compressionJustFinished) {
                            return@LaunchedEffect
                        }
                        // Approval overlay open/close/dismiss-settle must not yank the list.
                        if (!conversationChanged && approvalScrollFrozen) return@LaunchedEffect
                        // Persist race: streaming cleared before DB row appears — list briefly
                        // ends on the previous message; do not scroll-pin that transient state.
                        if (!conversationChanged && lostStreamingPlaceholder) return@LaunchedEffect
                        snapshotFlow { listState.layoutInfo.totalItemsCount }
                            .first { it >= displayMessages.size }
                        if (compressionJustFinished) {
                            browsingHistory = true
                            listState.scrollToItem(0)
                        } else {
                            listState.scrollToItem(displayMessages.lastIndex)
                        }
                    }

                    val atBottom by remember {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val lastIndex = info.totalItemsCount - 1
                            if (lastIndex < 0) return@derivedStateOf true
                            val lastVisible = info.visibleItemsInfo.lastOrNull()
                                ?: return@derivedStateOf false
                            // Require the last item to be visible — avoids false "at bottom" during layout.
                            lastVisible.index >= lastIndex && !listState.canScrollForward
                        }
                    }
                    val scrollToBottomThresholdPx = with(density) { 80.dp.roundToPx() }
                    // Show FAB when the last item is fully off-screen, or still visible but
                    // farther than the threshold from the viewport bottom.
                    val showScrollToBottom by remember(scrollToBottomThresholdPx) {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val lastIndex = info.totalItemsCount - 1
                            if (lastIndex < 0) return@derivedStateOf false
                            val lastVisible = info.visibleItemsInfo.lastOrNull()
                                ?: return@derivedStateOf false
                            if (lastVisible.index < lastIndex) return@derivedStateOf true
                            val lastBottom = lastVisible.offset + lastVisible.size
                            lastBottom < info.viewportEndOffset - scrollToBottomThresholdPx
                        }
                    }
                    LaunchedEffect(showScrollToBottom) {
                        if (showScrollToBottom) browsingHistory = true
                    }
                    LaunchedEffect(state.isSending) {
                        // Confirm keeps isSending=true; clearing browsingHistory here would
                        // undo the post-approval freeze and yank the list upward.
                        if (state.isSending && !approvalScrollFrozen) browsingHistory = false
                    }
                    // User scrolled back to the bottom manually — resume stick-to-bottom.
                    LaunchedEffect(atBottom, showScrollToBottom, approvalScrollFrozen) {
                        if (approvalScrollFrozen) return@LaunchedEffect
                        if (
                            atBottom &&
                            !showScrollToBottom &&
                            historyScrollAnchor == null &&
                            !state.isLoadingHistory
                        ) {
                            kotlinx.coroutines.delay(100)
                            if (
                                !approvalScrollFrozen &&
                                atBottom &&
                                !showScrollToBottom &&
                                historyScrollAnchor == null &&
                                !state.isLoadingHistory
                            ) {
                                browsingHistory = false
                            }
                        }
                    }
                    val allowStickToBottom =
                        atBottom &&
                            !browsingHistory &&
                            historyScrollAnchor == null &&
                            !state.isLoadingHistory &&
                            !approvalScrollFrozen

                    // Restore scroll position after history load; never auto-scroll to bottom here.
                    LaunchedEffect(state.isLoadingHistory, displayMessages.size) {
                        val anchor = historyScrollAnchor ?: return@LaunchedEffect
                        if (state.isLoadingHistory) return@LaunchedEffect
                        browsingHistory = true
                        if (displayMessages.size > anchor.messageCount) {
                            val added = displayMessages.size - anchor.messageCount
                            val targetIndex = anchor.anchorMessageId?.let { id ->
                                displayMessages.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                            } ?: (anchor.firstVisibleIndex + added).coerceIn(0, displayMessages.lastIndex)
                            snapshotFlow { listState.layoutInfo.totalItemsCount }
                                .first { it >= displayMessages.size }
                            listState.scrollToItem(targetIndex, anchor.scrollOffset)
                        }
                        historyLoadCooldownUntil = SystemClock.uptimeMillis() + 400L
                        historyScrollAnchor = null
                    }

                    // Safety scroll on new messages only when the user is still pinned to the bottom.
                    // Skip while the approval overlay is up/animating/settling after dismiss.
                    LaunchedEffect(displayMessages.size) {
                        if (displayMessages.isEmpty()) return@LaunchedEffect
                        if (
                            browsingHistory ||
                            historyScrollAnchor != null ||
                            state.isLoadingHistory ||
                            approvalScrollFrozen
                        ) {
                            return@LaunchedEffect
                        }
                        // Re-read after a frame so layout is ready; avoid false at-bottom during prepend.
                        kotlinx.coroutines.delay(16)
                        if (
                            browsingHistory ||
                            historyScrollAnchor != null ||
                            state.isLoadingHistory ||
                            approvalScrollFrozen
                        ) {
                            return@LaunchedEffect
                        }
                        if (!listState.canScrollForward) {
                            val lastIndex = displayMessages.size - 1
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                            if (lastVisible != null && lastVisible.index >= lastIndex) {
                                listState.scrollToItem(lastIndex)
                            }
                        }
                    }

                    // Scroll to bottom when the keyboard opens, only if already at the bottom.
                    LaunchedEffect(imeBottom) {
                        if (imeBottom > 0 && displayMessages.isNotEmpty() && allowStickToBottom) {
                            kotlinx.coroutines.delay(50)
                            listState.animateScrollToItem(displayMessages.size - 1)
                        }
                    }

                    // Scroll to bottom when the user sends a message (not while approval overlay
                    // is up or settling after dismiss/confirm).
                    LaunchedEffect(state.isSending) {
                        if (state.isSending && !approvalScrollFrozen) {
                            kotlinx.coroutines.delay(50)
                            if (approvalScrollFrozen) return@LaunchedEffect
                            val lastIdx = displayMessages.size - 1
                            if (lastIdx >= 0) listState.animateScrollToItem(lastIdx)
                        }
                    }

                    // During streaming, nudge ONCE when content starts arriving.
                    var hasNudgedThisRound by remember { mutableStateOf(false) }
                    if (!state.isSending) hasNudgedThisRound = false
                    LaunchedEffect(state.streamingMessage?.content?.length ?: 0) {
                        val len = state.streamingMessage?.content?.length ?: 0
                        if (len > 0 && state.isSending && !hasNudgedThisRound && allowStickToBottom) {
                            hasNudgedThisRound = true
                            kotlinx.coroutines.delay(80)
                            if (approvalScrollFrozen) return@LaunchedEffect
                            if (listState.canScrollForward) {
                                val viewportH = listState.layoutInfo.viewportSize.height
                                if (viewportH > 0) {
                                    val nudgePx = (viewportH * 0.1f).toInt().coerceAtLeast(1)
                                    val firstVisible = listState.firstVisibleItemIndex
                                    val currentOffset = listState.firstVisibleItemScrollOffset
                                    listState.animateScrollToItem(firstVisible, currentOffset + nudgePx)
                                }
                            }
                        }
                    }

                    // Auto-load earlier messages when scrolling to the top (user-driven only).
                    LaunchedEffect(state.hasMoreHistory) {
                        snapshotFlow {
                            Triple(
                                listState.firstVisibleItemIndex,
                                state.isLoadingHistory,
                                state.hasMoreHistory,
                            )
                        }.collect { (index, loading, hasMore) ->
                            if (
                                index <= 1 &&
                                displayMessages.isNotEmpty() &&
                                hasMore &&
                                !loading &&
                                historyScrollAnchor == null &&
                                SystemClock.uptimeMillis() >= historyLoadCooldownUntil
                            ) {
                                browsingHistory = true
                                val firstIdx = listState.firstVisibleItemIndex
                                historyScrollAnchor = HistoryScrollAnchor(
                                    anchorMessageId = displayMessages.getOrNull(firstIdx)?.id,
                                    firstVisibleIndex = firstIdx,
                                    scrollOffset = listState.firstVisibleItemScrollOffset,
                                    messageCount = displayMessages.size,
                                )
                                viewModel.onIntent(AiChatIntent.LoadMoreMessages)
                            }
                        }
                    }

                    val pendingApprovalToolIds = remember(state.pendingToolConfs) {
                        state.pendingToolConfs.map { it.callId }.toSet()
                    }
                    val pendingApprovalTiers = remember(state.pendingToolConfs) {
                        state.pendingToolConfs.associate { it.callId to it.tier }
                    }
                    // Same-turn multi-bubbles: action row only on the last bubble of the group.
                    val assistantActionMessageIds = remember(displayMessages) {
                        buildSet {
                            displayMessages
                                .asSequence()
                                .filter { it.role == AiMessageRole.ASSISTANT }
                                .groupBy { it.parentMessageId ?: it.id }
                                .values
                                .forEach { group -> add(group.last().id) }
                        }
                    }
                    // Chat: soft bubble bg only when this turn actually split into multiple bubbles.
                    val chatMultiBubbleMessageIds = remember(displayMessages, isWritingMode) {
                        if (isWritingMode) {
                            emptySet()
                        } else {
                            displayMessages
                                .asSequence()
                                .filter {
                                    it.role == AiMessageRole.ASSISTANT &&
                                        !it.parentMessageId.isNullOrBlank()
                                }
                                .groupBy { it.parentMessageId!! }
                                .filter { (_, group) -> group.size > 1 }
                                .values
                                .flatten()
                                .mapTo(mutableSetOf()) { it.id }
                        }
                    }

                    LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = approvalOverlayBottomPad)
                        .clipToBounds(),
                    state = listState,
                    reverseLayout = false,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    // Unique per message: multi-bubble replies share parentMessageId but must not
                    // collide. Branch switches remount by id (group swap handled in ViewModel).
                    items(
                        displayMessages,
                        key = { msg -> msg.id },
                    ) { msg ->
                        val isContextSynthetic =
                            msg.id == CONTEXT_SUMMARY_MESSAGE_ID || msg.id == CONTEXT_COMPRESSING_MESSAGE_ID
                        val showAssistantActions =
                            msg.role != AiMessageRole.ASSISTANT || msg.id in assistantActionMessageIds
                        ChatMessageItem(
                            msg = msg,
                            modifier = Modifier.fillMaxWidth(),
                            isEditing = editingMessageId == msg.id,
                            editText = editingText,
                            onEditTextChange = { editingText = it },
                            onStartEdit = if (isContextSynthetic) null else {
                                {
                                    editingMessageId = msg.id
                                    editingText = msg.content
                                }
                            },
                            onSaveEdit = if (isContextSynthetic) null else {
                                {
                                    if (editingText.isNotBlank()) {
                                        viewModel.onIntent(AiChatIntent.EditMessage(msg.id, editingText))
                                    }
                                    editingMessageId = null
                                }
                            },
                            onCancelEdit = { editingMessageId = null },
                            onRegenerate = if (isContextSynthetic) null else when {
                                !showAssistantActions && msg.role == AiMessageRole.ASSISTANT -> null
                                msg.role == AiMessageRole.USER -> {
                                    val hasChild = state.messages.any { it.parentMessageId == msg.id && it.role == AiMessageRole.ASSISTANT }
                                    if (!hasChild) {
                                        { viewModel.onIntent(AiChatIntent.RegenerateFromUserMessage(msg.id)) }
                                    } else null
                                }
                                else -> {
                                    { viewModel.onIntent(AiChatIntent.RegenerateMessage(msg.id)) }
                                }
                            },
                            onSwitchBranch = if (isContextSynthetic) null else if (showAssistantActions) {
                                { direction ->
                                    viewModel.onIntent(AiChatIntent.SwitchBranch(msg.id, direction))
                                }
                            } else null,
                            onDelete = if (isContextSynthetic) null else if (showAssistantActions || msg.role == AiMessageRole.USER) {
                                { viewModel.onIntent(AiChatIntent.DeleteMessage(msg.id)) }
                            } else null,
                            onEdit = if (isContextSynthetic) null else if (showAssistantActions || msg.role == AiMessageRole.USER) {
                                {
                                    editingMessageId = msg.id
                                    editingText = msg.content
                                }
                            } else null,
                            onFork = if (isContextSynthetic) null else if (showAssistantActions || msg.role == AiMessageRole.USER) {
                                { viewModel.onIntent(AiChatIntent.ForkConversation(msg.id)) }
                            } else null,
                            conversationId = state.currentConversationId,
                            onToolClick = { tool ->
                                val contentSearch = resolveToolContentSearchNav(
                                    tool.toolName, tool.input, tool.output,
                                )
                                if (contentSearch != null) {
                                    if (contentSearch.results.isNotEmpty()) {
                                        IntentData.put("searchResultList", contentSearch.results)
                                    }
                                    context.startActivity(
                                        Intent(context, SearchContentActivity::class.java).apply {
                                            putExtra("bookUrl", contentSearch.bookUrl)
                                            putExtra("searchWord", contentSearch.query)
                                            putExtra("searchResultIndex", 0)
                                        },
                                    )
                                } else {
                                    val bookNav = resolveToolBookNav(tool.toolName, tool.input, tool.output)
                                    if (bookNav != null) {
                                        context.startActivity<BookInfoComposeActivity> {
                                            putExtra("name", bookNav.name)
                                            putExtra("author", bookNav.author)
                                            putExtra("bookUrl", bookNav.bookUrl)
                                        }
                                    } else {
                                        openToolInEditor(
                                            ds = ds,
                                            toolName = tool.toolName,
                                            inputJson = tool.input,
                                            conversationId = state.currentConversationId,
                                            characterCards = state.characterCards,
                                            selectedCharacterCards = state.selectedCharacterCards,
                                        )
                                    }
                                }
                            },
                            onRestoreSnapshot = { snapshotId ->
                                viewModel.onIntent(AiChatIntent.RestoreSnapshot(snapshotId))
                            },
                            isChatMode = !isWritingMode,
                            writingSubMode = state.writingSubMode,
                            dialogueHighlightEnabled = state.dialogueHighlightEnabled,
                            roleplayDialogueBubbleEnabled = state.roleplayDialogueBubbleEnabled,
                            showSpeakerName = state.selectedCharacterCards.size > 1,
                            pendingApprovalToolIds = pendingApprovalToolIds,
                            pendingApprovalTiers = pendingApprovalTiers,
                            onBookClick = { book ->
                                context.startActivity<BookInfoComposeActivity> {
                                    putExtra("name", book.name)
                                    putExtra("author", book.author)
                                    putExtra("bookUrl", book.bookUrl)
                                }
                            },
                            onLaunchHtmlApp = { messageId ->
                                viewModel.onIntent(AiChatIntent.LaunchHtmlApp(messageId))
                            },
                            chatMultiBubbleStyle = msg.id in chatMultiBubbleMessageIds,
                            onOpenPlan = { planId ->
                                viewModel.onIntent(AiChatIntent.OpenPlan(planId))
                            },
                            onRejectedPlanFeedback = { planId, feedback ->
                                viewModel.onIntent(AiChatIntent.RejectedPlanFeedback(planId, feedback))
                            },
                        )
                    }
                } // LazyColumn

                    // Scroll-to-bottom FAB (hidden while tool approval overlay is up/animating)
                    if (
                        showScrollToBottom &&
                        displayMessages.isNotEmpty() &&
                        !approvalOverlayActive
                    ) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    browsingHistory = false
                                    scope.launch {
                                        listState.animateScrollToItem(displayMessages.size - 1)
                                    }
                                },
                            shape = CircleShape,
                            color = colorScheme.primaryContainer,
                            shadowElevation = 4.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.ai_scroll_to_bottom),
                                    tint = colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
            } // key

                // Empty state overlay (centered) — welcome is UI-only, never in messages/context.
                // Wait for messagesReady so entering a chat with history does not flash welcome.
                if (state.messagesReady &&
                    displayMessages.isEmpty() &&
                    state.streamingMessage == null
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isWritingMode) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(R.string.ai_chat_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorScheme.onSurfaceVariant,
                                )
                                if (state.selectedCharacterCard == null && state.selectedCharacterCards.isEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    FilledTonalButton(onClick = {
                                        ds.selectedCardIdsForSheet = emptySet()
                                        ds.showCharacterMultiSelect = true
                                    }) {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.ai_select_character))
                                    }
                                }
                            }
                        } else {
                            ChatWelcomePanel(
                                conversationId = state.currentConversationId,
                                onChipFill = { fill ->
                                    inputValue = fill.toTextFieldValueAtEnd()
                                    inputModeSwitchState = WritingInputModeSwitchState()
                                },
                            )
                        }
                    }
                }

                // Tool approval overlay — sits above chat list (not in bottomBar) so
                // translucent/rounded edges reveal messages behind. List viewport is
                // shortened by measured height so messages stay above the panel.
                AiToolApprovalOverlay(
                    items = state.pendingToolConfs,
                    batchFeedback = state.pendingToolBatchFeedback,
                    characterCards = state.characterCards,
                    selectedCharacterCards = state.selectedCharacterCards,
                    dialogState = ds,
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { approvalOverlayHeightPx = it.height }
                        .zIndex(2f),
                )

                // Generation gradient
                if (state.isSending) {
                    val gradientColor = colorScheme.secondaryContainer
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .align(Alignment.BottomCenter),
                    ) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, gradientColor.copy(alpha = 0.3f)),
                            ),
                        )
                    }
                }

                // Tablet split: side data panel in writing mode — only mount when wide so an
                // empty fillMaxSize layer never sits above the LazyColumn.
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                if (isWritingMode && screenWidthDp >= 840) {
                    WritingDataSidePanel(
                        state = state,
                        onOpenMemoryTable = {
                            ds.clearSheetTarget()
                            ds.showMemoryTableSheet = true
                        },
                        onOpenOutline = {
                            ds.clearSheetTarget()
                            ds.showOutlineSheet = true
                        },
                        onOpenCharacter = { ds.showCharacterSheet = true },
                        onOpenWorkspace = {
                            viewModel.onIntent(AiChatIntent.ShowWorkspaceSheet)
                        },
                        onOpenExecutionHistory = {
                            ds.showExecutionHistorySheet = true
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(260.dp)
                            .fillMaxHeight()
                            .padding(8.dp),
                    )
                }

                // Post-edit polishing indicator
                if (state.isPostEditing) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(50),
                        color = colorScheme.secondaryContainer,
                        shadowElevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.onSecondaryContainer,
                            )
                            Text(
                                "润色中…",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    AiChatSheetHost(
        ds = ds, viewModel = viewModel, state = state,
        onConversationDeleted = {
            if (state.conversations.size <= 2) {
                scope.launch { drawerState.close() }
            }
        },
        onSaveOutlineExportFile = {
            outlineExportLauncher.launch("outline_export.json")
        },
        onOpenOutlineImportFile = {
            outlineImportLauncher.launch(arrayOf("application/json", "text/*"))
        },
    )
    // 已结束计划只读详情。
    state.planDetailView?.let { detail ->
        PlanDetailDialog(
            planDetail = detail,
            onDismiss = { viewModel.onIntent(AiChatIntent.DismissPlanDetail) },
        )
    }
    // HTML App 播放器（AI 生成的游戏/HTML 应用）全屏覆盖。
    state.activeHtmlApp?.let { app ->
        HtmlAppPlayerScreen(
            entryUrl = "file://${app.entryPath}",
            conversationId = state.currentConversationId.orEmpty(),
            title = app.title,
            viewModel = viewModel,
            gameUpdateState = state.gameUpdatePayload,
            onExit = { viewModel.onIntent(AiChatIntent.DismissHtmlApp) },
        )
    }
}
}
}

