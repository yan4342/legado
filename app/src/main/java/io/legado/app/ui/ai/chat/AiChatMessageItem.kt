package io.legado.app.ui.ai.chat

import io.legado.app.domain.usecase.ToolTraceBuilder
import io.legado.app.domain.usecase.AiTodoItem
import io.legado.app.domain.usecase.TodoTools
import io.legado.app.domain.usecase.parseTodosJson
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.gson.JsonObject
import kotlinx.collections.immutable.toImmutableList
import java.io.File
import kotlin.text.Charsets
import io.legado.app.R
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.usecase.ai.DialogueNarrationSplitter
import io.legado.app.help.config.AiChatColorConfig
import io.legado.app.lib.theme.LocalAiChatSemanticColors
import io.legado.app.ui.common.compose.BookCoverCompose
import io.legado.app.utils.GSON

internal val defaultSpeakerColors = AiChatColorConfig.defaultSpeakerColors()

internal fun resolveDialogueColor(
    highlightEnabled: Boolean,
    isWritingMode: Boolean,
    isUser: Boolean,
    speakerColorIndex: Int,
    onUserBubble: Color,
    speakerColors: List<Color> = defaultSpeakerColors,
): Color? {
    if (!highlightEnabled || !isWritingMode) return null
    return if (isUser) {
        onUserBubble
    } else {
        speakerColors.getOrElse(speakerColorIndex) { speakerColors[0] }
    }
}

// ---- Message rendering ----

@Composable
internal fun ChatMessageItem(
    msg: AiChatMessageUi,
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    editText: String = "",
    onEditTextChange: (String) -> Unit = {},
    onStartEdit: (() -> Unit)? = null,
    onSaveEdit: (() -> Unit)? = null,
    onCancelEdit: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onSwitchBranch: ((direction: Int) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    isChatMode: Boolean = true,
    writingSubMode: String = "roleplay",
    dialogueHighlightEnabled: Boolean = true,
    roleplayDialogueBubbleEnabled: Boolean = true,
    showSpeakerName: Boolean = true,
    onToolClick: ((AiMessagePart.Tool) -> Unit)? = null,
    onRestoreSnapshot: ((String) -> Unit)? = null,
    onFork: (() -> Unit)? = null,
    conversationId: String? = null,
    pendingApprovalToolIds: Set<String> = emptySet(),
    pendingApprovalTiers: Map<String, String> = emptyMap(),
    onBookClick: ((AiChatBookResultUi) -> Unit)? = null,
    /** 点击 HTML App 卡"运行"：回传 messageId。 */
    onLaunchHtmlApp: ((messageId: String) -> Unit)? = null,
    /** Chat multi-bubble turn: soft surface behind each short bubble; single/long replies stay flat. */
    chatMultiBubbleStyle: Boolean = false,
    /** 点击计划链接卡重开计划（pending 弹审批 / 结束看只读详情）。 */
    onOpenPlan: ((String) -> Unit)? = null,
    onRejectedPlanFeedback: ((String, String) -> Unit)? = null,
) {
    val isUser = msg.role == AiMessageRole.USER
    val colorScheme = MaterialTheme.colorScheme
    val semanticColors = LocalAiChatSemanticColors.current
    val isStreaming = msg.id == "streaming_temp"
    val isWritingMode = !isChatMode
    val isRoleplaySplit = isWritingMode && writingSubMode == "roleplay" && !isUser
    val userBubbleBg = semanticColors.userBubbleBg ?: colorScheme.primaryContainer
    val userBubbleText = semanticColors.userBubbleText ?: colorScheme.onPrimaryContainer
    val awaitingUserApproval = !isUser && pendingApprovalToolIds.isNotEmpty()
    val bodyColor = when {
        isUser -> userBubbleText
        awaitingUserApproval -> colorScheme.onSurfaceVariant
        else -> colorScheme.onSurface
    }
    val assistantBubbleBg = when {
        isUser -> userBubbleBg
        chatMultiBubbleStyle && isChatMode -> colorScheme.surfaceVariant.copy(alpha = 0.72f)
        else -> Color.Transparent
    }
    // Roleplay uses block split instead of inline quote coloring; author keeps inline highlight.
    val dialogueColor = resolveDialogueColor(
        highlightEnabled = dialogueHighlightEnabled && !isRoleplaySplit,
        isWritingMode = isWritingMode,
        isUser = isUser,
        speakerColorIndex = msg.speakerColorIndex,
        onUserBubble = semanticColors.userDialogue ?: colorScheme.tertiary,
        speakerColors = semanticColors.speakerColors,
    )
    val roleplayDialogueColor = if (isRoleplaySplit && dialogueHighlightEnabled) {
        semanticColors.speakerColors.getOrElse(msg.speakerColorIndex) {
            semanticColors.speakerColors[0]
        }
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Speaker label for assistant messages in multi-character mode
        if (!isUser && msg.speakerName.isNotBlank() && showSpeakerName) {
            Text(
                msg.speakerName,
                style = MaterialTheme.typography.labelSmall,
                color = semanticColors.speakerColors.getOrElse(msg.speakerColorIndex) {
                    semanticColors.speakerColors[0]
                },
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
            )
        }
        if (isEditing) {
            Surface(
                modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 1f),
                shape = RoundedCornerShape(16.dp),
                color = if (isUser) userBubbleBg else Color.Transparent,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = onEditTextChange,
                        minLines = 2,
                        maxLines = 24,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onSaveEdit?.invoke() }) {
                            Text(stringResource(R.string.ok))
                        }
                        TextButton(onClick = { onCancelEdit?.invoke() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
        } else if (isChatMode && !isUser && msg.parts.isNotEmpty()) {
            // Thinking / tools outside text bubble; bubble wraps text (+attachments) only.
            RenderChatAssistantParts(
                msg = msg,
                chatMultiBubbleStyle = chatMultiBubbleStyle,
                conversationId = conversationId,
                onToolClick = onToolClick,
                onRestoreSnapshot = onRestoreSnapshot,
                pendingApprovalToolIds = pendingApprovalToolIds,
                pendingApprovalTiers = pendingApprovalTiers,
                provisionalText = awaitingUserApproval,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(
                    when {
                        isUser -> 0.82f
                        chatMultiBubbleStyle && isChatMode -> 0.92f
                        else -> 1f
                    },
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp,
                ),
                color = assistantBubbleBg,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (isUser) {
                        if (msg.excludeFromContext) {
                            Text(
                                text = stringResource(R.string.ai_temp_message_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = bodyColor.copy(alpha = 0.72f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        val attachmentParts = msg.parts.filterIsInstance<AiMessagePart.Attachment>()
                        attachmentParts.forEach { att ->
                            AttachmentPartCard(
                                attachment = att,
                                compact = true,
                                onUserBubble = true,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (msg.content.isNotBlank()) {
                            MarkdownText(
                                markdown = msg.content,
                                color = bodyColor,
                                dialogueColor = dialogueColor,
                            )
                        }
                    } else {
                        // Writing mode: interleaved, but filter out tool parts.
                        val displayParts = msg.parts.filter {
                            it !is AiMessagePart.Tool &&
                                it !is AiMessagePart.PromptInjection &&
                                it !is AiMessagePart.SideEffect
                        }

                        val hasReasoningInParts = displayParts.any { it is AiMessagePart.Reasoning }
                        if (isStreaming && msg.reasoning.orEmpty().isNotBlank() && !hasReasoningInParts) {
                            ThinkingCard(
                                steps = listOf(AiThinkingStep.ReasoningStep(msg.reasoning!!)),
                                durationSeconds = msg.thinkingDuration,
                                isStreaming = true,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        } else {
                            msg.reasoning?.takeIf { it.isNotBlank() && displayParts.isEmpty() }?.let { reasoning ->
                                CollapsibleReasoning(reasoning = reasoning)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        if (displayParts.isNotEmpty()) {
                            RenderMessageParts(
                                msg = msg.copy(parts = displayParts.toImmutableList()),
                                dialogueColor = dialogueColor,
                                provisionalText = awaitingUserApproval,
                                roleplaySplit = isRoleplaySplit,
                                roleplayDialogueColor = roleplayDialogueColor,
                                roleplayDialogueBubble = roleplayDialogueBubbleEnabled,
                            )
                        } else if (msg.content.isNotBlank()) {
                            if (isRoleplaySplit) {
                                RoleplaySegmentedText(
                                    text = msg.content,
                                    bodyColor = bodyColor,
                                    dialogueAccent = roleplayDialogueColor,
                                    dialogueBubble = roleplayDialogueBubbleEnabled,
                                    provisionalText = awaitingUserApproval,
                                )
                            } else {
                                MarkdownText(
                                    markdown = msg.content,
                                    color = bodyColor,
                                    dialogueColor = dialogueColor,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 计划工件卡片附在消息内容之后（reasoning / 工具卡 / 气泡之后），完成计划后追加。
        if (msg.isPlanMessage && !isEditing) {
            Spacer(Modifier.height(6.dp))
            PlanCard(
                planFileId = msg.planFileId,
                status = msg.planStatus,
                revision = msg.planRevision ?: 0,
                onOpen = msg.planFileId?.let { id -> { onOpenPlan?.invoke(id) } },
                onFeedback = msg.planFileId?.let { id -> { fb -> onRejectedPlanFeedback?.invoke(id, fb) } },
            )
        }

        // Actions row (assistant only, not streaming; multi-bubble: callbacks null except last)
        if (!isUser && !isStreaming) {
            val hasBranchNav = msg.totalBranches > 1 && onSwitchBranch != null
            val hasActions = hasBranchNav ||
                onRegenerate != null ||
                onDelete != null ||
                onEdit != null ||
                onFork != null
            if (hasActions) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (hasBranchNav) {
                        IconButton(
                            onClick = { onSwitchBranch(-1) },
                            modifier = Modifier.size(28.dp),
                            enabled = msg.branchIndex > 0,
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            "${msg.branchIndex + 1}/${msg.totalBranches}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        IconButton(
                            onClick = { onSwitchBranch(1) },
                            modifier = Modifier.size(28.dp),
                            enabled = msg.branchIndex < msg.totalBranches - 1,
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    if (onRegenerate != null) {
                        TextButton(onClick = onRegenerate) {
                            Text(
                                stringResource(R.string.ai_regenerate),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("Delete", style = MaterialTheme.typography.labelSmall, color = colorScheme.error)
                        }
                    }

                    if (onEdit != null) {
                        TextButton(onClick = onEdit) {
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (onFork != null) {
                        TextButton(onClick = onFork) {
                            Text("Fork", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // User message actions
        if (isUser && !isStreaming) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (onRegenerate != null) {
                    TextButton(onClick = onRegenerate) {
                        Text(
                            stringResource(R.string.ai_regenerate),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (onEdit != null) {
                    TextButton(onClick = onEdit) {
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onFork != null) {
                    TextButton(onClick = onFork) {
                        Text("Fork", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", style = MaterialTheme.typography.labelSmall, color = colorScheme.error)
                    }
                }
            }
        }

        // Book results
        if (msg.bookResults.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            msg.bookResults.take(3).forEach { book ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .then(
                            if (onBookClick != null) {
                                Modifier.clickable { onBookClick(book) }
                            } else {
                                Modifier
                            },
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookCoverCompose(
                            coverUrl = book.coverPath,
                            name = book.name,
                            author = book.author,
                            sourceOrigin = book.origin,
                            compact = true,
                            modifier = Modifier.size(width = 48.dp, height = 68.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                book.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (book.author.isNotBlank()) {
                                Text(
                                    book.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val chapterLine = book.currentChapterTitle ?: book.latestChapterTitle
                            if (!chapterLine.isNullOrBlank()) {
                                Text(
                                    "$chapterLine",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // HTML App 运行卡片（AI 生成的游戏/可视化）
        if (msg.htmlApps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            msg.htmlApps.forEach { app ->
                AiChatHtmlAppCard(
                    app = app,
                    onRun = { onLaunchHtmlApp?.invoke(msg.id) },
                )
            }
        }
    }
}

/** Render structured [AiMessagePart]s grouped into thinking blocks and content blocks. */
@Composable
private fun RenderMessageParts(
    msg: AiChatMessageUi,
    dialogueColor: Color? = null,
    provisionalText: Boolean = false,
    roleplaySplit: Boolean = false,
    roleplayDialogueColor: Color? = null,
    roleplayDialogueBubble: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val textColor = if (provisionalText) colorScheme.onSurfaceVariant else colorScheme.onSurface
    val blocks = remember(msg.parts) { msg.parts.groupMessageParts() }
    val sources = remember(msg.parts) { extractWebSearchSourceUrls(msg.parts) }

    for ((blockIndex, block) in blocks.withIndex()) {
        when (block) {
            is AiMessagePartBlock.ThinkingBlock -> {
                ThinkingCard(
                    steps = block.steps,
                    durationSeconds = msg.thinkingDuration,
                    isStreaming = msg.id == "streaming_temp",
                )
            }
            is AiMessagePartBlock.ContentBlock -> {
                when (val part = block.part) {
                    is AiMessagePart.Text -> {
                        if (part.text.isNotBlank()) {
                            if (roleplaySplit) {
                                RoleplaySegmentedText(
                                    text = part.text,
                                    bodyColor = textColor,
                                    dialogueAccent = roleplayDialogueColor,
                                    dialogueBubble = roleplayDialogueBubble,
                                    provisionalText = provisionalText,
                                )
                            } else {
                                MarkdownText(
                                    markdown = part.text,
                                    color = textColor,
                                    dialogueColor = dialogueColor,
                                    sources = sources,
                                )
                            }
                        }
                    }
                    is AiMessagePart.Attachment -> {
                        AttachmentPartCard(part)
                    }
                    is AiMessagePart.Image -> {
                        ImagePartCard(part)
                    }
                    else -> { /* other parts handled elsewhere */ }
                }
            }
        }
        if (blockIndex < blocks.size - 1) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Roleplay UI: dialogue optionally in accent bubbles; narration is flat text.
 * Pure narration (no quotes) stays a single unsplit block.
 */
@Composable
private fun RoleplaySegmentedText(
    text: String,
    bodyColor: Color,
    dialogueAccent: Color?,
    dialogueBubble: Boolean = true,
    provisionalText: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val segments = remember(text) { DialogueNarrationSplitter.split(text) }
    val hasDialogue = segments.any { it is DialogueNarrationSplitter.Segment.Dialogue }
    if (!hasDialogue) {
        MarkdownText(markdown = text, color = bodyColor, dialogueColor = null)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (segment in segments) {
            when (segment) {
                is DialogueNarrationSplitter.Segment.Narration -> {
                    MarkdownText(
                        markdown = segment.text.trim(),
                        color = if (provisionalText) {
                            colorScheme.onSurfaceVariant
                        } else {
                            bodyColor
                        },
                        dialogueColor = null,
                    )
                }
                is DialogueNarrationSplitter.Segment.Dialogue -> {
                    // Null accent = highlight off → same as body text (no primary fallback).
                    val dialogueTextColor = when {
                        provisionalText -> colorScheme.onSurfaceVariant
                        dialogueAccent != null -> dialogueAccent
                        else -> bodyColor
                    }
                    if (dialogueBubble) {
                        val bubbleFill = when {
                            dialogueAccent != null ->
                                dialogueAccent.copy(alpha = if (provisionalText) 0.10f else 0.16f)
                            else ->
                                colorScheme.surfaceVariant.copy(
                                    alpha = if (provisionalText) 0.35f else 0.55f,
                                )
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = bubbleFill,
                        ) {
                            MarkdownText(
                                markdown = segment.text.trim(),
                                color = dialogueTextColor,
                                dialogueColor = null,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        MarkdownText(
                            markdown = segment.text.trim(),
                            color = dialogueTextColor,
                            dialogueColor = null,
                        )
                    }
                }
            }
        }
    }
}

// ---- Chat mode: reasoning/tools outside bubble; text inside ----

/**
 * Preserves part order. Reasoning (and tools) render outside the text surface;
 * consecutive Text/Attachment chunks share one bubble when [chatMultiBubbleStyle].
 */
@Composable
private fun RenderChatAssistantParts(
    msg: AiChatMessageUi,
    chatMultiBubbleStyle: Boolean,
    conversationId: String? = null,
    onToolClick: ((AiMessagePart.Tool) -> Unit)? = null,
    onRestoreSnapshot: ((String) -> Unit)? = null,
    pendingApprovalToolIds: Set<String> = emptySet(),
    pendingApprovalTiers: Map<String, String> = emptyMap(),
    provisionalText: Boolean = false,
) {
    val isStreaming = msg.id == "streaming_temp"
    val colorScheme = MaterialTheme.colorScheme
    val textColor = if (provisionalText) {
        colorScheme.onSurfaceVariant
    } else {
        colorScheme.onSurface
    }
    val bubbleBg = if (chatMultiBubbleStyle) {
        colorScheme.surfaceVariant.copy(alpha = 0.72f)
    } else {
        Color.Transparent
    }
    val parts = msg.parts
    // 原生联网搜索计数：同一回复多次搜索时，在工具行标题标注「第 i/N 次」。
    val nativeSearchTotal = parts.count {
        it is AiMessagePart.Tool && it.rawType == "web_search_call"
    }
    var nativeSearchIndex = 0
    var index = 0
    while (index < parts.size) {
        when (val part = parts[index]) {
            is AiMessagePart.Reasoning -> {
                // Stable key by position, not content hash – avoids destroying/recreating
                // the card on every streaming token, which caused sibling text chunks to flash.
                key("reasoning-$index") {
                    ReasoningPartCard(
                        text = part.text,
                        isStreaming = isStreaming,
                        durationSeconds = msg.thinkingDuration,
                    )
                }
                Spacer(Modifier.height(6.dp))
                index++
            }
            is AiMessagePart.Tool -> {
                val toolKey = part.toolCallId.ifBlank { "tool-$index" }
                key(toolKey) {
                    val isInFlight = isStreaming && (
                        part.output.isBlank() || part.output == ToolTraceBuilder.EXECUTING_PLACEHOLDER
                        )
                    val contentSearchNav =
                        resolveToolContentSearchNav(part.toolName, part.input, part.output)
                    val bookNav = resolveToolBookNav(part.toolName, part.input, part.output)
                    val canOpen = !isInFlight && onToolClick != null && (
                        canOpenToolInEditor(part.toolName, part.input, conversationId) ||
                            bookNav != null ||
                            contentSearchNav != null
                        )
                    val searchRoundOf = if (part.rawType == "web_search_call" && nativeSearchTotal > 1) {
                        ++nativeSearchIndex
                    } else {
                        0
                    }
                    ToolPartCard(
                        tool = part,
                        isStreaming = isStreaming,
                        awaitingApproval = part.toolCallId in pendingApprovalToolIds,
                        approvalTier = pendingApprovalTiers[part.toolCallId],
                        searchRoundOf = searchRoundOf,
                        searchRoundTotal = nativeSearchTotal,
                        onClick = if (canOpen) {
                            { onToolClick(part) }
                        } else null,
                        onUndo = onRestoreSnapshot,
                    )
                }
                Spacer(Modifier.height(6.dp))
                index++
            }
            is AiMessagePart.Text, is AiMessagePart.Attachment -> {
                val chunk = mutableListOf<AiMessagePart>()
                val chunkStart = index
                while (index < parts.size) {
                    val next = parts[index]
                    if (next is AiMessagePart.Text || next is AiMessagePart.Attachment) {
                        chunk.add(next)
                        index++
                    } else {
                        break
                    }
                }
                val hasVisible = chunk.any {
                    when (it) {
                        is AiMessagePart.Text -> it.text.isNotBlank()
                        is AiMessagePart.Attachment -> true
                        else -> false
                    }
                }
                if (hasVisible) {
                    // Keep the node stable while streaming text grows; content-derived keys
                    // recreate the subtree and make sibling chunks flash.
                    val textChunkKey = "text-chunk-$chunkStart"
                    key(textChunkKey) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(if (chatMultiBubbleStyle) 0.92f else 1f),
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 16.dp,
                            ),
                            color = bubbleBg,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                chunk.forEachIndexed { i, p ->
                                    when (p) {
                                        is AiMessagePart.Text -> {
                                            if (p.text.isNotBlank()) {
                                                MarkdownText(
                                                    markdown = p.text,
                                                    color = textColor,
                                                )
                                            }
                                        }
                                        is AiMessagePart.Attachment -> AttachmentPartCard(p)
                                        else -> Unit
                                    }
                                    if (i < chunk.lastIndex) Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            is AiMessagePart.PromptInjection, is AiMessagePart.SideEffect -> index++
            else -> index++
        }
    }
}


@Composable
private fun AttachmentPartCard(
    attachment: AiMessagePart.Attachment,
    compact: Boolean = false,
    onUserBubble: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val kindLabel = when (attachment.kind) {
        io.legado.app.domain.model.AiAttachmentKind.IMAGE ->
            stringResource(R.string.ai_attachment_kind_image)
        io.legado.app.domain.model.AiAttachmentKind.DOCUMENT ->
            stringResource(R.string.ai_attachment_kind_document)
        io.legado.app.domain.model.AiAttachmentKind.TEXT_EXTRACT ->
            stringResource(R.string.ai_attachment_kind_text)
        else -> attachment.mimeType
    }
    val sizeLabel = formatAttachmentSize(attachment.sizeBytes)
    val detailLine = when (attachment.kind) {
        io.legado.app.domain.model.AiAttachmentKind.TEXT_EXTRACT -> {
            val extracted = io.legado.app.help.ai.AiChatAttachmentStore
                .readExtractedText(attachment.extractedTextPath)
            val chars = extracted?.length ?: 0
            buildString {
                append(stringResource(R.string.ai_attachment_extracted_chars, chars))
                if (attachment.truncated) {
                    append(" · ")
                    append(stringResource(R.string.ai_attachment_truncated))
                }
            }
        }
        else -> listOfNotNull(
            kindLabel.takeIf { it.isNotBlank() },
            sizeLabel.takeIf { it.isNotBlank() },
            attachment.mimeType.takeIf { it.isNotBlank() && attachment.kind == io.legado.app.domain.model.AiAttachmentKind.IMAGE },
        ).joinToString(" · ")
    }
    val bg = if (onUserBubble) {
        colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val titleColor = if (onUserBubble) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val subColor = if (onUserBubble) {
        colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
    } else {
        colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(if (compact) 10.dp else 12.dp)) {
            if (attachment.kind == io.legado.app.domain.model.AiAttachmentKind.IMAGE) {
                val bitmap = remember(attachment.localPath) {
                    runCatching {
                        android.graphics.BitmapFactory.decodeFile(attachment.localPath)
                    }.getOrNull()
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = attachment.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compact) 120.dp else 160.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (attachment.kind) {
                        io.legado.app.domain.model.AiAttachmentKind.IMAGE ->
                            Icons.Default.Image
                        io.legado.app.domain.model.AiAttachmentKind.DOCUMENT ->
                            Icons.Default.PictureAsPdf
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = subColor,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        attachment.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detailLine.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            detailLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = subColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun formatAttachmentSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> String.format("%.0f KB", kb.coerceAtLeast(1.0))
        else -> String.format("%.1f MB", kb / 1024.0)
    }
}

/** AI-generated or downloaded image. SVGs are rendered via androidsvg; raster via Coil. */
@Composable
private fun ImagePartCard(image: AiMessagePart.Image) {
    val context = LocalContext.current
    val isSvg = image.mimeType.contains("svg", ignoreCase = true)

    if (isSvg) {
        val bitmap = remember(image.localPath) {
            runCatching {
                val file = File(image.localPath)
                if (!file.isFile) return@runCatching null
                val svgSource = file.readText(Charsets.UTF_8)
                val svg = com.caverock.androidsvg.SVG.getFromString(svgSource)
                    ?: return@runCatching null
                val density = context.resources.displayMetrics.density
                val targetW = (320f * density).toInt()
                val targetH = (320f * density).toInt()
                val docW = svg.documentWidth.takeIf { it > 0f } ?: targetW.toFloat()
                val docH = svg.documentHeight.takeIf { it > 0f } ?: targetH.toFloat()
                val scale = minOf(targetW / docW, targetH / docH)
                val w = (docW * scale).toInt().coerceAtLeast(1)
                val h = (docH * scale).toInt().coerceAtLeast(1)
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.scale(scale, scale)
                svg.renderToCanvas(canvas)
                bmp
            }.getOrNull()
        }
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(image.localPath))
                .crossfade(true)
                .build(),
            contentDescription = "图片",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ReasoningPartCard(text: String, isStreaming: Boolean, durationSeconds: Int = 0) {
    val colorScheme = MaterialTheme.colorScheme
    // Collapsed by design even while streaming; header still shows Thinking… / spinner.
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.secondary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isStreaming) "Thinking…" else "Thinking",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (durationSeconds > 0) {
                    Text(
                        "${durationSeconds}s",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )            
                }
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.secondary,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolPartCard(
    tool: AiMessagePart.Tool,
    isStreaming: Boolean,
    awaitingApproval: Boolean = false,
    approvalTier: String? = null,
    searchRoundOf: Int = 0,
    searchRoundTotal: Int = 0,
    onClick: (() -> Unit)? = null,
    onUndo: ((String) -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isTodosTool = tool.toolName == AiToolRepository.TOOL_UPDATE_TODOS
    // 任务清单从 input args（模型本次调用传入的完整 todos 数组）解析，流式早期即可渲染。
    val todosItems = if (isTodosTool) {
        remember(tool.input) { parseUpdateTodosInput(tool.input) }
    } else {
        emptyList()
    }
    // 任务清单气泡默认展开（其他工具默认收起）。
    var expanded by rememberSaveable { mutableStateOf(isTodosTool) }

    val isAwaitingApproval = awaitingApproval && tool.output.isBlank()
    val isRunning = isStreaming && tool.output.isBlank() && !isAwaitingApproval
    val isExecuting = isStreaming && tool.output == ToolTraceBuilder.EXECUTING_PLACEHOLDER
    val toolProgress = remember(tool.metadata) {
        io.legado.app.domain.model.AiToolProgress.fromMetadata(tool.metadata)
    }
    val hasDeterminateProgress = (isRunning || isExecuting) && toolProgress != null
    val isTerminalFailure = !isStreaming && (
        tool.output in TERMINAL_TOOL_OUTPUTS || toolOutputLooksRejectedOrSkipped(tool.output)
    )
    val hasResult = !isStreaming && !isExecuting && !isTerminalFailure
    val canNavigate = onClick != null && !isTerminalFailure
    val snapshotId = remember(tool.output) { parseToolSnapshotId(tool.output) }
    val operationPreview = remember(tool.toolName, tool.input) {
        io.legado.app.data.repository.AiToolRepository.toolOperationPreview(tool.toolName, tool.input)
    }

    val bubbleState = when {
        isAwaitingApproval -> ToolBubbleVisualState.AwaitingApproval
        isExecuting -> ToolBubbleVisualState.Executing
        isRunning -> ToolBubbleVisualState.Running
        isTerminalFailure -> ToolBubbleVisualState.TerminalFailure
        hasResult && toolOutputLooksLikeError(tool.output) -> ToolBubbleVisualState.TerminalFailure
        hasResult -> ToolBubbleVisualState.Done
        else -> ToolBubbleVisualState.Running
    }
    val bubbleColors = toolBubbleColors(tool.toolName, approvalTier, bubbleState)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bubbleColors.panelContainer,
    ) {
        Column {
            // Header — always toggles expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = bubbleColors.iconTint,
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val badges = remember(tool.toolName, approvalTier) {
                        resolveToolApprovalBadges(tool.toolName, approvalTier)
                    }
                    if (badges.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            badges.forEach { badge ->
                                ToolApprovalBadge(badge)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    val searchRoundLabel = if (searchRoundTotal > 1 && searchRoundOf > 0) {
                        stringResource(R.string.ai_native_search_round, searchRoundOf, searchRoundTotal)
                    } else {
                        ""
                    }
                    Text(
                        text = if (isTodosTool) {
                            buildString {
                                append("📋 任务清单")
                                if (todosItems.isNotEmpty()) {
                                    append(" · ")
                                    append(todosItems.count { it.status == TodoTools.STATUS_COMPLETED })
                                    append("/${todosItems.size} 完成")
                                }
                            }
                        } else {
                            buildString {
                                append(toolDisplayName(tool.toolName))
                                if (operationPreview.isNotBlank()) {
                                    append(" · ")
                                    append(operationPreview)
                                }
                                if (searchRoundLabel.isNotBlank()) {
                                    append(" · ")
                                    append(searchRoundLabel)
                                }
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isExecuting || isRunning) {
                            bubbleColors.itemOnContainer
                        } else {
                            bubbleColors.accent
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                when {
                    isAwaitingApproval -> {
                        Text(
                            stringResource(R.string.ai_tool_awaiting_approval),
                            style = MaterialTheme.typography.labelSmall,
                            color = bubbleColors.statusTint,
                        )
                        // Stay collapsed: expanding mid-stream covers interleaved body text.
                        // Approval details live in the bottom overlay.
                    }
                    isRunning -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = bubbleColors.statusTint,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Running…",
                            style = MaterialTheme.typography.labelSmall,
                            color = bubbleColors.statusTint,
                        )
                        // Stay collapsed while streaming so tool args don't hide body text.
                    }
                    isExecuting -> {
                        if (hasDeterminateProgress) {
                            Text(
                                "${(toolProgress!!.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = bubbleColors.statusTint,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = bubbleColors.statusTint,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Executing…",
                                style = MaterialTheme.typography.labelSmall,
                                color = bubbleColors.statusTint,
                            )
                        }
                    }
                    isTerminalFailure || bubbleState == ToolBubbleVisualState.TerminalFailure -> {
                        Text(
                            terminalToolLabel(tool.output),
                            style = MaterialTheme.typography.labelSmall,
                            color = bubbleColors.statusTint,
                        )
                    }
                    canNavigate -> {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = bubbleColors.iconTint,
                        )
                    }
                    hasResult -> {
                        Text(
                            "Done",
                            style = MaterialTheme.typography.labelSmall,
                            color = bubbleColors.statusTint,
                        )
                    }
                }
            }

            // Progress bar for running/executing state
            if (isRunning || isExecuting) {
                if (hasDeterminateProgress) {
                    val progress = toolProgress!!
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 2.dp),
                        color = bubbleColors.accent,
                        trackColor = bubbleColors.panelContainer,
                    )
                    if (progress.label.isNotBlank()) {
                        Text(
                            progress.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 2.dp),
                        color = bubbleColors.accent,
                        trackColor = bubbleColors.panelContainer,
                    )
                }
                if (tool.input.isNotBlank() && tool.input != "{}") {
                    if (operationPreview.isBlank()) {
                        Text(
                            tool.input.take(300),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                        )
                    }
                }
            }

            // Expandable content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (canNavigate) Modifier.clickable { onClick() }
                            else Modifier
                        )
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                ) {

                    if (isTodosTool) {
                        // 任务清单专用渲染：直接展示清单，不显示 Args/原始 output。
                        UpdateTodosChecklist(
                            items = todosItems,
                            running = isRunning || isExecuting,
                            failure = isTerminalFailure || (hasResult && toolOutputLooksLikeError(tool.output)),
                        )
                    } else if (tool.input.isNotBlank() && tool.input != "{}") {
                        Text(
                            "Args: ${tool.input.take(500)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isRunning || isExecuting) {
                                colorScheme.onSurface
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (!isTodosTool && (hasResult || (isTerminalFailure && tool.output.isNotBlank()))) {
                        if (tool.toolName == AiToolRepository.TOOL_WEB_SEARCH) {
                            val results = remember(tool.output) { parseWebSearchResults(tool.output) }
                            if (results.isNotEmpty()) {
                                WebSearchSourcesList(results)
                            } else {
                                val query = remember(tool.input) { parseWebSearchQuery(tool.input) }
                                if (query != null) {
                                    Text(
                                        stringResource(R.string.ai_web_search_preview, query),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface,
                                    )
                                } else {
                                    Text(
                                        tool.output.take(1200),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface,
                                        maxLines = 15,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        } else {
                            Text(
                                tool.output.take(1200),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface,
                                maxLines = 15,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!snapshotId.isNullOrBlank() && onUndo != null) {
                        Spacer(Modifier.height(4.dp))
                        IconButton(
                            onClick = { onUndo(snapshotId) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.ai_memory_undo_confirm),
                                modifier = Modifier.size(18.dp),
                                tint = colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseToolSnapshotId(output: String): String? = runCatching {
    com.google.gson.JsonParser.parseString(output).asJsonObject.get("snapshotId")?.asString
}.getOrNull()?.takeIf { it.isNotBlank() }

/** 从 update_todos 的 input args（含完整 todos 数组）解析任务列表。 */
private fun parseUpdateTodosInput(input: String): List<AiTodoItem> {
    val todosJson = runCatching {
        com.google.gson.JsonParser.parseString(input).asJsonObject.get("todos")?.toString()
    }.getOrNull()
    return parseTodosJson(todosJson.orEmpty())
}

/** 任务清单专用气泡内容：逐项渲染状态标记，不展示 Args/原始 output。 */
@Composable
private fun UpdateTodosChecklist(
    items: List<AiTodoItem>,
    running: Boolean,
    failure: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            failure -> {
                Text(
                    "清单更新失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.error,
                )
            }
            items.isEmpty() -> {
                Text(
                    if (running) "更新中…" else "任务清单已清空",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            when (item.status) {
                                TodoTools.STATUS_COMPLETED -> "☑"
                                TodoTools.STATUS_IN_PROGRESS -> "▶"
                                else -> "☐"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.status == TodoTools.STATUS_COMPLETED) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = item.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.status == TodoTools.STATUS_COMPLETED) {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                colorScheme.onSurface
                            },
                            textDecoration = if (item.status == TodoTools.STATUS_COMPLETED) {
                                TextDecoration.LineThrough
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---- Web search sources ----

private data class WebSearchResultItem(
    val title: String,
    val url: String,
    val snippet: String,
    val publishedAt: String = "",
)

/** Parse the web_search tool output JSON into ordered source items (url, title, snippet, date). */
private fun parseWebSearchResults(output: String): List<WebSearchResultItem> = runCatching {
    val obj = GSON.fromJson(output, JsonObject::class.java)
    obj.getAsJsonArray("results")?.mapNotNull { el ->
        val r = el.asJsonObject
        WebSearchResultItem(
            title = r.get("title")?.asString.orEmpty(),
            url = r.get("url")?.asString.orEmpty(),
            snippet = r.get("snippet")?.asString.orEmpty(),
            publishedAt = r.get("publishedAt")
                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
        ).takeIf { it.url.isNotBlank() }
    } ?: emptyList()
}.getOrDefault(emptyList())

/** Read the search query from a native web_search tool's input JSON. */
private fun parseWebSearchQuery(input: String): String? = runCatching {
    GSON.fromJson(input, JsonObject::class.java).get("query")?.asString
}.getOrNull()?.takeIf { it.isNotBlank() }

/** Ordered source URLs from all web_search tool parts in a message (for [N] citation badges). */
private fun extractWebSearchSourceUrls(parts: List<AiMessagePart>): List<String> =
    parts.filterIsInstance<AiMessagePart.Tool>()
        .filter { it.toolName == AiToolRepository.TOOL_WEB_SEARCH }
        .flatMap { tool -> parseWebSearchResults(tool.output).map { it.url } }

/** Numbered, clickable source links inside the expanded web_search tool bubble. */
@Composable
private fun WebSearchSourcesList(results: List<WebSearchResultItem>, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val colorScheme = MaterialTheme.colorScheme
    val visible = results.take(WEB_SEARCH_SOURCES_MAX)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visible.forEachIndexed { idx, item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${idx + 1}.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title.ifBlank { item.url },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { uriHandler.openUri(item.url) },
                    )
                    if (item.publishedAt.isNotBlank()) {
                        Text(
                            item.publishedAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.snippet.isNotBlank()) {
                        Text(
                            item.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (results.size > visible.size) {
            Text(
                stringResource(R.string.ai_web_search_sources_truncated, visible.size),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Cap on source rows rendered inside one web_search bubble; the rest collapse into a notice. */
private const val WEB_SEARCH_SOURCES_MAX = 8

private val TERMINAL_TOOL_OUTPUTS = setOf(
    ToolTraceBuilder.EXECUTING_PLACEHOLDER,
    ToolTraceBuilder.RESULT_CANCELLED,
    ToolTraceBuilder.RESULT_NOT_COMPLETED,
    ToolTraceBuilder.RESULT_REJECTED,
)

private fun toolOutputLooksRejectedOrSkipped(output: String): Boolean {
    if (output.isBlank()) return false
    return runCatching {
        val status = com.google.gson.JsonParser.parseString(output).asJsonObject
            .get("status")?.takeIf { !it.isJsonNull }?.asString
        status == "rejected" || status == "skipped"
    }.getOrDefault(false)
}

private fun toolOutputLooksLikeError(output: String): Boolean {
    if (output.isBlank() || output in TERMINAL_TOOL_OUTPUTS) return false
    if (toolOutputLooksRejectedOrSkipped(output)) return false
    return runCatching {
        val obj = com.google.gson.JsonParser.parseString(output).asJsonObject
        obj.has("error") && !obj.get("error").isJsonNull
    }.getOrDefault(false)
}

private fun terminalToolLabel(output: String): String = when {
    output.isBlank() -> "未完成"
    output == ToolTraceBuilder.RESULT_CANCELLED -> "已取消"
    output == ToolTraceBuilder.RESULT_NOT_COMPLETED -> "未完成"
    output == ToolTraceBuilder.RESULT_REJECTED -> "已拒绝"
    output == ToolTraceBuilder.EXECUTING_PLACEHOLDER -> "已中断"
    toolOutputLooksRejectedOrSkipped(output) -> {
        val status = runCatching {
            com.google.gson.JsonParser.parseString(output).asJsonObject
                .get("status")?.asString
        }.getOrNull()
        if (status == "skipped") "已跳过" else "已拒绝"
    }
    toolOutputLooksLikeError(output) -> "失败"
    else -> "未完成"
}

private fun toolDisplayName(name: String): String =
    io.legado.app.ui.config.ai.resolvedToolDisplayName(name)

// ---- Thinking card ----

@Composable
private fun ThinkingCard(
    steps: List<AiThinkingStep>,
    durationSeconds: Int,
    isStreaming: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    // Collapsed by design even while streaming; header still shows progress.
    var expanded by rememberSaveable { mutableStateOf(false) }

    val reasoningSteps = steps.filterIsInstance<AiThinkingStep.ReasoningStep>()
    val toolSteps = steps.filterIsInstance<AiThinkingStep.ToolStep>()
    val title = when {
        toolSteps.isNotEmpty() && reasoningSteps.isNotEmpty() -> "Thought & Tools"
        toolSteps.isNotEmpty() -> "Tools"
        else -> "Thinking"
    }
    val icon = if (toolSteps.isNotEmpty()) Icons.Default.Delete else Icons.Default.Lightbulb

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (durationSeconds > 0) {
                    Text(
                        "${durationSeconds}s",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    reasoningSteps.forEach { step ->
                        if (step.text.isNotBlank()) {
                            Text(
                                step.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }

                    toolSteps.forEach { step ->
                        val tool = step.tool
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.surface,
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "Tool: ${tool.toolName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.tertiary,
                                )
                                when {
                                    isStreaming && tool.output.isBlank() -> {
                                        Text(
                                            "Running…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    isStreaming && tool.output == ToolTraceBuilder.EXECUTING_PLACEHOLDER -> {
                                        Text(
                                            "Executing…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.tertiary,
                                        )
                                    }
                                    !isStreaming && (tool.output.isBlank() || tool.output in TERMINAL_TOOL_OUTPUTS) -> {
                                        Text(
                                            if (tool.output.isBlank()) "Done" else terminalToolLabel(tool.output),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (tool.output.isBlank()) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    tool.output.isNotBlank() -> {
                                        Text(
                                            tool.output.take(1000),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurface,
                                            maxLines = 12,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Collapsible legacy reasoning ----

@Composable
private fun CollapsibleReasoning(reasoning: String) {
    val colorScheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Text(
        text = if (expanded) reasoning else reasoning.take(300) + if (reasoning.length > 300) "…" else "",
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
    )
    if (reasoning.length > 300) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                if (expanded) "Show less" else "Show more",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
