package io.legado.app.ui.ai.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.common.compose.rememberSwipeToConfirmDismiss
import io.legado.app.utils.toastOnUi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.AiUsageRecord
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.data.repository.AiUsageRepository
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.promptInjection
import io.legado.app.domain.model.sideEffectParts
import io.legado.app.domain.model.toolParts
import io.legado.app.domain.usecase.ToolTraceBuilder
import io.legado.app.ui.book.readRecord.aiSourceDisplayName
import io.legado.app.ui.common.compose.legadoCardBackgroundColor
import io.legado.app.ui.common.compose.legadoSheetInsets
import io.legado.app.ui.config.ai.AiAbilityManagementViewModel
import io.legado.app.ui.config.ai.toolDisplayNameRes
import io.legado.app.utils.sendToClip
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val PREVIEW_INPUT_CHARS = 800
private const val PREVIEW_OUTPUT_CHARS = 1200
/** Only scan the newest N loaded messages when building the activity log. */
private const val MAX_MESSAGES_SCANNED = 48
/** Hard cap on list rows shown in the sheet. */
private const val MAX_HISTORY_ITEMS = 80
private val TERMINAL_OUTPUTS = setOf(
    ToolTraceBuilder.EXECUTING_PLACEHOLDER,
    ToolTraceBuilder.RESULT_CANCELLED,
    ToolTraceBuilder.RESULT_NOT_COMPLETED,
    ToolTraceBuilder.RESULT_REJECTED,
)

private val PRETTY_GSON: com.google.gson.Gson =
    com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

/**
 * Pretty-print JSON for readable tool args/results previews.
 * Non-JSON text (error strings, plain output) passes through unchanged.
 */
private fun prettyJson(text: String): String {
    if (text.isBlank()) return text
    return runCatching {
        val element = com.google.gson.JsonParser.parseString(text)
        if (element.isJsonNull || element.isJsonPrimitive) text else PRETTY_GSON.toJson(element)
    }.getOrDefault(text)
}

/** Usage sources that are side effects (not the main CHAT turn). */
val EXECUTION_SIDE_EFFECT_SOURCES: Set<String> = setOf(
    AiCallSource.POST_EDIT,
    AiCallSource.STRUCTURED_MAINTAIN,
    AiCallSource.SUGGESTION,
    AiCallSource.GALGAME,
    AiCallSource.HELP_REPLY,
    AiCallSource.TOOL_SUBMODEL,
    AiCallSource.OUTLINE,
    AiCallSource.MEMORY,
    AiCallSource.CHARACTER,
    AiCallSource.WORLDBOOK,
    AiCallSource.COMPRESS,
    AiCallSource.TITLE,
)

/**
 * Fingerprint for streaming updates that ignores pure text/reasoning deltas.
 * Keeps the activity log from rebuilding on every token while the sheet is open.
 */
fun streamingActivityLogKey(message: AiChatMessageUi?): String {
    if (message == null) return ""
    val tools = message.parts.toolParts().joinToString("|") { tool ->
        "${tool.toolCallId}:${tool.toolName}:${tool.output.length}:${tool.approvalState}"
    }
    val inj = message.parts.promptInjection()?.let { inj ->
        "${inj.blocks.size}:${inj.totalEstimatedTokens}"
    }.orEmpty()
    return "${message.id}|$tools|$inj"
}

/** Cheap fingerprint so we rebuild when messages/tools change, not on unrelated UI churn. */
fun messagesActivityLogKey(messages: List<AiChatMessageUi>): String {
    if (messages.isEmpty()) return "0"
    return buildString {
        append(messages.size)
        messages.takeLast(8).forEach { msg ->
            append('|')
            append(msg.id)
            append(':')
            append(msg.parts.size)
            val tools = msg.parts.toolParts()
            if (tools.isNotEmpty()) {
                append('t')
                append(tools.size)
                append(tools.sumOf { it.output.length })
            }
            msg.parts.promptInjection()?.let { append('i').append(it.blocks.size) }
        }
    }
}

/** Build newest-first activity log from messages, optional streaming, and usage side effects. */
fun buildExecutionHistory(
    messages: List<AiChatMessageUi>,
    streamingMessage: AiChatMessageUi? = null,
    sideEffects: List<AiUsageRecord> = emptyList(),
    maxMessagesScanned: Int = MAX_MESSAGES_SCANNED,
    maxItems: Int = MAX_HISTORY_ITEMS,
): ImmutableList<AiExecutionHistoryItemUi> {
    val items = mutableListOf<AiExecutionHistoryItemUi>()
    val seenTools = mutableSetOf<String>()
    val scanned = if (messages.size > maxMessagesScanned) {
        messages.takeLast(maxMessagesScanned)
    } else {
        messages
    }

    fun consume(message: AiChatMessageUi, isStreaming: Boolean) {
        message.parts.promptInjection()?.let { injection ->
            if (injection.blocks.isNotEmpty()) {
                items += injection.toHistoryItem(message.id, message.createdAt)
            }
        }
        message.parts.sideEffectParts().forEachIndexed { index, effect ->
            if (effect.source.isBlank()) return@forEachIndexed
            items += effect.toHistoryItem(
                id = "side_${message.id}_$index",
                messageId = message.id,
                timestamp = message.createdAt,
            )
        }
        message.parts.toolParts().forEachIndexed { index, tool ->
            if (tool.toolName.isBlank()) return@forEachIndexed
            val key = tool.toolCallId.ifBlank { "${message.id}_$index" }
            if (!seenTools.add(key)) return@forEachIndexed
            items += tool.toHistoryItem(
                id = key,
                messageId = message.id,
                timestamp = message.createdAt,
                isStreaming = isStreaming,
            )
        }
    }

    streamingMessage?.let { consume(it, isStreaming = true) }
    scanned.asReversed().forEach { consume(it, isStreaming = false) }

    val messagePostEditTimes = items
        .filter { it.kind == AiExecutionKind.SIDE_EFFECT && it.source == AiCallSource.POST_EDIT }
        .map { it.timestamp }
        .toSet()

    sideEffects.forEach { record ->
        if (record.source !in EXECUTION_SIDE_EFFECT_SOURCES) return@forEach
        // Prefer message-backed post-edit detail over usage-only duplicate.
        if (record.source == AiCallSource.POST_EDIT &&
            messagePostEditTimes.any { kotlin.math.abs(it - record.timestamp) < 120_000L }
        ) {
            return@forEach
        }
        items += record.toHistoryItem()
    }

    return groupExecutionHistoryByTurn(items)
        .take(maxItems)
        .toImmutableList()
}

/** Keep same-message items contiguous (newest turn first) so UI can draw turn separators. */
internal fun groupExecutionHistoryByTurn(
    items: List<AiExecutionHistoryItemUi>,
): List<AiExecutionHistoryItemUi> {
    if (items.isEmpty()) return emptyList()
    fun turnKey(item: AiExecutionHistoryItemUi): String =
        item.messageId.ifBlank { "solo_${item.id}" }
    val kindOrder = mapOf(
        AiExecutionKind.PROMPT_INJECTION to 0,
        AiExecutionKind.TOOL to 1,
        AiExecutionKind.SIDE_EFFECT to 2,
    )
    return items
        .groupBy(::turnKey)
        .entries
        .sortedByDescending { (_, group) -> group.maxOf { it.timestamp } }
        .flatMap { (_, group) ->
            group.sortedWith(
                compareBy<AiExecutionHistoryItemUi> { kindOrder[it.kind] ?: 9 }
                    .thenByDescending { it.timestamp }
                    .thenBy { it.id },
            )
        }
}

private fun AiMessagePart.Tool.toHistoryItem(
    id: String,
    messageId: String,
    timestamp: Long,
    isStreaming: Boolean,
): AiExecutionHistoryItemUi {
    val snapshotId = parseSnapshotId(output)
    return AiExecutionHistoryItemUi(
        id = id,
        kind = AiExecutionKind.TOOL,
        toolCallId = toolCallId,
        messageId = messageId,
        toolName = toolName,
        operationPreview = AiToolRepository.toolOperationPreview(toolName, input),
        status = resolveStatus(output, isStreaming),
        timestamp = timestamp,
        input = input.take(PREVIEW_INPUT_CHARS),
        output = output.take(PREVIEW_OUTPUT_CHARS),
        snapshotId = snapshotId,
    )
}

private fun AiMessagePart.SideEffect.toHistoryItem(
    id: String,
    messageId: String,
    timestamp: Long,
): AiExecutionHistoryItemUi {
    return AiExecutionHistoryItemUi(
        id = id,
        kind = AiExecutionKind.SIDE_EFFECT,
        messageId = messageId,
        source = source,
        operationPreview = modelName,
        status = if (success) AiExecutionStatus.DONE else AiExecutionStatus.FAILED,
        timestamp = timestamp,
        input = input,
        output = output,
        totalTokens = totalTokens,
        generatedChars = generatedChars,
        modelName = modelName,
    )
}

private fun AiMessagePart.PromptInjection.toHistoryItem(
    messageId: String,
    timestamp: Long,
): AiExecutionHistoryItemUi {
    val blockUis = blocks.map {
        AiExecutionInjectionBlockUi(
            blockId = it.blockId,
            estimatedTokens = it.estimatedTokens,
            truncated = it.truncated,
            position = it.position,
            preview = it.preview,
            content = it.content.ifBlank { it.preview },
        )
    }.toImmutableList()
    val subtitle = blocks.joinToString(", ") { it.blockId }.take(120)
    return AiExecutionHistoryItemUi(
        id = "inject_$messageId",
        kind = AiExecutionKind.PROMPT_INJECTION,
        messageId = messageId,
        operationPreview = subtitle,
        status = AiExecutionStatus.DONE,
        timestamp = timestamp,
        injectionBlocks = blockUis,
        injectionTotalTokens = totalEstimatedTokens,
        inChatCount = inChatCount,
    )
}

private fun AiUsageRecord.toHistoryItem(): AiExecutionHistoryItemUi {
    return AiExecutionHistoryItemUi(
        id = "usage_$id",
        kind = AiExecutionKind.SIDE_EFFECT,
        source = source,
        operationPreview = modelName.ifBlank { modelId },
        status = if (success) AiExecutionStatus.DONE else AiExecutionStatus.FAILED,
        timestamp = timestamp,
        totalTokens = totalTokens,
        generatedChars = generatedChars,
        modelName = modelName.ifBlank { modelId },
    )
}

private fun resolveStatus(output: String, isStreaming: Boolean): AiExecutionStatus = when {
    isStreaming && output.isBlank() -> AiExecutionStatus.RUNNING
    isStreaming && output == ToolTraceBuilder.EXECUTING_PLACEHOLDER -> AiExecutionStatus.EXECUTING
    output == ToolTraceBuilder.RESULT_CANCELLED -> AiExecutionStatus.CANCELLED
    output == ToolTraceBuilder.RESULT_REJECTED -> AiExecutionStatus.REJECTED
    outputLooksLikeSkippedOrRejected(output) -> AiExecutionStatus.REJECTED
    output == ToolTraceBuilder.RESULT_NOT_COMPLETED ||
        output == ToolTraceBuilder.EXECUTING_PLACEHOLDER -> AiExecutionStatus.INCOMPLETE
    outputLooksLikeError(output) -> AiExecutionStatus.FAILED
    else -> AiExecutionStatus.DONE
}

private fun outputLooksLikeSkippedOrRejected(output: String): Boolean {
    if (output.isBlank()) return false
    return runCatching {
        val status = com.google.gson.JsonParser.parseString(output).asJsonObject
            .get("status")?.takeIf { !it.isJsonNull }?.asString
        status == "rejected" || status == "skipped"
    }.getOrDefault(false)
}

private fun outputLooksLikeError(output: String): Boolean {
    if (output.isBlank() || output in TERMINAL_OUTPUTS) return false
    if (outputLooksLikeSkippedOrRejected(output)) return false
    return runCatching {
        val obj = com.google.gson.JsonParser.parseString(output).asJsonObject
        obj.has("error") && !obj.get("error").isJsonNull
    }.getOrDefault(false)
}

private fun parseSnapshotId(output: String): String? = runCatching {
    com.google.gson.JsonParser.parseString(output).asJsonObject
        .get("snapshotId")?.asString
}.getOrNull()?.takeIf { it.isNotBlank() }

@Composable
fun executionToolDisplayName(toolName: String): String {
    val resId = toolDisplayNameRes(toolName)
    if (resId != 0) return stringResource(resId)
    return AiAbilityManagementViewModel.defaultDisplayName(toolName)
}

@Composable
fun executionStatusLabel(status: AiExecutionStatus): String = when (status) {
    AiExecutionStatus.RUNNING -> stringResource(R.string.ai_execution_status_running)
    AiExecutionStatus.EXECUTING -> stringResource(R.string.ai_execution_status_executing)
    AiExecutionStatus.DONE -> stringResource(R.string.ai_execution_status_done)
    AiExecutionStatus.FAILED -> stringResource(R.string.ai_execution_status_failed)
    AiExecutionStatus.CANCELLED -> stringResource(R.string.ai_execution_status_cancelled)
    AiExecutionStatus.REJECTED -> stringResource(R.string.ai_execution_status_rejected)
    AiExecutionStatus.INCOMPLETE -> stringResource(R.string.ai_execution_status_incomplete)
}

@Composable
fun executionKindTitle(item: AiExecutionHistoryItemUi): String = when (item.kind) {
    AiExecutionKind.TOOL -> {
        val name = executionToolDisplayName(item.toolName)
        if (item.operationPreview.isNotBlank()) "$name · ${item.operationPreview}" else name
    }
    AiExecutionKind.SIDE_EFFECT -> aiSourceDisplayName(item.source)
    AiExecutionKind.PROMPT_INJECTION -> stringResource(R.string.ai_execution_prompt_injection)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiExecutionHistorySheet(
    messages: ImmutableList<AiChatMessageUi>,
    streamingMessage: AiChatMessageUi?,
    conversationId: String?,
    onDismiss: () -> Unit,
    onOpenInEditor: (toolName: String, input: String) -> Unit,
    onUndo: (snapshotId: String) -> Unit,
) {
    val context = LocalContext.current
    val (sheetState, onDismissRequest) = rememberSwipeToConfirmDismiss(
        onNeedConfirm = { context.toastOnUi(R.string.ai_sheet_swipe_again_to_close) },
        onDismiss = onDismiss,
    )
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    }
    val usageRepository = remember { AiUsageRepository() }
    var sideEffects by remember { mutableStateOf<List<AiUsageRecord>>(emptyList()) }
    var sideEffectsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        if (conversationId.isNullOrBlank()) {
            sideEffects = emptyList()
            sideEffectsLoading = false
            return@LaunchedEffect
        }
        sideEffectsLoading = true
        sideEffects = withContext(Dispatchers.IO) {
            runCatching {
                usageRepository.recentSideEffectsByConversation(conversationId, 50)
            }.getOrDefault(emptyList())
        }
        sideEffectsLoading = false
    }

    val messagesKey = remember(messages) { messagesActivityLogKey(messages) }
    val streamingKey = remember(streamingMessage) { streamingActivityLogKey(streamingMessage) }
    val items = remember(messagesKey, streamingKey, sideEffects) {
        buildExecutionHistory(
            messages = messages,
            streamingMessage = streamingMessage,
            sideEffects = sideEffects,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(1f)
                .legadoSheetInsets()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ai_execution_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
            Text(
                stringResource(R.string.ai_execution_history_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (items.isEmpty() && sideEffectsLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.ai_execution_history_empty_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.ai_execution_history_empty_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        val previous = items.getOrNull(index - 1)
                        val currentTurn = item.messageId.ifBlank { "solo_${item.id}" }
                        val previousTurn = previous?.let { it.messageId.ifBlank { "solo_${it.id}" } }
                        val showTurnSeparator = previous != null && previousTurn != currentTurn
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showTurnSeparator) {
                                ExecutionTurnSeparator(
                                    timeLabel = dateFormat.format(Date(item.timestamp)),
                                )
                            }
                            val fullTool = remember(item.id, messagesKey, streamingKey) {
                                findToolPart(messages, streamingMessage, item)
                            }
                            val fullSideEffect = remember(item.id, messagesKey, streamingKey) {
                                findSideEffectPart(messages, streamingMessage, item)
                            }
                            val fullInput = fullTool?.input
                                ?: fullSideEffect?.input
                                ?: item.input
                            val fullOutput = fullTool?.output
                                ?: fullSideEffect?.output
                                ?: item.output
                            AiExecutionHistoryRow(
                                item = item,
                                timeLabel = dateFormat.format(Date(item.timestamp)),
                                fullInput = fullInput,
                                fullOutput = fullOutput,
                                canOpenEditor = item.kind == AiExecutionKind.TOOL && canOpenToolInEditor(
                                    toolName = item.toolName,
                                    inputJson = fullTool?.input ?: item.input,
                                    conversationId = conversationId,
                                ),
                                onOpenInEditor = {
                                    onOpenInEditor(item.toolName, fullTool?.input ?: item.input)
                                },
                                onUndo = item.snapshotId?.let { sid -> { onUndo(sid) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionTurnSeparator(timeLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
        )
        Text(
            stringResource(R.string.ai_execution_turn_separator, timeLabel),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
        )
    }
}

private fun findToolPart(
    messages: List<AiChatMessageUi>,
    streamingMessage: AiChatMessageUi?,
    item: AiExecutionHistoryItemUi,
): AiMessagePart.Tool? {
    if (item.kind != AiExecutionKind.TOOL) return null
    val sources = buildList {
        streamingMessage?.let { add(it) }
        addAll(messages)
    }
    for (msg in sources) {
        if (item.messageId.isNotBlank() && msg.id != item.messageId) continue
        msg.parts.toolParts().forEachIndexed { index, tool ->
            if (item.toolCallId.isNotBlank() && tool.toolCallId == item.toolCallId) return tool
            val fallbackId = tool.toolCallId.ifBlank { "${msg.id}_$index" }
            if (fallbackId == item.id) return tool
        }
    }
    return null
}

private fun findSideEffectPart(
    messages: List<AiChatMessageUi>,
    streamingMessage: AiChatMessageUi?,
    item: AiExecutionHistoryItemUi,
): AiMessagePart.SideEffect? {
    if (item.kind != AiExecutionKind.SIDE_EFFECT || item.messageId.isBlank()) return null
    val sources = buildList {
        streamingMessage?.let { add(it) }
        addAll(messages)
    }
    for (msg in sources) {
        if (msg.id != item.messageId) continue
        msg.parts.sideEffectParts().forEachIndexed { index, effect ->
            if ("side_${msg.id}_$index" == item.id) return effect
            if (effect.source == item.source &&
                (item.input.isBlank() || effect.input == item.input)
            ) {
                return effect
            }
        }
    }
    return null
}

private fun formatSideEffectText(text: String): String =
    text.replace(io.legado.app.domain.model.PostEditRules.SENTENCE_SEPARATOR, "\n\n")

private data class ExecutionTextDetail(
    val title: String,
    val body: String,
)

@Composable
private fun AiExecutionHistoryRow(
    item: AiExecutionHistoryItemUi,
    timeLabel: String,
    fullInput: String,
    fullOutput: String,
    canOpenEditor: Boolean,
    onOpenInEditor: () -> Unit,
    onUndo: (() -> Unit)?,
) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    var textDetail by remember { mutableStateOf<ExecutionTextDetail?>(null) }
    val cardBg = legadoCardBackgroundColor()
    val icon: ImageVector
    val iconTint: androidx.compose.ui.graphics.Color
    val panelBg: androidx.compose.ui.graphics.Color
    when (item.kind) {
        AiExecutionKind.TOOL -> {
            val colors = toolApprovalColors(resolveToolVisualFamily(item.toolName))
            icon = Icons.Default.Code
            iconTint = colors.iconTint
            panelBg = colors.panelContainer
        }
        AiExecutionKind.SIDE_EFFECT -> {
            icon = Icons.Default.AutoAwesome
            iconTint = MaterialTheme.colorScheme.secondary
            panelBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        }
        AiExecutionKind.PROMPT_INJECTION -> {
            icon = Icons.Default.History
            iconTint = MaterialTheme.colorScheme.tertiary
            panelBg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        }
    }
    val title = executionKindTitle(item)
    val statusLabel = executionStatusLabel(item.status)
    val statusColor = when (item.status) {
        AiExecutionStatus.DONE -> MaterialTheme.colorScheme.primary
        AiExecutionStatus.FAILED,
        AiExecutionStatus.CANCELLED,
        AiExecutionStatus.REJECTED,
        AiExecutionStatus.INCOMPLETE -> MaterialTheme.colorScheme.error
        AiExecutionStatus.RUNNING,
        AiExecutionStatus.EXECUTING -> MaterialTheme.colorScheme.tertiary
    }
    val argsLabel = stringResource(R.string.ai_execution_args)
    val resultLabel = stringResource(R.string.ai_execution_result)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = panelBg,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = when (item.kind) {
                        AiExecutionKind.PROMPT_INJECTION -> stringResource(
                            R.string.ai_execution_injection_summary,
                            item.injectionBlocks.size,
                            item.injectionTotalTokens,
                        )
                        AiExecutionKind.SIDE_EFFECT -> buildString {
                            if (item.modelName.isNotBlank()) append(item.modelName)
                            if (item.totalTokens > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("${item.totalTokens} tok")
                            }
                        }
                        AiExecutionKind.TOOL -> timeLabel
                    }
                    Text(
                        if (item.kind == AiExecutionKind.TOOL) timeLabel else "$subtitle · $timeLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider()
                    when (item.kind) {
                        AiExecutionKind.TOOL -> {
                            if (fullInput.isNotBlank() && fullInput != "{}") {
                                ExecutionDetailBlock(
                                    label = argsLabel,
                                    preview = prettyJson(fullInput).take(PREVIEW_INPUT_CHARS),
                                    onClick = {
                                        textDetail = ExecutionTextDetail(argsLabel, prettyJson(fullInput))
                                    },
                                )
                            }
                            if (fullOutput.isNotBlank() && fullOutput !in TERMINAL_OUTPUTS) {
                                ExecutionDetailBlock(
                                    label = resultLabel,
                                    preview = prettyJson(fullOutput).take(PREVIEW_OUTPUT_CHARS),
                                    onClick = {
                                        textDetail = ExecutionTextDetail(resultLabel, prettyJson(fullOutput))
                                    },
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (canOpenEditor) {
                                    TextButton(onClick = onOpenInEditor) {
                                        Text(stringResource(R.string.ai_tool_open_editor))
                                    }
                                }
                                if (onUndo != null) {
                                    TextButton(onClick = onUndo) {
                                        Icon(
                                            Icons.Default.Undo,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.ai_execution_undo))
                                    }
                                }
                            }
                        }
                        AiExecutionKind.SIDE_EFFECT -> {
                            val stats = stringResource(
                                R.string.ai_execution_side_effect_detail,
                                item.totalTokens,
                                item.generatedChars,
                            )
                            Text(
                                stats,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val inputLabel = stringResource(R.string.ai_execution_post_edit_input)
                            val outputLabel = stringResource(R.string.ai_execution_post_edit_output)
                            if (fullInput.isNotBlank()) {
                                ExecutionDetailBlock(
                                    label = inputLabel,
                                    preview = formatSideEffectText(fullInput).take(PREVIEW_INPUT_CHARS),
                                    onClick = {
                                        textDetail = ExecutionTextDetail(
                                            inputLabel,
                                            formatSideEffectText(fullInput),
                                        )
                                    },
                                )
                            }
                            if (fullOutput.isNotBlank()) {
                                ExecutionDetailBlock(
                                    label = outputLabel,
                                    preview = formatSideEffectText(fullOutput).take(PREVIEW_OUTPUT_CHARS),
                                    onClick = {
                                        textDetail = ExecutionTextDetail(
                                            outputLabel,
                                            formatSideEffectText(fullOutput),
                                        )
                                    },
                                )
                            }
                            if (fullInput.isBlank() && fullOutput.isBlank()) {
                                Surface(
                                    onClick = {
                                        textDetail = ExecutionTextDetail(
                                            title,
                                            buildString {
                                                append(stats)
                                                if (item.modelName.isNotBlank()) {
                                                    append("\n")
                                                    append(item.modelName)
                                                }
                                            },
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                ) {
                                    Text(
                                        stringResource(R.string.ai_execution_side_effect_tap_detail),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                            }
                        }
                        AiExecutionKind.PROMPT_INJECTION -> {
                            if (item.inChatCount > 0) {
                                Text(
                                    stringResource(R.string.ai_execution_in_chat_count, item.inChatCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item.injectionBlocks.forEach { injBlock ->
                                val meta = buildString {
                                    append(injBlock.blockId)
                                    append(" · ")
                                    append(injBlock.estimatedTokens)
                                    append(" tok")
                                    if (injBlock.truncated) append(" · truncated")
                                    if (injBlock.position.isNotBlank()) {
                                        append(" · ")
                                        append(injBlock.position)
                                    }
                                }
                                val listPreview = buildString {
                                    append(meta)
                                    if (injBlock.preview.isNotBlank()) {
                                        append("\n")
                                        append(injBlock.preview)
                                    }
                                }
                                val detailBody = buildString {
                                    append(meta)
                                    val full = injBlock.content.ifBlank { injBlock.preview }
                                    if (full.isNotBlank()) {
                                        append("\n\n")
                                        append(full)
                                    }
                                }
                                val blockTitle = injBlock.blockId.ifBlank {
                                    stringResource(R.string.ai_execution_prompt_injection)
                                }
                                Surface(
                                    onClick = {
                                        textDetail = ExecutionTextDetail(blockTitle, detailBody)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                ) {
                                    Text(
                                        listPreview,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        modifier = Modifier.padding(10.dp),
                                        maxLines = 8,
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

    textDetail?.let { detail ->
        ExecutionTextDetailDialog(
            title = detail.title,
            body = detail.body,
            onDismiss = { textDetail = null },
        )
    }
}

@Composable
private fun ExecutionDetailBlock(
    label: String,
    preview: String,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            preview,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(10.dp),
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExecutionTextDetailDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.sendToClip(body)
                Toast.makeText(context, R.string.copy_complete, Toast.LENGTH_SHORT).show()
            }) {
                Text(stringResource(R.string.copy_text))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
